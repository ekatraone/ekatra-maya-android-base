package org.ekatra.alfred.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ekatra.alfred.data.local.*
import org.ekatra.alfred.data.repository.ChatRepository
import org.ekatra.alfred.data.repository.SavedContentRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EkatraDatabase {
        return EkatraDatabase.getInstance(context)
    }

    @Provides
    fun provideChatSessionDao(db: EkatraDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideChatMessageDao(db: EkatraDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideSavedAnswerDao(db: EkatraDatabase): SavedAnswerDao = db.savedAnswerDao()

    @Provides
    fun provideSavedChartDao(db: EkatraDatabase): SavedChartDao = db.savedChartDao()

    @Provides
    fun provideUserProfileDao(db: EkatraDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideSyncQueueDao(db: EkatraDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideChatRepository(
        sessionDao: ChatSessionDao,
        messageDao: ChatMessageDao
    ): ChatRepository = ChatRepository(sessionDao, messageDao)

    @Provides
    @Singleton
    fun provideSavedContentRepository(
        savedAnswerDao: SavedAnswerDao,
        savedChartDao: SavedChartDao
    ): SavedContentRepository = SavedContentRepository(savedAnswerDao, savedChartDao)
}
