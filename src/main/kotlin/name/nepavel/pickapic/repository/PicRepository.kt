package name.nepavel.pickapic.repository

import name.nepavel.pickapic.domain.Pic
import org.telegram.abilitybots.api.db.DBContext
import java.util.*

const val PICS = "PICS"

object PicRepository {
    lateinit var db: DBContext

    fun add(voting: String, vararg pics: Pic) {
        val pcs = db.getMap<String, Set<Pic>>(PICS)
        pcs[voting] = pcs.getOrDefault(voting, setOf()).toMutableSet().apply {
            this.removeAll(pics)
            this.addAll(pics)
        }
    }

    fun list(voting: String): Set<Pic> {
        return db.getMap<String, Set<Pic>>(PICS)[voting] ?: setOf()
    }

    fun save(voting: String, pic: Pic) {
        val pcs = db.getMap<String, Set<Pic>>(PICS)
        pcs[voting] = pcs.getOrDefault(voting, setOf()).toMutableSet().apply {
            this.remove(pic)
            this.add(pic)
        }
    }

    fun get(voting: String, id: UUID): Pic {
        return db.getMap<String, Set<Pic>>(PICS)[voting]!!.first { it.id == id }
    }

    fun get(voting: String, hashCode: Int): Pic {
        return db.getMap<String, Set<Pic>>(PICS)[voting]!!.first { it.hashCode() == hashCode }
    }
}