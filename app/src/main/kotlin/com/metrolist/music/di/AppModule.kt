package com.metrolist.music.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.Factory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.DatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DynamicLruCacheEvictor
import com.metrolist.music.playback.OfflineCacheManager
import com.metrolist.music.playback.OfflineCacheRegistry
import com.metrolist.music.playback.PlayerCache
import com.metrolist.music.playback.DownloadCache
import com.metrolist.music.playback.SongCacheConfig
import com.metrolist.music.settings.MaxSongCacheSizeKey
import com.metrolist.music.utils.dataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
        LazyCache(
            create = {
                SimpleCache(
                    context.filesDir.resolve("download"),
                    NoOpCacheEvictor(),
                    databaseProvider,
                )
            },
        )

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
