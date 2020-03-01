package name.nepavel.pickapic.repository

import org.telegram.abilitybots.api.db.DBContext

const val CURRENT = "CURRENT"

object UserCurrentVoteRepository {
    lateinit var db: DBContext

    fun save(userId: Long, currentVoting: String?) {
        db.getMap<Long, String>(CURRENT)[userId] = currentVoting
    }

    fun get(userId: Long): String? {
        return db.getMap<Long, String>(CURRENT)[userId]
    }
}