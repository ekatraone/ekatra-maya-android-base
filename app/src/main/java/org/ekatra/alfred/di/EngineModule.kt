package org.ekatra.alfred.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import org.ekatra.alfred.InferenceEngine
import org.ekatra.alfred.LlamaEngine
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(): LlamaEngine = LlamaEngine()

    /** All consumers should inject [InferenceEngine], not [LlamaEngine]. */
    @Provides
    @Singleton
    fun provideInferenceEngine(llama: LlamaEngine): InferenceEngine = llama

    /**
     * Single Mutex shared by ChatViewModel and AlfredServer to prevent
     * concurrent inference calls (llama.cpp is single-threaded per context).
     */
    @Provides
    @Singleton
    @Named("inference")
    fun provideInferenceMutex(): Mutex = Mutex()
}
