package com.homeport.app.di

import com.homeport.app.data.mock.MockDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMockDataRepository(): MockDataRepository = MockDataRepository()
}
