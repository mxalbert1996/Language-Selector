package vegabobo.languageselector.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.LocaleManager
import vegabobo.languageselector.dao.AppInfoDb

@InstallIn(SingletonComponent::class)
@Module
object Modules {

    @Singleton
    @Provides
    fun provideLocaleManager(): LocaleManager = LocaleManager()

    @Singleton
    @Provides
    fun provideSharedPreferences(
        app: Application,
    ): SharedPreferences = app.getSharedPreferences(
        BuildConfig.APPLICATION_ID,
        Context.MODE_PRIVATE,
    )

    @Singleton
    @Provides
    fun provideAppInfoDb(
        app: Application,
    ): AppInfoDb = Room.databaseBuilder(app, AppInfoDb::class.java, "app-info-db").build()
}
