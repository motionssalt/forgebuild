package com.forgebuild.taskflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, ChatMessage::class], version = 7, exportSchema = false)
@TypeConverters(ChatConverters::class)
abstract class TaskFlowDb : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile private var instance: TaskFlowDb? = null
        fun get(context: Context): TaskFlowDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, TaskFlowDb::class.java, "taskflow.db")
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
