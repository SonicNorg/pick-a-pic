package name.nepavel.pickapic.core

import name.nepavel.pickapic.Config
import name.nepavel.pickapic.GFG.eloRating
import name.nepavel.pickapic.calcCoefficient
import name.nepavel.pickapic.domain.Pic
import name.nepavel.pickapic.domain.State
import name.nepavel.pickapic.domain.Voting
import name.nepavel.pickapic.repository.*
import org.apache.logging.log4j.LogManager
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.BaseAbilityBot
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.objects.Locality
import org.telegram.abilitybots.api.objects.Privacy
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.math.max

private const val CHOOSE_VOTING_FIRST = "Choose voting first!"

class PickAPicBot(
    botUsername: String,
    botToken: String,
    offlineInstance: DBContext
) : AbilityBot(botToken, botUsername, offlineInstance) {
    private val log = LogManager.getLogger("name.nepavel.pickapic.core.PickAPicBot")

    private val votingButtons: ReplyKeyboard
        get() {
            return if (VotingRepository.list().isEmpty()) ReplyKeyboardRemove() else ReplyKeyboardMarkup(
                VotingRepository.list().chunked(2) { chunk ->
                    KeyboardRow().apply {
                        chunk.forEach { add("Choose ${it.name} ${it.state}") }
                    }
                }
            ).setResizeKeyboard(true)
        }

    init {
        DbHelper.db = db
        db.getVar<Boolean>("DEBUG").set(false)
    }

    override fun creatorId(): Int = Config.config.service.botOwner

    fun debug(): Ability {
        return Ability.builder()
            .name("debug")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                val debugMode = db.getVar<Boolean>("DEBUG")
                debugMode.set(!debugMode.get())
                silent.send("Debug mode is now ${if (debugMode.get()) "ON" else "OFF"}", ctx.chatId())
            }
            .build()
    }

    fun start(): Ability {
        return Ability.builder()
            .name("start")
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                val started = VotingRepository.list(State.STARTED).singleOrNull()
                if (started != null) {
                    UserCurrentVoteRepository.save(ctx.chatId(), started.name)
                    execute(
                        SendMessage(ctx.chatId(), "Hi there! Welcome to $started! Choose wisely!")
                            .setReplyMarkup(votingButtons)
                    )
                    sendPicsToVote(ctx.chatId(), started.name)
                } else if (VotingRepository.list().isNotEmpty()) {
                    execute(
                        SendMessage(ctx.chatId(), "Hi there! Please choose a started voting and start picking pics!")
                            .setReplyMarkup(votingButtons)
                    )
                } else {
                    execute(
                        SendMessage(ctx.chatId(), "Hi there! No voting is created. Wait until admins do their job!")
                            .setReplyMarkup(votingButtons)
                    )
                }
            }
            .build()
    }

    fun clear(): Ability {
        return Ability.builder()
            .name("clear")
            .locality(Locality.USER)
            .privacy(Privacy.CREATOR)
            .action { db.clear() }
            .build()
    }

    fun help(): Ability {
        return Ability.builder()
            .name("help")
            .info("help")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                execute(
                    SendMessage(
                        ctx.chatId(),
                        """/create NAME 2020-01-01 2020-01-18 - ${create().info()}
/clear - totally clears database (CANNOT BE UNDONE)
/ranks - ${ranks().info()}
/coef [X] - ${coef().info()}
/views [X] - ${viewLimit().info()}
/info - ${info().info()}
/users - ${usersCount().info()}
/debug - switch debug mode on/off""".trimIndent()
                    ).setReplyMarkup(votingButtons)
                )
            }
            .build()
    }

    fun usersCount(): Ability {
        return Ability.builder()
            .name("users")
            .info("See users count")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action {
                silent.send("Total users count: ${users().size}", it.chatId())
            }.build()
    }

    fun ranks(): Ability {
        return Ability.builder()
            .name("ranks")
            .info("See pics ranks")
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                val chosen = UserCurrentVoteRepository.get(ctx.chatId())
                if (chosen != null) {
                    if (!(VotingRepository.get(chosen)?.state == State.CLOSED
                                || admins().contains(ctx.chatId().toInt())
                                || creatorId() == ctx.chatId().toInt())
                    ) {
                        silent.send("Voting $chosen is in progress, ranks are unavailable.", ctx.chatId())
                    } else if (VotingRepository.get(chosen) == null) {
                        silent.send("Voting $chosen not found! We are very sorry :(", ctx.chatId())
                    } else {
                        sendRanks(chosen, ctx.chatId())
                    }
                } else {
                    silent.send(CHOOSE_VOTING_FIRST, ctx.chatId())
                }
            }
            .build()
    }

    private fun sendRanks(voting: String, chatId: Long?) {
        PicRepository.list(voting).sortedByDescending { it.rank }.map {
            InputMediaPhoto(it.file_id, "%.1f".format(it.rank))
        }.chunked(8).forEach {
            execute(SendMediaGroup(chatId, it))
        }
    }

    fun create(): Ability {
        return Ability.builder()
            .name("create")
            .info("create new voting with specified name, start date, end date and sets as current")
            .input(3)
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                val voting = Voting(
                    ctx.firstArg(),
                    LocalDate.parse(ctx.secondArg()),
                    LocalDate.parse(ctx.thirdArg()),
                    State.CREATED
                )
                VotingRepository.save(voting)
                UserCurrentVoteRepository.save(ctx.chatId(), voting.name)
                execute(
                    SendMessage(ctx.chatId(), "You have just created '${ctx.firstArg()}' voting. Add pics now!")
                        .setReplyMarkup(votingButtons.also {
                            (it as ReplyKeyboardMarkup).keyboard.add(
                                0,
                                KeyboardRow().also { it.add("Start voting") })
                        })
                )
            }
            .build()
    }

    fun coef(): Ability {
        return Ability.builder()
            .name("coef")
            .info("Get (no args) or set (one arg) new base for dynamic Elo rating")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                if (ctx.arguments().isNullOrEmpty()) {
                    silent.send(
                        "Coefficient base is ${Config.config.logic.coefficient}",
                        ctx.chatId()
                    )
                } else {
                    val newCoef = ctx.firstArg().toInt()
                    silent.send(
                        "Coefficient base changed from ${Config.config.logic.coefficient} to $newCoef",
                        ctx.chatId()
                    )
                    Config.config.logic.coefficient = newCoef
                }
            }
            .build()
    }

    fun viewLimit(): Ability {
        return Ability.builder()
            .name("views")
            .info("Get (no args) or set (one arg) new view limit")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                if (ctx.arguments().isNullOrEmpty()) {
                    silent.send(
                        "Views limit is ${Config.config.logic.maxEachShows}",
                        ctx.chatId()
                    )
                } else {
                    val maxEachShows = ctx.firstArg().toInt()
                    silent.send(
                        "Views limit changed from ${Config.config.logic.maxEachShows} to $maxEachShows",
                        ctx.chatId()
                    )
                    Config.config.logic.maxEachShows = maxEachShows
                }
            }
            .build()
    }

    fun info(): Ability {
        return Ability.builder()
            .name("info")
            .info("Show completed votings count and current ELO value")
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action { ctx ->
                if (UserCurrentVoteRepository.get(ctx.chatId()) == null) {
                    silent.send(CHOOSE_VOTING_FIRST, ctx.chatId())
                    return@action
                }
                val finishes = FinishesRepository.get(UserCurrentVoteRepository.get(ctx.chatId())!!)
                silent.send(
                    "Current base: ${Config.config.logic.coefficient}\nCurrent finishes: $finishes\nCurrent ELO: ${
                        calcCoefficient(
                            Config.config.logic.coefficient,
                            finishes
                        )
                    }",
                    ctx.chatId()
                )
            }
            .build()
    }

    fun default(): Ability {
        return Ability.builder()
            .name(BaseAbilityBot.DEFAULT)
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                val currentVoting = UserCurrentVoteRepository.get(ctx.chatId())?.let { VotingRepository.get(it) }
                when {
                    ctx.update().hasMessage() && ctx.update().message.hasPhoto() -> {
                        if (admins().contains(ctx.chatId().toInt()) || ctx.chatId().toInt() == creatorId()) {
                            when {
                                currentVoting == null -> {
                                    execute(
                                        SendMessage(
                                            ctx.chatId(),
                                            "Choose or /create voting first!"
                                        ).setReplyMarkup(votingButtons)
                                    )
                                }
                                currentVoting.state != State.CREATED -> {
                                    UserCurrentVoteRepository.save(ctx.chatId(), null)
                                    execute(SendMessage(ctx.chatId(), "Too late to add pics!"))
                                }
                                else -> {
                                    PicRepository.add(
                                        currentVoting.name,
                                        Pic(ctx.update().message.photo[0].fileId)
                                    )
                                    silent.send(
                                        "Pic added. Total is ${PicRepository.list(currentVoting.name).size} pics.",
                                        ctx.chatId()
                                    )
                                }
                            }
                        }
                    }
                    ctx.update().hasCallbackQuery() -> {
                        val callbackQuery = ctx.update().callbackQuery
                        when {
                            currentVoting == null -> execute(
                                SendMessage(
                                    ctx.chatId(),
                                    CHOOSE_VOTING_FIRST
                                ).setReplyMarkup(votingButtons)
                            )
                            currentVoting.state != State.STARTED -> execute(
                                AnswerCallbackQuery()
                                    .setText("Started voting '${currentVoting.name}' not found! Choose STARTED voting.")
                                    .setCallbackQueryId(callbackQuery.id)
                                    .setShowAlert(true)
                            )
                            else -> {
                                val (winnerHash, loserHash) = callbackQuery.data.split("|", limit = 2)
                                val winner = PicRepository.get(currentVoting.name, winnerHash.toInt())
                                val loser = PicRepository.get(currentVoting.name, loserHash.toInt())
                                val (winnerRating, loserRating) = eloRating(
                                    winner.rank,
                                    loser.rank,
                                    calcCoefficient(
                                        Config.config.logic.coefficient,
                                        FinishesRepository.get(currentVoting.name)
                                    ).toInt()
                                )
                                if (db.getVar<Boolean>("DEBUG").get()) {
                                    execute(
                                        AnswerCallbackQuery()
                                            .setCallbackQueryId(callbackQuery.id)
                                            .setShowAlert(true)
                                            .setText(
                                                "Winner rank: ${"%.2f".format(winner.rank)} -> ${
                                                    "%.2f".format(
                                                        winnerRating
                                                    )
                                                }\n" +
                                                        "Loser rank: ${"%.2f".format(loser.rank)} -> ${
                                                            "%.2f".format(
                                                                loserRating
                                                            )
                                                        }"
                                            )
                                    )
                                } else {
                                    execute(
                                        AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
                                            .setText("Your voice saved!")
                                    )
                                }
                                PicRepository.save(currentVoting.name, winner.copy(rank = winnerRating.toFloat()))
                                PicRepository.save(currentVoting.name, loser.copy(rank = loserRating.toFloat()))
                                sendPicsToVote(ctx.chatId(), currentVoting.name)
                            }
                        }
                        execute(DeleteMessage(ctx.chatId(), callbackQuery.message.messageId))
                    }
                    ctx.update().hasMessage() && ctx.update().message.hasText() -> {
                        val text = ctx.update().message.text
                        when {
                            text.startsWith("Close voting") -> {
                                val closed = currentVoting!!.copy(state = State.CLOSED, end = LocalDate.now())
                                VotingRepository.save(closed)
                                execute(
                                    SendMessage(
                                        ctx.chatId(),
                                        "Voting '$closed' closed, results become available for all."
                                    ).setReplyMarkup(votingButtons)
                                )
                                users().keys.forEach { id ->
                                    try {
                                        silent.send("Voting $closed, here are the final ranks!", id.toLong())
                                        sendRanks(closed.name, id.toLong())
                                    } catch (e: Exception) {
                                        log.warn("Failed to send ranks on vote closed to {}", id, e)
                                    }
                                }
                            }
                            text.startsWith("Choose") -> {
                                val chosenVoting = VotingRepository.get(text.split(" ", limit = 3)[1])
                                if (chosenVoting == null) {
                                    execute(
                                        SendMessage(ctx.chatId(), "Voting $currentVoting not found! We are very sorry :(").setReplyMarkup(
                                            votingButtons
                                        )
                                    )
                                    return@action
                                }
                                UserCurrentVoteRepository.save(ctx.chatId(), chosenVoting.name)
                                execute(
                                    SendMessage(
                                        ctx.chatId(),
                                        "You have chosen '$chosenVoting' which contains ${
                                            PicRepository.list(
                                                chosenVoting.name
                                            ).size
                                        } pics. ${if (chosenVoting.state == State.STARTED) "Go on" else "See /ranks"}!"
                                    ).setReplyMarkup(votingButtons.also {
                                        if ((admins().contains(ctx.chatId().toInt())
                                                    || creatorId() == ctx.chatId().toInt())
                                            && chosenVoting.state == State.CREATED
                                        ) {
                                            (it as ReplyKeyboardMarkup).keyboard.add(
                                                0,
                                                KeyboardRow().also { it.add("Start voting") })
                                        } else if ((admins().contains(ctx.chatId().toInt())
                                                    || creatorId() == ctx.chatId().toInt())
                                            && chosenVoting.state == State.STARTED
                                        ) {
                                            (it as ReplyKeyboardMarkup).keyboard.add(
                                                0,
                                                KeyboardRow().also { it.add("Close voting") })
                                        }
                                    })
                                )
                                if (chosenVoting.state == State.STARTED) {
                                    sendPicsToVote(ctx.chatId(), chosenVoting.name)
                                }
                            }
                            text == "Start voting" -> {
                                if (currentVoting == null) {
                                    execute(
                                        SendMessage(ctx.chatId(), CHOOSE_VOTING_FIRST).setReplyMarkup(
                                            votingButtons
                                        )
                                    )
                                } else {
                                    if (admins().contains(ctx.chatId().toInt()) || ctx.chatId()
                                            .toInt() == creatorId()
                                    ) {
                                        val started = currentVoting.copy(state = State.STARTED)
                                        VotingRepository.save(started)
                                        if (currentVoting.state == State.CREATED) {
                                            execute(
                                                SendMessage(ctx.chatId(), "Hey, $started! Come and make your choice!")
                                                    .setReplyMarkup(
                                                        votingButtons.apply {
                                                            (this as ReplyKeyboardMarkup).keyboard.add(
                                                                0,
                                                                KeyboardRow().apply { add("Close voting $started") })
                                                        }
                                                    )
                                            )
                                            sendPicsToVote(ctx.chatId(), currentVoting.name)
                                        } else {
                                            silent.send("Voting is ${currentVoting.state}", ctx.chatId())
                                        }
                                    }
                                }
                            }
                            else -> execute(
                                SendMessage(ctx.chatId(), "Type /help for help!").setReplyMarkup(
                                    votingButtons
                                )
                            )
                        }
                    }
                }
            }
            .build()
    }

    private fun sendPicsToVote(chatId: Long, currentVoting: String) {
        val nextPair = PairManager.getNextPair(chatId, currentVoting)
        if (nextPair == null) {
            FinishesRepository.increment(currentVoting)
            execute(
                SendMessage(chatId, "You are done! Thank you!")
                    .setReplyMarkup(
                        votingButtons.also {
                            if (admins().contains(chatId.toInt()) || creatorId() == chatId.toInt()) {
                                if (VotingRepository.get(currentVoting)?.state == State.STARTED) {
                                    (it as ReplyKeyboardMarkup).keyboard.add(
                                        0,
                                        KeyboardRow().also { it.add("Close voting") })
                                } else if (VotingRepository.get(currentVoting)?.state == State.CREATED) {
                                    (it as ReplyKeyboardMarkup).keyboard.add(
                                        0,
                                        KeyboardRow().also { it.add("Start voting") })
                                }
                            }
                        }
                    )
            )
            return
        }
        execute(
            SendMediaGroup(
                chatId, listOf(
                    InputMediaPhoto(nextPair.first.file_id, "left"),
                    InputMediaPhoto(nextPair.second.file_id, "right")
                )
            )
        )
        execute(
            SendMessage(chatId, "Choose the best pic")
                .apply {
                    replyMarkup = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton("\uD83D\uDC4D LEFT").setCallbackData("${nextPair.first.hashCode()}|${nextPair.second.hashCode()}"),
                                InlineKeyboardButton("RIGHT \uD83D\uDC4D").setCallbackData("${nextPair.second.hashCode()}|${nextPair.first.hashCode()}")
                            )
                        )
                    )
                })
    }

    override fun onUpdateReceived(update: Update?) {
        try {
            super.onUpdateReceived(update)
        } catch (e: TelegramApiRequestException) {
            log.error("{}", e.apiResponse, e)
        } catch (e: Exception) {
            log.error("", e)
        }
    }

    private fun mergeImages(left: InputStream, right: InputStream): InputStream {
        val result = ByteArrayOutputStream()
        val _left = ImageIO.read(left)
        val _right = ImageIO.read(right)

// create the new image, canvas size is the max. of both image sizes
        val w = _left.width + _right.width
        val h = max(_left.height, _right.height)
        val combined = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)

// paint both images, preserving the alpha channels
        val g = combined.graphics
        g.drawImage(_left, 0, 0, null)
        g.drawImage(_right, _left.width + 10, 0, null)

// Save as new image
        ImageIO.write(combined, "PNG", result)
        return result.toByteArray().inputStream()
    }
}