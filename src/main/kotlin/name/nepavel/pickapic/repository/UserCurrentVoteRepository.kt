package name.nepavel.pickapic.repository

const val CURRENT = "CURRENT"

object UserCurrentVoteRepository {

    fun save(userId: Long, currentVoting: String?) {
        DbHelper.db.getMap<Long, String>(CURRENT)[userId] = currentVoting
    }

    fun get(userId: Long): String? {
        return DbHelper.db.getMap<Long, String>(CURRENT)[userId]
    }
}