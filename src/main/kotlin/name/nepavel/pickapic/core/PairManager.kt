package name.nepavel.pickapic.core

import name.nepavel.pickapic.domain.Pic
import name.nepavel.pickapic.repository.DbHelper
import name.nepavel.pickapic.repository.PicRepository
import name.nepavel.pickapic.repository.ViewsRepository

object PairManager {

    fun getNextPair(chatId: Long, votingName: String): Pair<Pic, Pic>? {
        val closest = DbHelper.db.getMap<Long, Boolean>("CLOSEST").getOrDefault(chatId, false)
        val list = getList(votingName, chatId, closest)
        return if (list.isEmpty()) {
            null
        } else {
            list.random()
        }?.let {
            DbHelper.db.getMap<Long, Boolean>("CLOSEST")[chatId] = !closest
            ViewsRepository.increment(chatId, votingName, it.first.file_id)
            ViewsRepository.increment(chatId, votingName, it.second.file_id)
            it
        }
    }

    private fun getList(voting: String, chatId: Long, closest: Boolean): List<Pair<Pic, Pic>> {
        var list = getListWithViewLimit(chatId, voting, closest, 1)
        if (list.size < 2) {
            list = getListWithViewLimit(chatId, voting, closest, 2)
        }
        return list
    }

    private fun getListWithViewLimit(
        chatId: Long,
        voting: String,
        closest: Boolean,
        viewsLimit: Int
    ): List<Pair<Pic, Pic>> {
        return if (closest)
            PicRepository.list(voting).filter { pic ->
                ViewsRepository.get(
                    chatId,
                    voting,
                    pic.file_id
                ) < viewsLimit
            }.sortedBy { it.rank }.windowed(2, 2) { it[0] to it[1] }
        else {
            val evens = PicRepository.list(voting).filter { pic -> ViewsRepository.get(chatId, voting, pic.file_id) < viewsLimit }
                .filterIndexed { index, _ -> index % 2 == 1 }
                .sortedBy { it.rank }
            val odds = PicRepository.list(voting).filter { pic -> ViewsRepository.get(chatId, voting, pic.file_id) < viewsLimit }
                .filterIndexed { index, _ -> index % 2 == 0}
                .sortedBy { it.rank }
            evens.zip(odds)
        }
    }
}