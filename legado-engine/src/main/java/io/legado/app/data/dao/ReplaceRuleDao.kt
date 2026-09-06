package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReplaceRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplaceRuleDao {

    @Query("SELECT * FROM replace_rules ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules ORDER BY sortOrder ASC")
    fun all(): List<ReplaceRule>

    @Query(
        """select * from replace_rules where isEnabled = 1 and scopeContent = 1
        and (scope is null or scope = '' or (scope like '%' || :name || '%' or scope like '%' || :origin || '%'))
        and (excludeScope is null or (excludeScope not like '%' || :name || '%' and excludeScope not like '%' || :origin || '%'))
        order by sortOrder"""
    )
    fun findEnabledByContentScope(name: String, origin: String): List<ReplaceRule>

    @Query(
        """select * from replace_rules where isEnabled = 1 and scopeTitle = 1
        and (scope is null or scope = '' or (scope like '%' || :name || '%' or scope like '%' || :origin || '%'))
        and (excludeScope is null or (excludeScope not like '%' || :name || '%' and excludeScope not like '%' || :origin || '%'))
        order by sortOrder"""
    )
    fun findEnabledByTitleScope(name: String, origin: String): List<ReplaceRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg replaceRule: ReplaceRule)

    @Update
    fun update(vararg replaceRule: ReplaceRule)

    @Delete
    fun delete(vararg replaceRule: ReplaceRule)
}