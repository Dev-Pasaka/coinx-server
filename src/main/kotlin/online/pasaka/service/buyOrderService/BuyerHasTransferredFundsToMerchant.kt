package online.pasaka.service.buyOrderService

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import online.pasaka.Kafka.models.messages.FundsTransferNotification
import online.pasaka.Kafka.models.Notification
import online.pasaka.Kafka.models.NotificationType
import online.pasaka.Kafka.producers.kafkaProducer
import online.pasaka.config.KafkaConfig
import online.pasaka.database.Entries
import online.pasaka.model.cryptoAds.BuyAd
import online.pasaka.model.escrow.BuyEscrowWallet
import online.pasaka.model.escrow.EscrowState
import online.pasaka.model.order.BuyOrder
import online.pasaka.model.order.OrderStatus
import online.pasaka.responses.DefaultResponse
import org.litote.kmongo.*


suspend fun buyerHasTransferredFundsToMerchant(buyOrderID: String): DefaultResponse {
    val gson = Gson()

    return coroutineScope {

        val doesBuyOrderExists = try {
            Entries.cryptoBuyOrders.findOne(BuyOrder::orderId eq buyOrderID)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return@coroutineScope DefaultResponse(message = "Crypto buy order does not exist")

        when (doesBuyOrderExists.orderStatus) {
            OrderStatus.CANCELLED -> return@coroutineScope DefaultResponse("This order is already cancelled, kindly check with customer service for more information.")
            OrderStatus.EXPIRED -> return@coroutineScope DefaultResponse("This order has expired, kindly check with customer service for more information.")
            OrderStatus.COMPLETED -> return@coroutineScope DefaultResponse("This Order is already completed. kindly check with customer service for more information.")
            OrderStatus.BUYER_HAS_TRANSFERRED_FUNDS -> return@coroutineScope DefaultResponse("This Order is awaiting merchant confirmation, kindly wait for the merchant to release your crypto assets.")
            else -> {}
        }
        val getMerchantInfo = try {
            async(Dispatchers.IO) {  Entries.buyAd.findOne(BuyAd::id eq doesBuyOrderExists.adId)}.await()
        }catch (e:Exception){
            null
        } ?: return@coroutineScope DefaultResponse(message = "Crypto Ad info not found")

        /** Update Buy order state */
        val updateOrderStatus = try {
            Entries.cryptoBuyOrders.updateOne(
                BuyOrder::orderId eq buyOrderID,
                doesBuyOrderExists.copy(orderStatus = OrderStatus.BUYER_HAS_TRANSFERRED_FUNDS)
            ).wasAcknowledged()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return@coroutineScope DefaultResponse(message = "An expected error has occurred")

        /** Notify merchant about the money transfer */
        val notificationMessage = Notification(
            notificationType = NotificationType.BUYER_HAS_TRANSFERRED_FUNDS,
            notificationMessage = FundsTransferNotification(
                orderId = doesBuyOrderExists.orderId,
                recipientName = getMerchantInfo.merchantUsername,
                recipientEmail = getMerchantInfo.email,
                cryptoName = doesBuyOrderExists.cryptoName,
                cryptoSymbol = doesBuyOrderExists.cryptoSymbol,
                cryptoAmount = doesBuyOrderExists.cryptoAmount,
                amountInKes = doesBuyOrderExists.amountInKes
            )
        )

        /** Update Merchants escrowState */
        val escrowStateResult = launch(Dispatchers.IO) {
            val updateEscrowState = setValue(BuyEscrowWallet::escrowState, EscrowState.PENDING_MERCHANT_RELEASE)
            Entries.buyEscrowWallet.findOneAndUpdate(BuyEscrowWallet::orderId eq buyOrderID, updateEscrowState )
        }

        launch(Dispatchers.IO) { kafkaProducer(topic = KafkaConfig.EMAIL_NOTIFICATIONS, message = gson.toJson(notificationMessage)) }
        DefaultResponse(status = true, message = "Kindly wait for merchant to confirm and release crypto")

    }

}

