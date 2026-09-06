package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.SearchBook

@Dao
interface SearchBookDao {

    @Query("select * from searchBooks where bookUrl = :bookUrl")
    fun getSearchBook(bookUrl: String): SearchBook?

    @Query("select * from searchBooks where name = :name and author = :author order by originOrder")
    fun getByNameAuthor(name: String, author: String): List<SearchBook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg searchBook: SearchBook)

    @Update
    fun update(vararg searchBook: SearchBook)

    @Delete
    fun delete(vararg searchBook: SearchBook)

    @Query("delete from searchBooks where origin = :origin")
    fun deleteByOrigin(origin: String)

    @Query("delete from searchBooks")
    fun clearAll()
}