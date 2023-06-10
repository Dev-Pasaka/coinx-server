package online.pasaka.routes

import com.example.cryptodata.GetAllCryptoPrices
import com.example.cryptodata.GetCryptoPrice
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.cryptoPrices(){
    authenticate("auth-jwt") {
        get("/cryptoPrices") {

            val cryptoPrices = GetAllCryptoPrices().getAllCryptoMetadata()

            if (cryptoPrices != null) {

                call.respond(
                    cryptoPrices
                )

            } else {

                call.respondText("Request Not Sent")

            }

        }
    }
}
fun Route.cryptoPrice(){
    authenticate("auth-jwt") {
        get("/cryptoPrice/{symbol?}") {

            val symbol = call.parameters["symbol"]?.uppercase()

            if (symbol != null) {
                try {
                    println(GetCryptoPrice().getCryptoMetadata(symbol))
                    println(symbol)
                    call.respond(GetCryptoPrice().getCryptoMetadata(symbol))
                } catch (_: Exception) {
                    call.respondText("Request not sent.")
                }

            } else {

                call.respondText("Bad Request, Check your parameters", status = HttpStatusCode.BadRequest)

            }

        }
    }
}


