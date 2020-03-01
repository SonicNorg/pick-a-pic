package name.nepavel.pickapic.domain

import java.io.Serializable
import java.util.*

data class Pic(val file_id: String, val id: UUID = UUID.randomUUID(), val rank: Float = 1400f) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pic

        if (id != other.id) return false
        if (file_id != other.file_id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + file_id.hashCode()
        return result
    }
}