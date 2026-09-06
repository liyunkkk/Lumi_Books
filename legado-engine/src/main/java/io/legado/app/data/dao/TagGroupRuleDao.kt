package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.TagGroupRule
import kotlinx.coroutines.flow.Flow

@Dao
interface TagGroupRuleDao {

    @Query("SELECT * FROM tag_group_rules ORDER BY `order` ASC")
    fun getAll(): List<TagGroupRule>

    @Query("SELECT * FROM tag_group_rules ORDER BY `order` ASC")
    fun flowAll(): Flow<List<TagGroupRule>>

    @Query("SELECT * FROM tag_group_rules WHERE id = :id")
    fun getById(id: Long): TagGroupRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rule: TagGroupRule)

    @Update
    fun update(vararg rule: TagGroupRule)

    @Delete
    fun delete(vararg rule: TagGroupRule)
}