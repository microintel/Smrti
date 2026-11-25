package com.smrti.app.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
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
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quotes", indices = [Index(value = ["text", "author"], unique = true)])
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val author: String = "",
    val category: String = "Quote",
    val isCustom: Boolean = false,
    val lastShownAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    /** Picture chosen for this quote (content:// uri or file path). Null = pick a random one. */
    val imagePath: String? = null,
    /** Active quotes can be sent as reminders; inactive ones are kept but never sent. */
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true
)

/** Result of [QuoteDao.observeCounts]. */
data class QuoteCounts(val total: Int, val active: Int)

/** [path] is either a content:// uri of the original photo (v1.2+) or a file path of a copy made by older versions. */
@Entity(tableName = "images")
data class QuoteImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    val isContent: Boolean get() = path.startsWith("content://")
}

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

    @Update
    suspend fun update(quote: Quote)

    @Query("SELECT * FROM quotes ORDER BY id ASC")
    suspend fun all(): List<Quote>

    @Query("UPDATE quotes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT * FROM quotes WHERE text = :text AND author = :author LIMIT 1")
    suspend fun find(text: String, author: String): Quote?

    @Query("SELECT * FROM quotes WHERE imagePath IS NOT NULL")
    suspend fun withImage(): List<Quote>

    @Query("UPDATE quotes SET imagePath = :path WHERE id = :id")
    suspend fun setImage(id: Long, path: String?)

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun byId(id: Long): Quote?

    @Query("SELECT isFavorite FROM quotes WHERE id = :id")
    suspend fun isFavorite(id: Long): Boolean?

    // One page at a time for the Quotes tab; empty q / category and favOnly = 0 mean "no filter".
    @Query(
        "SELECT * FROM quotes WHERE (:category = '' OR category = :category) " +
            "AND (:favOnly = 0 OR isFavorite = 1) AND isActive = :active " +
            "AND (:q = '' OR text LIKE '%' || :q || '%' OR author LIKE '%' || :q || '%') " +
            "ORDER BY id DESC"
    )
    fun paged(q: String, category: String, favOnly: Int, active: Int): PagingSource<Int, Quote>

    // Every quote the current search / filter matches (what "Select all" picks), not just the loaded page.
    @Query(
        "SELECT id FROM quotes WHERE (:category = '' OR category = :category) " +
            "AND (:favOnly = 0 OR isFavorite = 1) AND isActive = :active " +
            "AND (:q = '' OR text LIKE '%' || :q || '%' OR author LIKE '%' || :q || '%')"
    )
    suspend fun matchingIds(q: String, category: String, favOnly: Int, active: Int): List<Long>

    @Query("SELECT * FROM quotes WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Quote>

    @Query("DELETE FROM quotes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM quotes")
    fun observeCount(): Flow<Int>

    // Total and active in ONE query, so the Quotes tab listens to one flow instead of two
    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(isActive), 0) AS active FROM quotes")
    fun observeCounts(): Flow<QuoteCounts>

    // How many quotes the current search / filter matches (what "Select all" will pick)
    @Query(
        "SELECT COUNT(*) FROM quotes WHERE (:category = '' OR category = :category) " +
            "AND (:favOnly = 0 OR isFavorite = 1) AND isActive = :active " +
            "AND (:q = '' OR text LIKE '%' || :q || '%' OR author LIKE '%' || :q || '%')"
    )
    suspend fun countMatching(q: String, category: String, favOnly: Int, active: Int): Int

    @Query("SELECT COUNT(*) FROM quotes WHERE isActive = 1")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE quotes SET isActive = :active WHERE id IN (:ids)")
    suspend fun setActive(ids: List<Long>, active: Boolean)

    @Query("SELECT DISTINCT category FROM quotes ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM quotes ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): Quote?

    @Query("SELECT * FROM quotes WHERE isActive = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun randomActive(): Quote?

    // Least recently shown first; never-shown quotes are picked at random.
    @Query("SELECT * FROM quotes WHERE isActive = 1 AND category NOT IN (:disabled) ORDER BY lastShownAt ASC, RANDOM() LIMIT 1")
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

    // Used when picking a notification picture, so the whole table never has to be read.
    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int

    @Query("SELECT * FROM images ORDER BY id ASC LIMIT 1 OFFSET :offset")
    suspend fun at(offset: Int): QuoteImage?

    @Query("SELECT COUNT(*) FROM images WHERE path = :path")
    suspend fun countPath(path: String): Int

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Oldest first, so "in series" order is stable.
    @Query("SELECT * FROM images ORDER BY id ASC")
    suspend fun all(): List<QuoteImage>
}

/**
 * Upgrade path: every change to a table gets its own Migration here, so phones that update keep their data.
 * Never edit an old migration; add the next one and bump the database version.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quotes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quotes ADD COLUMN imagePath TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // every quote you already have stays active, so reminders keep working exactly as before
        db.execSQL("ALTER TABLE quotes ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

@Database(entities = [Quote::class, QuoteImage::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quotes(): QuoteDao
    abstract fun images(): ImageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "smrti.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also { instance = it }
            }
    }
}
