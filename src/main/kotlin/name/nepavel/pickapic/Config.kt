package name.nepavel.pickapic

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.NonNullableLeafDecoder
import com.sksamuel.hoplite.decoder.toValidated
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KType


object Config {
    lateinit var config: BotConfig
    lateinit var versionInfo: VersionInfo
}

data class Database(val type: String, val host: String, val port: Int, val db: String, val schema: String, val user: String, val pass: Masked, val tempDbPath: String)
data class Service(val botName: String, val botToken: String)
data class Logic(val coefficient: Int)
data class VersionInfo(val version: String, val buildDate: LocalDateTime)

data class BotConfig(val database:Database, val service: Service, val logic: Logic)

class LocalTimeDecoder : NonNullableLeafDecoder<LocalTime> {
    override fun safeLeafDecode(node: Node, type: KType, context: DecoderContext): ConfigResult<LocalTime> =
        runCatching { LocalTime.parse(node.castUnsafe<StringNode>().value, DateTimeFormatter.ofPattern("HH:mm")) }.toValidated {
            ConfigFailure.DecodeError(node, type)
        }

    override fun supports(type: KType): Boolean = type.classifier == LocalTime::class

}