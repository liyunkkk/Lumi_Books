package io.legado.app.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.dao.TagGroupRuleDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.TagGroupRule
import splitties.init.appCtx

/**
 * 书源引擎自带的独立 Room 数据库。
 *
 * 与宿主 App（Lumi）原有的数据库物理隔离：单独的文件名 legado_engine.db，
 * 单独的 schema 版本线，双方互不影响，避免迁移冲突。
 */
@Database(
    entities = [
        Book::class,
        BookChapter::class,
        BookSource::class,
        BookGroup::class,
        Bookmark::class,
        SearchBook::class,
        ReplaceRule::class,
        Cache::class,
        Cookie::class,
        RssSource::class,
        TagGroupRule::class,
        HighlightTagRule::class,
    ],
    views = [BookSourcePart::class],
    version = LegadoDatabase.VERSION,
    exportSchema = false,
)
abstract class LegadoDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookChapterDao: BookChapterDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookGroupDao: BookGroupDao
    abstract val searchBookDao: SearchBookDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val cacheDao: CacheDao
    abstract val cookieDao: CookieDao
    abstract val rssSourceDao: RssSourceDao
    abstract val tagGroupRuleDao: TagGroupRuleDao

    companion object {
        const val VERSION = 1
        const val DB_NAME = "legado_engine.db"
    }
}

val appDb: LegadoDatabase by lazy {
    Room.databaseBuilder(appCtx, LegadoDatabase::class.java, LegadoDatabase.DB_NAME)
        // 引擎库仅存放书源/缓存类数据，结构变更时直接重建，避免阻塞宿主升级
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}