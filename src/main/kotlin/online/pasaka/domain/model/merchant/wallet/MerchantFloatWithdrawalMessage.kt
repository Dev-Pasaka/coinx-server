package online.pasaka.domain.model.merchant.wallet

import kotlinx.serialization.Serializable

@Serializable
data class MerchantFloatWithdrawalMessage(
    val email:String,
    val amount:String
)
