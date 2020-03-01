package name.nepavel.pickapic.repository

import name.nepavel.pickapic.domain.State
import name.nepavel.pickapic.domain.Voting
import org.telegram.abilitybots.api.db.DBContext

const val VOTING = "VOTING"

object VotingRepository {
    lateinit var db: DBContext

    fun list(state: State? = null): Set<Voting> {
        return db.getSet<Voting>(VOTING).apply {
            if (state != null) {
                filter {
                    it.state == state
                }
            }
        }
    }

    fun save(voting: Voting) {
        db.getSet<Voting>(VOTING).apply {
            remove(voting)
            add(voting)
        }
    }

    fun get(voting: String): Voting {
        return db.getSet<Voting>(VOTING).first { it.name == voting }
    }
}