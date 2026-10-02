package com.forgebuild.taskflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

/**
 * Durable entity for AI chat messages.
 *
 * Persisted immediately to SQLite on every user input, agent action, and model reply.
 * Carries an immutable, auto-incrementing SQLite primary key [id] guaranteed to be unique
 * and stable across app restarts and recompositions.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: Role,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Role { USER, MODEL, ACTION }
}

class ChatConverters {
    @TypeConverter
    fun fromRole(role: ChatMessage.Role): String = role.name

    @TypeConverter
    fun toRole(name: String): ChatMessage.Role =
        runCatching { ChatMessage.Role.valueOf(name) }.getOrDefault(ChatMessage.Role.MODEL)
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun getAll(): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int
}
