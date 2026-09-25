package com.opdownloader.app.di

import android.content.Context
import com.opdownloader.app.data.local.db.DownloadDao
import com.opdownloader.app.data.local.db.OpDatabase
import com.opdownloader.app.data.mediastore.MediaStoreHelper
import com.opdownloader.app.data.repository.DownloadRepositoryImpl
import com.opdownloader.app.data.security.KeystoreManager
import com.opdownloader.app.domain.repository.DownloadRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOpDatabase(
        @ApplicationContext context: Context
    ): OpDatabase {
        return OpDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(
        database: OpDatabase
    ): DownloadDao {
        return database.downloadDao()
    }
}
