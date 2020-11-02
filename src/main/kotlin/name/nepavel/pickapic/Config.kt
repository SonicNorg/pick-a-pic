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

data class Service(val botOwner: Int, val botName: String, val botToken: Masked, val dbPath: String)
data class Logic(var coefficient: Int, var maxEachShows: Int)
data class VersionInfo(val version: String, val buildDate: LocalDateTime)

data class BotConfig(val service: Service, val logic: Logic)

class LocalTimeDecoder : NonNullableLeafDecoder<LocalTime> {
    override fun safeLeafDecode(node: Node, type: KType, context: DecoderContext): ConfigResult<LocalTime> =
        runCatching { LocalTime.parse(node.castUnsafe<StringNode>().value, DateTimeFormatter.ofPattern("HH:mm")) }.toValidated {
            ConfigFailure.DecodeError(node, type)
        }

    override fun supports(type: KType): Boolean = type.classifier == LocalTime::class

}