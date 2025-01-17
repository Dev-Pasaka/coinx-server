package online.pasaka.domain.model.wallet.crypto

import kotlinx.serialization.Serializable

@Serializable
data class CryptoCoin(
    val symbol:String,
    val name: String,
    val amount: Double,
)
