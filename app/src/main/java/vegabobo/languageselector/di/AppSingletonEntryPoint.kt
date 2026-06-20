package vegabobo.languageselector.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import vegabobo.languageselector.dao.AppInfoDb
import vegabobo.languageselector.dao.RecordedLanguageStore

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSingletonEntryPoint {
    fun appInfoDb(): AppInfoDb
    fun recordedLanguageStore(): RecordedLanguageStore
}

fun appSingletonEntryPoint(context: Context): AppSingletonEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        AppSingletonEntryPoint::class.java,
    )
