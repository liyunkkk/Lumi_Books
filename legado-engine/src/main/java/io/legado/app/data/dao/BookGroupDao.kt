package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface BookGroupDao {

    @Query("select * from book_groups where groupId = :id")
    fun getByID(id: Long): BookGroup?

    @Query("select * from book_groups where groupName = :groupName")
    fun getByName(groupName: String): BookGroup?

    @Query("SELECT * FROM book_groups ORDER BY `order`")
    fun flowAll(): Flow<List<BookGroup>>

    @get:Query("SELECT sum(groupId) FROM book_groups where groupId >= 0")
    val idsSum: Long

    @get:Query("SELECT ifnull(MAX(`order`), 0) FROM book_groups where groupId >= 0")
    val maxOrder: Int

    @get:Query("SELECT * FROM book_groups ORDER BY `order`")
    val all: List<BookGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookGroup: BookGroup)

    @Update
    fun update(vararg bookGroup: BookGroup)

    @Delete
    fun delete(vararg bookGroup: BookGroup)

    fun getUnusedId(): Long {
        var id = 1L
        val idsSum = idsSum
        while (id and idsSum != 0L) {
            id = id.shl(1)
        }
        return id
    }
}