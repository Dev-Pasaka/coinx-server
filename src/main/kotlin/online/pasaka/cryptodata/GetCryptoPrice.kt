package com.example.cryptodata

import com.example.config.Config
import online.pasaka.model.wallet.crypto.Cryptocurrency
import kotlinx.serialization.json.*


import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*


class GetCryptoPrice {

    suspend fun getCryptoMetadata(currencySymbol:String="ETH"): Cryptocurrency {
        val config = Config.load
        val url = config.property("coinmarketcap_quote_url").getString()
        val client = HttpClient(CIO)
        val request = client.get(url){
            header("X-CMC_PRO_API_KEY", config.property("coinmarketcap_apikey").getString())
            //contentType(ContentType.Application.Json)
            url {
                //  parameter("start", "1")
                //parameter("limit","10")
                //parameter("coins", "Bitcoin")
                parameter("symbol",currencySymbol)
                parameter("convert","KES")
            }
        }

        //println(request.bodyAsText())
        val jsonResponseString = request.bodyAsText()
        println(jsonResponseString)
        val jsonResponseObj = Json.parseToJsonElement(jsonResponseString) as JsonObject
        val cryptoData = jsonResponseObj["data"]
        val cryptoObject = Json.parseToJsonElement(cryptoData.toString()) as JsonObject
        val crypto = cryptoObject[currencySymbol] as Map<String,*>
        val cryptoPriceQuoteObj = Json.parseToJsonElement(crypto.toString()) as JsonObject
        val cryptoQuote = cryptoPriceQuoteObj["quote"]
        val cryptoKesObj = Json.parseToJsonElement(cryptoQuote.toString()) as JsonObject
        val cryptoInKes = cryptoKesObj["KES"] as Map<String, Double>
        println(cryptoInKes)

        return Cryptocurrency(
            id = crypto["id"].toString().toInt(),
            name = crypto["name"].toString(),
            symbol = crypto["symbol"].toString(),
            price = cryptoInKes["price"].toString(),
            volume24h =cryptoInKes["volume_24h"].toString(),
            volumeChange24h = cryptoInKes["volume_change_24h"].toString(),
            percentageChange1h = cryptoInKes["percent_change_1h"].toString(),
            percentageChange24h = (Math.round((cryptoInKes["percent_change_24h"].toString()).toDouble()) * 100/ 10000.0).toString(),
            percentageChange7d = cryptoInKes["percent_change_7d"].toString(),
            percentageChange30d = cryptoInKes["percent_change_30d"].toString(),
            percentageChange60d = cryptoInKes["percent_change_60d"].toString(),
            percentageChange90d = cryptoInKes["percent_change_90d"].toString(),
            marketCap = cryptoInKes["market_cap"].toString() ,
            fullyDilutedMarketCap = cryptoInKes["fully_diluted_market_cap"].toString()

        )
    }


}