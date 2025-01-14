package online.pasaka.domain.service.orders.sellOrderService

import com.google.gson.Gson
import kotlinx.coroutines.*
import online.pasaka.Kafka.models.Notification
import online.pasaka.Kafka.models.NotificationType
import online.pasaka.Kafka.models.messages.BuyOrderConfirmationNotificationMessage
import online.pasaka.Kafka.producers.kafkaProducer
import online.pasaka.domain.model.order.SellOrder
import online.pasaka.domain.repository.remote.cryptodata.GetCryptoPrice
import online.pasaka.infrastructure.config.KafkaConfig
import online.pasaka.infrastructure.database.Entries
import online.pasaka.domain.responses.DefaultResponse
import online.pasaka.domain.utils.Utils
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.updateOne

suspend fun createSellOrder(sellOrder: online.pasaka.domain.model.order.SellOrder): DefaultResponse {
    return coroutineScope {

        /**Step 1: Retrieve sellers wallet*/
        val sellersWallet = try {
            async(Dispatchers.IO) {
                Entries.userWallet.findOne(online.pasaka.domain.model.wallet.Wallet::walletId eq sellOrder.sellerEmail)
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return@coroutineScope DefaultResponse(message = "Sellers wallet not found, contact customer service for more information")

        /** Step2 Check if seller has sufficient  crypto amount*/
        sellersWallet.assets.forEach {
            if (it.symbol == sellOrder.cryptoSymbol.uppercase()){
                if (it.amount < sellOrder.cryptoAmount) return@coroutineScope DefaultResponse("You have insufficient balance to place an order.")
            }
        }

        /** Step3 Get merchants crypto sell  ad*/
        val merchantsCryptoAd = try {
            async(Dispatchers.IO) {
                Entries.sellAd.findOne(online.pasaka.domain.model.cryptoAds.SellAd::id eq sellOrder.adId)
            }.await()
        } catch (e: Exception) {
            null
        } ?: return@coroutineScope DefaultResponse(message = "The crypto  sell Ad selected does not exist")


        /** Step4: Check if the crypto symbol in the order matches the crypto ad's symbol */
           val doesCryptoSymbolMatch = merchantsCryptoAd.cryptoSymbol == sellOrder.cryptoSymbol.uppercase()
        if (!doesCryptoSymbolMatch) {
            return@coroutineScope DefaultResponse(message = "The crypto selected does not match with the crypto ad")
        }

        /** Step5: Update the sellers assets */
        println(sellersWallet)
        val sellersAssets = sellersWallet.assets
        val updatedSellersAssets = mutableListOf<online.pasaka.domain.model.wallet.crypto.CryptoCoin>()
            sellersAssets.forEach {
            if(it.symbol == sellOrder.cryptoSymbol){
                updatedSellersAssets.add(
                    online.pasaka.domain.model.wallet.crypto.CryptoCoin(
                        symbol = it.symbol,
                        amount = it.amount - sellOrder.cryptoAmount,
                        name = it.name
                    )
                )
            }else{
                updatedSellersAssets.add(
                    online.pasaka.domain.model.wallet.crypto.CryptoCoin(
                        symbol = it.symbol,
                        amount = it.amount,
                        name = it.name
                    )
                )
            }
        }

        println("Updated assets: $updatedSellersAssets")



        /** Step6: Fetch the current crypto price in KES */
        val cryptoPriceInKes = GetCryptoPrice().getCryptoMetadata(
            cryptoSymbol = sellOrder.cryptoSymbol.uppercase(),
            currency = "KES"
        ).price?.toDoubleOrNull() ?: return@coroutineScope DefaultResponse(message = "Failed to fetch current prices")

        println("cryptoPrice $cryptoPriceInKes")

        /** Step7 Calculate the amount to be transferred by the buyer */
        val transferredAmountByMerchant = (sellOrder.cryptoAmount * cryptoPriceInKes) +
                (merchantsCryptoAd.margin * cryptoPriceInKes * sellOrder.cryptoAmount)

        /** Step8: Generate a unique  sell order ID */
        val orderId = ObjectId().toString()

        /** Step9: Create an entry in the escrow wallet */
        val escrowWalletData = online.pasaka.domain.model.escrow.SellEscrowWallet(
            orderId = orderId,
            merchantAdId = merchantsCryptoAd.id,
            merchantEmail = merchantsCryptoAd.email,
            sellersEmail = sellOrder.sellerEmail,
            cryptoName = merchantsCryptoAd.cryptoName,
            cryptoSymbol = sellOrder.cryptoSymbol,
            cryptoAmount = sellOrder.cryptoAmount,
            escrowState = online.pasaka.domain.model.escrow.EscrowState.PENDING,
            debitedAt = Utils.currentTimeStamp(),
            expiresAt = sellOrder.expiresAt
        )

        /** Debit merchants cryptoAd*/
        val debitMerchantCryptoAd = try {
            async(Dispatchers.IO) {
                Entries.sellAd
                    .updateOne(
                        online.pasaka.domain.model.cryptoAds.SellAd::id eq merchantsCryptoAd.id,
                        merchantsCryptoAd.copy(totalAmount = merchantsCryptoAd.totalAmount - sellOrder.cryptoAmount)
                    )
                    .wasAcknowledged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }


        /** Step10: Debit the sellers crypto */
        val debitSellersCrypto = try {
            async(Dispatchers.IO) {
                Entries.userWallet
                    .updateOne(online.pasaka.domain.model.wallet.Wallet::walletId eq sellOrder.sellerEmail, sellersWallet.copy(assets =  updatedSellersAssets))
                    .wasAcknowledged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        /** Step11: Credit the  sellEscrow wallet */
        val creditEscrowWallet = try {
            async(Dispatchers.IO) {
                Entries.sellEscrowWallet
                    .insertOne(escrowWalletData)
                    .wasAcknowledged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        /** Step12: Create a sell order entry */
        val createOrder = online.pasaka.domain.model.order.SellOrder(
            orderId = orderId,
            adId = merchantsCryptoAd.id,
            sellerEmail = sellOrder.sellerEmail,
            cryptoName = merchantsCryptoAd.cryptoName,
            cryptoSymbol = merchantsCryptoAd.cryptoSymbol,
            cryptoAmount = sellOrder.cryptoAmount,
            amountInKes = transferredAmountByMerchant,
            orderStatus = online.pasaka.domain.model.order.OrderStatus.PENDING,
            expiresAt = sellOrder.expiresAt
        )

        /** Step13: Insert the sell order into the database */
        val createSellOrder = try {
            async(Dispatchers.IO) {
                Entries.sellOrders.insertOne(createOrder).wasAcknowledged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        /** Get merchant's stats*/
        val getMerchantOrderStats = try {
            async(Dispatchers.IO) {
                Entries.dbMerchant.findOne(online.pasaka.domain.model.merchant.Merchant::email eq merchantsCryptoAd.email )
            }.await()
        }catch (e:Exception){
            e.printStackTrace()
            null
        }

        /** Update merchant's stats*/
        launch(Dispatchers.IO) {
            if (getMerchantOrderStats != null){
                val ordersCompletedByPercentage = (getMerchantOrderStats.ordersCompleted.toDouble()/getMerchantOrderStats.ordersMade).toDouble() * 100
                Entries.dbMerchant.updateOne(
                    online.pasaka.domain.model.merchant.Merchant::email eq merchantsCryptoAd.email,
                    getMerchantOrderStats.copy(
                        ordersMade = getMerchantOrderStats.ordersMade + 1,
                        ordersCompletedByPercentage = ordersCompletedByPercentage
                    )

                )
            }
        }

        /** Step14: Send an email notification to the merchant */
        val gson = Gson()
        val notificationsMessage = BuyOrderConfirmationNotificationMessage(
            orderId = createOrder.orderId,
            title = "P2P Order Confirmation",
            iconUrl = "https://play-lh.googleusercontent.com/Yg7Lo7wiW-iLzcnaarj7nm5-hQjl7J9eTgEupxKzC79Vq8qyRgTBnxeWDap-yC8kHoE=w240-h480-rw",
            recipientName = merchantsCryptoAd.merchantUsername,
            recipientEmail = merchantsCryptoAd.email,
            cryptoName = createOrder.cryptoName,
            cryptoSymbol = createOrder.cryptoSymbol,
            cryptoAmount = createOrder.cryptoAmount,
            amountInKes = createOrder.amountInKes
        )
        val emailNotificationMessage = Notification(
            notificationType = NotificationType.SELL_ORDER_HAS_BEEN_PLACED,
            notificationMessage = notificationsMessage
        )
        launch(Dispatchers.IO) {
            kafkaProducer(topic = KafkaConfig.EMAIL_NOTIFICATIONS, message = gson.toJson(emailNotificationMessage))
        }

        /** Step15: Await asynchronous operations */
        createSellOrder?.await() ?: return@coroutineScope DefaultResponse(message = "An expected error has occurred,  contact support for more information.")
        val debitCryptoAdResult = debitSellersCrypto?.await() ?: return@coroutineScope DefaultResponse(message = "An expected error has occurred, contact support for more information")
        val creditEscrowWalletResult = creditEscrowWallet?.await() ?: return@coroutineScope DefaultResponse(message = "An expected error has occurred, contact support for more information")

        /** Step16: Check results and return the appropriate response */
        if (debitCryptoAdResult && creditEscrowWalletResult) {
            return@coroutineScope DefaultResponse(
                status = true,
                message = "Sellers assets are in holding in escrow"
            )
        } else {
            return@coroutineScope DefaultResponse(message = "An expected error has occurred")
        }


    }
}




