package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.flow.Flow

@Dao
interface RssSourceDao {

    @Query("select * from rssSources where sourceUrl = :sourceUrl")
    fun getByKey(sourceUrl: String): RssSource?

    @Query("select exists(select 1 from rssSources where sourceUrl = :sourceUrl)")
    fun has(sourceUrl: String): Boolean

    @Query("select * from rssSources order by customOrder")
    fun flowAll(): Flow<List<RssSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rssSource: RssSource)

    @Update
    fun update(vararg rssSource: RssSource)

    @Delete
    fun delete(vararg rssSource: RssSource)
}