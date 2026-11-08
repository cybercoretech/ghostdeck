package net.cybercoretech.ghostdeck.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppKey(val packageName: String, val className: String, val userSerial: Long)

object ItemIds {
    fun new(): String = UUID.randomUUID().toString()
}
