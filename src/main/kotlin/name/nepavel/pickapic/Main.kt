package name.nepavel.pickapic

import com.sksamuel.hoplite.ConfigLoader
import name.nepavel.pickapic.core.PickAPicBot
import org.apache.logging.log4j.LogManager
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.nio.file.Paths

fun main() {
    //  -Dlog4j.configurationFile=config/log4j2.yml -Dconfig=/opt/bot/config/config.yaml
    val logger = LogManager.getLogger("name.nepavel.pickapic.MAIN")
    val getenv = System.getenv("config")
    logger.info("Reading config from ENV 'config' ($getenv)...")
    Config.config = try {
        ConfigLoader().withDecoder(LocalTimeDecoder()).loadConfigOrThrow(Paths.get(getenv))
    } catch (e: Exception) {
        logger.error("Failed to load config from ENV 'config' ($getenv)!", e)
        logger.info("Using default config")
        ConfigLoader().withDecoder(LocalTimeDecoder()).loadConfigOrThrow("/config/config.yaml")
    }
    Config.versionInfo = ConfigLoader().loadConfigOrThrow("/versionInfo.yaml")
    logger.info("Config loaded: {}", Config.config)
    logger.info("Starting bot, version ${Config.versionInfo}")
    ApiContextInitializer.init()
    val botsApi = TelegramBotsApi()
    try {
        botsApi.registerBot(PickAPicBot(Config.config.service.botName, Config.config.service.botToken.value, org.telegram.abilitybots.api.db.MapDBContext.onlineInstance(Config.config.service.dbPath)))
        logger.info("Database initialized at ${Config.config.service.dbPath}")
    } catch (e: TelegramApiException) {
        logger.error("Failed to start", e)
        throw e
    }
    logger.info("Bot started.")
}