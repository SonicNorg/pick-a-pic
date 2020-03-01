package name.nepavel.pickapic.domain

import java.io.Serializable
import java.time.LocalDate

data class Voting(val name:String, val start: LocalDate, val end: LocalDate, val state: State) : Serializable {
    override fun toString(): String {
        return "$name: $start - $end, currently is $state"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Voting

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }


}

enum class State {
    CREATED, STARTED, CLOSED
}