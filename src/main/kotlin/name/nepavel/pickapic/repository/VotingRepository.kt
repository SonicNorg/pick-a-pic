package name.nepavel.pickapic.repository

import name.nepavel.pickapic.domain.State
import name.nepavel.pickapic.domain.Voting

const val VOTING = "VOTING"

object VotingRepository {

    fun list(state: State? = null): Set<Voting> {
        return DbHelper.db.getSet<Voting>(VOTING).apply {
            if (state != null) {
                filter {
                    it.state == state
                }
            }
        }
    }

    fun save(voting: Voting) {
        DbHelper.db.getSet<Voting>(VOTING).apply {
            remove(voting)
            add(voting)
        }
    }

    fun get(voting: String): Voting {
        return DbHelper.db.getSet<Voting>(VOTING).first { it.name == voting }
    }
}