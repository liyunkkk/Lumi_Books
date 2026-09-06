package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY durChapterTime DESC")
    fun flowAll(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY durChapterTime DESC")
    fun getAll(): List<Book>

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?

    @Query("SELECT * FROM books WHERE name = :name and author = :author")
    fun getBook(name: String, author: String): Book?

    @Query("SELECT * FROM books WHERE name = :name and origin = :origin")
    fun getBookByOrigin(name: String, origin: String): Book?

    @Query("select exists(select 1 from books where bookUrl = :bookUrl)")
    fun has(bookUrl: String): Boolean

    @Query("select exists(select 1 from books where name = :name and author = :author)")
    fun has(name: String, author: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)

    @Update
    fun update(vararg book: Book)

    @Delete
    fun delete(vararg book: Book)

    @Query("DELETE FROM books")
    fun deleteAll()

    @Transaction
    fun replace(oldBook: Book, newBook: Book) {
        delete(oldBook)
        insert(newBook)
    }

    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)
}