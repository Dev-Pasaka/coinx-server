package online.pasaka.model.user

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePassword(
        val phoneNumber: String,
        val newPassword:String,
)
