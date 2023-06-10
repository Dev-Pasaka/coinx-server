package online.pasaka.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.aitservices.SendOtp
import com.example.config.JWTConfig
import com.example.database.CrudOperations
import com.example.database.DatabaseConnection
import online.pasaka.model.user.SignIn
import online.pasaka.model.wallet.crypto.CryptoCoin
import online.pasaka.model.user.User
import online.pasaka.model.user.UserData
import online.pasaka.model.wallet.Wallet
import com.example.responses.Registration
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.litote.kmongo.*
import org.mindrot.jbcrypt.BCrypt
import  com.example.responses.*
import io.ktor.server.auth.*
import online.pasaka.OtpCodeGenerator.OtpCodeGenerator
import online.pasaka.model.user.UpdatePassword
import java.util.*
import online.pasaka.responses.*

fun Route.userRegistration(){
        post("/registerUser") {

            val db = DatabaseConnection.database

            val userData = call.receive<User>()

            val hashedPassword = BCrypt.hashpw(userData.password, BCrypt.gensalt())

            val userCollection = db.getCollection<User>("user")

            val doesEmailExists = userCollection.findOne(User::email eq userData.email)

            if (doesEmailExists == null) {
                val userRegistration = CrudOperations.createUser(
                    userRegistration = User(
                            fullName = userData.fullName,
                            email = userData.email,
                            phoneNumber = userData.phoneNumber,
                            username = userData.username,
                            country = userData.country,
                            password = hashedPassword
                    )
                )

                val walletCreation = CrudOperations.createWallet(
                    Wallet(
                        walletId = userData.email,
                        assets = listOf(
                            CryptoCoin(
                                symbol = "USDT",
                                name = "Tether",
                                amount = 0.0
                            ),
                            CryptoCoin(
                                symbol = "ETH",
                                name = "Ethereum",
                                amount = 0.0
                            ),
                            CryptoCoin(
                                symbol = "BTC",
                                name = "Bitcoin",
                                amount = 0.0
                            )
                        )
                    )
                )

                if (userRegistration && walletCreation) call.respond(

                    status = HttpStatusCode.OK,
                    message = Registration(
                        message = "Registration successful",
                        isRegistered = true
                    )

                )
                else call.respond(

                    status = HttpStatusCode.OK,
                    message = Registration(
                        message = "Registration Failed",
                        isRegistered = false
                    )

                )
            } else {
                call.respond(

                    status = HttpStatusCode.OK,
                    message = Registration(
                        message = "Email already exits",
                        isRegistered = false
                    )

                )
            }
        }

}
fun Route.getUserData(){
    authenticate("auth-jwt") {
        get("/getUserData") {
            val email = call.parameters["email"]

            if (email != null) {
                val userdata = try {
                    CrudOperations.getUserData(email)
                } catch (e: ExceptionInInitializerError) {
                    null
                }

                if (userdata != null) {

                    call.respond(
                        UserData(
                            id = userdata.id,
                            fullName = userdata.fullName,
                            username = userdata.username,
                            phoneNumber = userdata.phoneNumber,
                            email = userdata.email,
                            country = userdata.country
                        )
                    )

                } else {

                    call.respond(
                        User(
                            message = "Failed to fetch data for user $email",
                            status = false
                        )
                    )

                }

            }
        }
    }
}

fun Route.signIn(){
    post("/signIn"){

        val userCredentials = call.receive<SignIn>()

        val userdata = try {
            CrudOperations.fetchUserCredentials(userCredentials.email)
        }catch (e:Exception){null}

        val hashedPassword = userdata?.password

        val email = userdata?.email

        if (email == null ) call.respond(SignInResponse())

        if (email == userCredentials.email && BCrypt.checkpw(userCredentials.password,hashedPassword)){

            val token = JWT.create()
                .withAudience(JWTConfig.audience)
                .withIssuer(JWTConfig.issuer)
                .withClaim("email", userCredentials.email)
                .withExpiresAt(Date(System.currentTimeMillis() + (60000 * 3600)))
                .sign(Algorithm.HMAC256(JWTConfig.secret))

                call.respond(hashMapOf("token" to token))

            }
            else call.respond(SignInResponse())
    }
}

fun Route.updatePassword(){
    post("/updatePassword"){
        val updatePassword = call.receive<UpdatePassword>()
        val hashedPassword = BCrypt.hashpw(updatePassword.newPassword, BCrypt.gensalt())
        val verifyPhoneNumber = CrudOperations.checkIfPhoneExists(
                phoneNumber = updatePassword.phoneNumber
        )
        if (verifyPhoneNumber != null){
            val result = CrudOperations.updatePasswordByPhoneNumber(
                phoneNumber = verifyPhoneNumber.phoneNumber,
                newPassword = hashedPassword
            )
            if(result != null){
                call.respond(
                    status = HttpStatusCode.OK,
                    message = UpdatePassword(
                        status = true,
                        message = "Password updated successfully",
                    )
                )
            }else{
                call.respond(
                    status = HttpStatusCode.OK,
                    message = UpdatePassword(
                        status = false,
                        message = "Failed to update password ",
                    )
                )
            }

        }else{
            call.respond(
                    status = HttpStatusCode.OK,
                    message = UpdatePassword(
                        message = "Password update failed please confirm your phone number"
                    )
            )
        }
    }
}
