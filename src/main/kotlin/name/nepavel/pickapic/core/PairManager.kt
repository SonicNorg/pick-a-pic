package name.nepavel.pickapic.core

import name.nepavel.pickapic.Config
import name.nepavel.pickapic.domain.Pic
import name.nepavel.pickapic.repository.PicRepository
import name.nepavel.pickapic.repository.ViewsRepository
import org.apache.logging.log4j.LogManager
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object PairManager {
    private val log = LogManager.getLogger("name.nepavel.pickapic.core.PairManager")

    fun getNextPair(chatId: Long, votingName: String): Pair<Pic, Pic>? {
        return getPairViewLimit(chatId, votingName, 1)?.let {
            ViewsRepository.increment(chatId, votingName, it.first.file_id)
            ViewsRepository.increment(chatId, votingName, it.second.file_id)
            it
        }
    }

    private fun getPairViewLimit(
        chatId: Long,
        voting: String,
        viewsLimit: Int
    ): Pair<Pic, Pic>? {
        val list = PicRepository.list(voting).filter { pic ->
            ViewsRepository.get(
                chatId,
                voting,
                pic.file_id
            ) < viewsLimit
        }.sortedBy { it.rank }
        return when {
            list.size < 2 -> {
                log.debug("Searching pics with < {} views, {} found", viewsLimit, list.size)
                if (viewsLimit < Config.config.logic.maxEachShows) {
                    getPairViewLimit(chatId, voting, viewsLimit + 1)
                } else {
                    log.debug("View limit reached at {}", viewsLimit)
                    null
                }
            }
            list.size == 2 -> list[0] to list[1]
            list.size in 3..6 -> list.shuffled().windowed(2, 2) { it[0] to it[1] }.random()
            list.size > 6 -> {
                val rndLeftIndex = Random.nextInt(list.size / 3 - 1, 2 * list.size / 3 + 1)
                val left = list[rndLeftIndex]
                val chooseRightFrom = list.subList(
                    max(0, rndLeftIndex - ceil(list.size / 10.0).toInt()),
                    min(list.size, rndLeftIndex + ceil(list.size / 10.0).toInt())
                )
                var right: Pic = chooseRightFrom.random()
                while (left == right) {
                    right = chooseRightFrom.random()
                }
                left to right
            }
            else -> throw IllegalStateException("List size is ${list.size}, no 'when' branch for it, WTF?!")
        }
    }
}