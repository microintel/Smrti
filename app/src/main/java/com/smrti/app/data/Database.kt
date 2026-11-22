package com.smrti.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quotes", indices = [Index(value = ["text", "author"], unique = true)])
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val author: String = "",
    val category: String = "Quote",
    val isCustom: Boolean = false,
    val lastShownAt: Long = 0
)

@Entity(tableName = "images")
data class QuoteImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY id DESC")
    fun observeAll(): Flow<List<Quote>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(quote: Quote): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(quotes: List<Quote>): List<Long>

    @Delete
    suspend fun delete(quote: Quote)

    @Query("SELECT COUNT(*) FROM quotes")
    fun observeCount(): Flow<Int>

    @Query("SELECT DISTINCT category FROM quotes ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM quotes ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): Quote?

    // Least recently shown first; never-shown quotes are picked at random.
    @Query("SELECT * FROM quotes WHERE category NOT IN (:disabled) ORDER BY lastShownAt ASC, RANDOM() LIMIT 1")
    suspend fun next(disabled: List<String>): Quote?

    @Query("DELETE FROM quotes WHERE isCustom = 0 AND text IN (:texts)")
    suspend fun deleteDefaults(texts: List<String>)

    @Query("UPDATE quotes SET lastShownAt = :time WHERE id = :id")
    suspend fun markShown(id: Long, time: Long)
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM images ORDER BY id DESC")
    fun observeAll(): Flow<List<QuoteImage>>

    @Insert
    suspend fun insert(image: QuoteImage): Long

    @Delete
    suspend fun delete(image: QuoteImage)

    @Query("SELECT * FROM images ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): QuoteImage?

    // Oldest first, so "in series" order is stable.
    @Query("SELECT * FROM images ORDER BY id ASC")
    suspend fun all(): List<QuoteImage>
}

@Database(entities = [Quote::class, QuoteImage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quotes(): QuoteDao
    abstract fun images(): ImageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "smrti.db"
                ).build().also { instance = it }
            }
    }
}
