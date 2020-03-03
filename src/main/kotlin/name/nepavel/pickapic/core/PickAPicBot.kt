package name.nepavel.pickapic.core

import name.nepavel.pickapic.Config
import name.nepavel.pickapic.GFG.eloRating
import name.nepavel.pickapic.domain.Pic
import name.nepavel.pickapic.domain.State
import name.nepavel.pickapic.domain.Voting
import name.nepavel.pickapic.repository.PicRepository
import name.nepavel.pickapic.repository.UserCurrentVoteRepository
import name.nepavel.pickapic.repository.ViewsRepository
import name.nepavel.pickapic.repository.VotingRepository
import org.apache.logging.log4j.LogManager
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.BaseAbilityBot
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.round


class PickAPicBot(botUsername: String, botToken: String) : AbilityBot(botToken, botUsername) {
    private val log = LogManager.getLogger()

    private val votingButtons: ReplyKeyboardMarkup
        get() {
            return ReplyKeyboardMarkup(
                VotingRepository.list().chunked(2) { chunk ->
                    KeyboardRow().apply {
                        chunk.forEach { add("Choose ${it.name} ${it.state}") }
                    }
                }
            ).setResizeKeyboard(true)
        }

    init {
        PicRepository.db = db
        VotingRepository.db = db
        UserCurrentVoteRepository.db = db
        ViewsRepository.db = db
        PairManager.db = db
    }

    override fun creatorId(): Int = 141897089

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
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                execute(
                    SendMessage(
                        ctx.chatId(),
                        """/create NAME 2020-01-01 2020-01-18 - ${create().info()}
/ranks - ${ranks().info()}""".trimIndent()
                    ).setReplyMarkup(votingButtons)
                )
            }
            .build()
    }

    fun ranks(): Ability {
        return Ability.builder()
            .name("ranks")
            .info("see pics ranks")
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                val chosen = UserCurrentVoteRepository.get(ctx.chatId())
                if (chosen != null) {
                    if (!(VotingRepository.get(chosen).state == State.CLOSED
                                || admins().contains(ctx.chatId().toInt())
                                || creatorId() == ctx.chatId().toInt())
                    ) {
                        silent.send("Voting $chosen is in progress, ranks are unavailable.", ctx.chatId())
                    } else {
                        PicRepository.list(chosen).sortedByDescending { it.rank }.map {
                            InputMediaPhoto(it.file_id, round(it.rank).toInt().toString())
                        }.chunked(8).forEach {
                            execute(SendMediaGroup(ctx.chatId(), it))
                        }
                    }
                } else {
                    silent.send("Choose voting first!", ctx.chatId())
                }
            }
            .build()
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
                            it.keyboard.add(
                                0,
                                KeyboardRow().also { it.add("Start voting") })
                        })
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
                                    "Choose voting first!"
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
                                    Config.config.logic.coefficient
                                )
                                PicRepository.save(currentVoting.name, winner.copy(rank = winnerRating.toFloat()))
                                PicRepository.save(currentVoting.name, loser.copy(rank = loserRating.toFloat()))
                                execute(AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id).setText("Your voice saved!"))
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
                            }
                            text.startsWith("Choose") -> {
                                val chosenVoting = VotingRepository.get(text.split(" ", limit = 3)[1])
                                UserCurrentVoteRepository.save(ctx.chatId(), chosenVoting.name)
                                execute(
                                    SendMessage(
                                        ctx.chatId(),
                                        "You have chosen '$chosenVoting' which contains ${PicRepository.list(
                                            chosenVoting.name
                                        ).size} pics. ${if (chosenVoting.state == State.STARTED) "Go on" else "See /ranks"}!"
                                    )
                                        .setReplyMarkup(votingButtons.also {
                                            if ((admins().contains(ctx.chatId().toInt())
                                                        || creatorId() == ctx.chatId().toInt())
                                                && chosenVoting.state == State.CREATED
                                            ) {
                                                it.keyboard.add(
                                                    0,
                                                    KeyboardRow().also { it.add("Start voting") })
                                            } else if ((admins().contains(ctx.chatId().toInt())
                                                        || creatorId() == ctx.chatId().toInt())
                                                && chosenVoting.state == State.STARTED) {
                                                it.keyboard.add(
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
                                        SendMessage(ctx.chatId(), "Choose voting first!").setReplyMarkup(
                                            votingButtons
                                        )
                                    )
                                } else {
                                    if (admins().contains(ctx.chatId().toInt()) || ctx.chatId().toInt() == creatorId()) {
                                        val started = currentVoting.copy(state = State.STARTED)
                                        VotingRepository.save(started)
                                        if (currentVoting.state == State.CREATED) {
                                            execute(
                                                SendMessage(ctx.chatId(), "Hey, $started! Come and make your choice!")
                                                    .setReplyMarkup(
                                                        votingButtons.apply {
                                                            this.keyboard.add(
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
            execute(
                SendMessage(chatId, "You are done! Thank you!")
                    .setReplyMarkup(
                        votingButtons.also {
                            if (admins().contains(chatId.toInt()) || creatorId() == chatId.toInt()) {
                                if (VotingRepository.get(currentVoting).state == State.STARTED) {
                                    it.keyboard.add(
                                        0,
                                        KeyboardRow().also { it.add("Close voting") })
                                } else if (VotingRepository.get(currentVoting).state == State.CREATED) {
                                    it.keyboard.add(
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