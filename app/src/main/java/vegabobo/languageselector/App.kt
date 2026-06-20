package vegabobo.languageselector

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import vegabobo.languageselector.sync.AppLanguageSyncCoordinator

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
        AppLanguageSyncCoordinator.enqueuePeriodic(this)
    }
}
