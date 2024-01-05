package online.pasaka.domain.model.escrow

enum class EscrowState {
    EXPIRED,
    PENDING,
    PENDING_MERCHANT_RELEASE,
    AUTO_RELEASED,
    PENDING_SELLERS_RELEASE,
    CRYPTO_RELEASED_TO_BUYER,
    CRYPTO_SEND_TO_ORDER_APPEAL,
    CRYPTO_RELEASED_TO_MERCHANT
}