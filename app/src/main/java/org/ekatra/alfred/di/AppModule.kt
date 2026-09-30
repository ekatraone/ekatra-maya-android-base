package org.ekatra.alfred.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.persona.ActivePersona
import org.ekatra.alfred.persona.Persona
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings = AppSettings(context)

    @Provides
    fun providePersona(): Persona = ActivePersona
}
