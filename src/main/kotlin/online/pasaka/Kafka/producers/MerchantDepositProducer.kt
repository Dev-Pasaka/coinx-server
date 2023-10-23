package online.pasaka.Kafka.producers

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import online.pasaka.config.KafkaConfig
import online.pasaka.model.merchant.wallet.MerchantFloatTopUpMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

suspend fun merchantTopUpProducer(
    email:String,
    topic: String = "pasaka",
    properties: Properties = Properties(),
    message: String
)  = coroutineScope{

    launch {

        val producerProps = properties.apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfig.BOOTSTRAP_SERVER_URL)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        }

        val producer = KafkaProducer<Nothing, String>(producerProps)
        val merchantTopUp = MerchantFloatTopUpMessage(
            email = email,
            crypto = message,
        )

        val json = Json.encodeToString(merchantTopUp)
        val record = ProducerRecord(topic, null, json)

        val result = producer.send(record)
        println(result)
        producer.close()
    }


}