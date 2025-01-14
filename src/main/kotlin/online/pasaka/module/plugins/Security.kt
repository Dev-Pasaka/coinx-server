package online.pasaka.module.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import online.pasaka.infrastructure.config.JWTConfig
import online.pasaka.domain.responses.InvalidToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity() { println(JWTConfig.issuer)
    install(Authentication) {

        jwt("auth-jwt") {

            realm = JWTConfig.realm
            challenge { defaultScheme, realm ->

                call.respond(HttpStatusCode.Unauthorized, InvalidToken(message = "Token has expired or is invalid"))

            }

            verifier(
                JWT
                    .require(Algorithm.HMAC256(JWTConfig.secret))
                    .withAudience(JWTConfig.audience)
                    .withIssuer(JWTConfig.issuer)
                    .build()
                )
            validate { credential ->

                if (credential.payload.getClaim("email").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else null

            }
        }
    }

}
