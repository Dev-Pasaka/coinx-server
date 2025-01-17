import io.ktor.plugin.features.*

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val confluent_version: String by project
val ak_version: String by project

plugins {
    kotlin("jvm") version "1.8.21"
    id("io.ktor.plugin") version "2.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10"
}

group = "online.pasaka"
version = "0.0.1"
application {
    mainClass.set("online.pasaka.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
    maven("https://packages.confluent.io/maven")
    maven("https://kotlin.bintray.com/ktor")
}






dependencies {

    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    //Client Requests engine
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")

    //Json Encoding & Decoding
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("com.google.code.gson:gson:2.8.8")


    //Mongodb
    implementation("org.litote.kmongo:kmongo:4.5.1")
    implementation("org.litote.kmongo:kmongo-coroutine:4.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("org.mongodb:mongodb-driver-sync:4.3.1")

    //JWT
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")

    //Hashing
    implementation("org.mindrot:jbcrypt:0.4")

    //status pages
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")

    //Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("org.slf4j:slf4j-api:1.7.33")

    //Kafka
    //implementation("com.github.gAmUssA:ktor-kafka:main-SNAPSHOT")
    implementation("org.apache.kafka:kafka-streams:2.7.0")
    implementation("io.confluent:kafka-json-schema-serializer:$confluent_version")
    implementation("io.confluent:kafka-streams-json-schema-serde:$confluent_version") {
        exclude("org.apache.kafka", "kafka-clients")

    }
    //websokets
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")


    // https://mvnrepository.com/artifact/redis.clients/jedis
    implementation("redis.clients:jedis:5.0.1")

    //Java Mail SDK
    implementation("javax.mail:javax.mail-api:1.6.2")
    implementation("com.sun.mail:javax.mail:1.6.2")
    //Afraca's taking SMS SDK
    implementation("com.github.AfricasTalkingLtd.africastalking-java:core:3.4.11")

    //Rate limiting
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")
    //SSL
    implementation("io.ktor:ktor-network-tls-certificates:$ktor_version")








}

tasks {
    create("stage").dependsOn("installDist")
}
ktor {
    fatJar {
        archiveFileName.set("CoinxApi.jar")
    }
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("sample-docker-image")
        imageTag.set("0.0.1-preview")

        portMappings.set(listOf(
            DockerPortMapping(
                80,
                8080,
                DockerPortMappingProtocol.TCP
            )
        ))

    }
}
