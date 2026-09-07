package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSourceDao {

    @Query("select * from book_sources where bookSourceUrl = :sourceUrl")
    fun getBookSource(sourceUrl: String): BookSource?

    @Query("select * from book_sources order by customOrder asc")
    fun all(): List<BookSource>

    @Query("select * from book_sources order by customOrder asc")
    fun flowAll(): Flow<List<BookSource>>

    @Query("select * from book_sources where enabled = 1 order by customOrder asc")
    fun allEnabled(): List<BookSource>

    @Query("select * from book_sources where enabled = 1 order by customOrder asc")
    fun flowEnabled(): Flow<List<BookSource>>

    @Query("select * from book_sources where enabledExplore = 1 and trim(exploreUrl) <> '' order by customOrder asc")
    fun flowExplore(): Flow<List<BookSource>>

    @Query("select * from book_sources_part order by customOrder asc")
    fun flowAllPart(): Flow<List<BookSourcePart>>

    @Query("select exists(select 1 from book_sources where bookSourceUrl = :sourceUrl)")
    fun has(sourceUrl: String): Boolean

    @Query("select count(*) from book_sources")
    fun count(): Int

    @Query("select ifnull(max(customOrder), 0) from book_sources")
    fun maxOrder(): Int

    @Query("select distinct bookSourceGroup from book_sources where trim(bookSourceGroup) <> ''")
    fun flowGroups(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookSource: BookSource)

    @Update
    fun update(vararg bookSource: BookSource)

    @Delete
    fun delete(vararg bookSource: BookSource)

    @Query("delete from book_sources where bookSourceUrl = :sourceUrl")
    fun delete(sourceUrl: String)

    @Query("delete from book_sources")
    fun deleteAll()

    @Query("update book_sources set enabled = :enabled where bookSourceUrl = :sourceUrl")
    fun enable(sourceUrl: String, enabled: Boolean)

    @Query("update book_sources set respondTime = :respondTime where bookSourceUrl = :sourceUrl")
    fun upRespondTime(sourceUrl: String, respondTime: Long)
    @Query("update book_sources set enabledExplore = :enabledExplore where bookSourceUrl = :sourceUrl")
    fun enableExplore(sourceUrl: String, enabledExplore: Boolean)
}