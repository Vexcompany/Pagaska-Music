/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.metrolist.music.constants.MaxSongCacheSizeKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.listentogether.ListenTogetherClient
import com.metrolist.music.listentogether.ListenTogetherManager
import com.metrolist.music.playback.DynamicLruCacheEvictor
import com.metrolist.music.playback.OfflineCacheManager
import com.metrolist.music.playback.OfflineCacheRegistry
import com.metrolist.music.utils.SongCacheConfig
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet
import javax.inject.Singleton

private class LazyCache(
    private val create: () -> SimpleCache,
    private val protectResource: ((String) -> Boolean)? = null,
) : Cache {
    private val lock = Any()

    @Volatile private var cache: SimpleCache? = null

    private fun delegate(): SimpleCache = cache ?: synchronized(lock) { cache ?: create().also { cache = it } }

    override fun addListener(
        key: String,
        listener: Cache.Listener,
    ) = delegate().addListener(key, listener)

    override fun removeListener(
        key: String,
        listener: Cache.Listener,
    ) = delegate().removeListener(key, listener)

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> = delegate().getCachedSpans(key)

    override fun getKeys(): NavigableSet<String> = TreeSet(delegate().keys)

    override fun getCacheSpace(): Long = delegate().cacheSpace

    override fun getUid(): Long = delegate().uid

    override fun getCachedLength(
        key: String,
        position: Long,
        length: Long,
    ): Long = delegate().getCachedLength(key, position, length)

    override fun getCachedBytes(
        key: String,
        position: Long,
        length: Long,
    ): Long = delegate().getCachedBytes(key, position, length)

    override fun applyContentMetadataMutations(
        key: String,
        mutations: ContentMetadataMutations,
    ) = delegate().applyContentMetadataMutations(key, mutations)

    override fun getContentMetadata(key: String): ContentMetadata = delegate().getContentMetadata(key)

    override fun startReadWrite(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan = delegate().startReadWrite(key, position, length)

    override fun startReadWriteNonBlocking(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan? = delegate().startReadWriteNonBlocking(key, position, length)

    override fun startFile(
        key: String,
        position: Long,
        maxLength: Long,
    ): File = delegate().startFile(key, position, maxLength)

    override fun commitFile(
        file: File,
        length: Long,
    ) = delegate().commitFile(file, length)

    override fun releaseHoleSpan(holeSpan: CacheSpan) = delegate().releaseHoleSpan(holeSpan)

    override fun removeSpan(span: CacheSpan) = delegate().removeSpan(span)

    override fun removeResource(key: String) {
        // Offline Cache is a protected layer on top of the same physical player cache. Generic
        // cache clears and playback error recovery must not destroy an offline resource. Explicit
        // removal goes through OfflineCacheManager, which unprotects the id before this call.
        if (protectResource?.invoke(key) == true) return
        delegate().removeResource(key)
    }

    override fun isCached(
        key: String,
        position: Long,
        length: Long,
    ): Boolean = delegate().isCached(key, position, length)

    override fun release() {
        val cacheToRelease =
            synchronized(lock) {
                cache.also { cache = null }
            }
        cacheToRelease?.release()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase = InternalDatabase.newInternalDatabaseInstance(context)

    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache(
            create = {
                // Seed the runtime byte budget once, before the first eviction can happen. Later
                // changes are pushed by the DataStore collector in App.observeSettingsChanges(), so the
                // evictor never has to block on I/O while SimpleCache's monitor is held.
                SongCacheConfig.updateIfUnset(
                    context.dataStore[MaxSongCacheSizeKey] ?: SongCacheConfig.DEFAULT_MB,
                )
                SimpleCache(
                    context.filesDir.resolve("exoplayer"),
                    // Dynamic on purpose: LeastRecentlyUsedCacheEvictor takes an immutable maxBytes and
                    // this Cache is a singleton, so the "max song cache size" slider would otherwise
                    // only apply after the process is killed.
                    DynamicLruCacheEvictor(),
                    databaseProvider,
                )
            },
            protectResource = OfflineCacheRegistry::isProtected,
        )

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(
                context.filesDir.resolve("download"),
                NoOpCacheEvictor(),
                databaseProvider,
            )
        }

    @Singleton
    @Provides
    fun provideOfflineCacheManager(
        database: MusicDatabase,
        @PlayerCache playerCache: Cache,
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
    ): OfflineCacheManager = OfflineCacheManager(database, playerCache, context, applicationScope)

    @Singleton
    @Provides
    fun provideListenTogetherClient(
        @ApplicationContext context: Context,
    ): ListenTogetherClient = ListenTogetherClient(context)

    @Singleton
    @Provides
    fun provideListenTogetherManager(
        @ApplicationContext context: Context,
        client: ListenTogetherClient,
    ): ListenTogetherManager = ListenTogetherManager(client, context)
}
