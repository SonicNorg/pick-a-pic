package name.nepavel.pickapic.repository

import org.telegram.abilitybots.api.db.DBContext

const val VIEWS = "VIEWS"

object ViewsRepository {
    lateinit var db: DBContext

    fun get(chatId: Long, votingName: String, picId: String): Int {
        return db.getMap<Long, Map<String, Map<String, Int>>>(VIEWS)[chatId]?.get(votingName)?.get(picId) ?: 0
    }

    fun increment(chatId: Long, votingName: String, picId: String) {
        val views = db.getMap<Long, Map<String, Map<String, Int>>>(VIEWS)
        val userViews = views.getOrDefault(chatId, mapOf()).toMutableMap()
        val userVotingViews = userViews.getOrDefault(votingName, mapOf()).toMutableMap()
        val picViews = userVotingViews.getOrDefault(picId, 0) + 1
        userVotingViews[picId] = picViews
        userViews[votingName] = userVotingViews
        views[chatId] = userViews
    }

}