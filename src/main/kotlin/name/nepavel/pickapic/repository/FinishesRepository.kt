package name.nepavel.pickapic.repository


const val FINISHES = "FINISHES"

object FinishesRepository {
    fun get(voting: String): Int {
        return DbHelper.db.getMap<String, Int>(FINISHES)[voting] ?: 0
    }

    fun increment(voting: String) {
        DbHelper.db.getMap<String, Int>(FINISHES)[voting] = (DbHelper.db.getMap<String, Int>(FINISHES)[voting] ?: 0) + 1
    }
}