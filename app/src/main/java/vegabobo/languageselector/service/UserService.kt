package vegabobo.languageselector.service

import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.ILocaleManager
import android.os.Build
import android.os.LocaleList
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess
import rikka.shizuku.SystemServiceHelper
import vegabobo.hiddenapi.getCurrentUser
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.IUserService

class UserService : IUserService.Stub() {

    override fun exit() {
        destroy()
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    private var LOCALE_MANAGER: ILocaleManager? = null
    private fun requiresLocaleManager() {
        if (LOCALE_MANAGER != null) return
        val localeBinder = SystemServiceHelper.getSystemService("locale")
        LOCALE_MANAGER = ILocaleManager.Stub.asInterface(localeBinder)
    }

    override fun setApplicationLocales(packageName: String?, locales: LocaleList?) {
        requiresLocaleManager()
        val currentUser = getCurrentUser()
        if (Build.VERSION.SDK_INT == 33 && Build.VERSION.RELEASE_OR_CODENAME != "UpsideDownCake") {
            LOCALE_MANAGER!!.setApplicationLocales(packageName, currentUser, locales)
            return
        }
        LOCALE_MANAGER!!.setApplicationLocales(packageName, currentUser, locales, true)
    }

    override fun getApplicationLocales(packageName: String?): LocaleList {
        requiresLocaleManager()
        val currentUser = getCurrentUser()
        return LOCALE_MANAGER!!.getApplicationLocales(packageName, currentUser)
    }

    override fun getSystemLocales(): LocaleList {
        requiresLocaleManager()
        return LOCALE_MANAGER!!.systemLocales
    }

    private var ACTIVITY_MANAGER: IActivityManager? = null
    private fun requiresActivityManager() {
        if (ACTIVITY_MANAGER != null) return
        val am = SystemServiceHelper.getSystemService("activity")
        ACTIVITY_MANAGER = IActivityManager.Stub.asInterface(am)
    }

    override fun forceStopPackage(packageName: String?) {
        requiresActivityManager()
        val currentUser = getCurrentUser()
        ACTIVITY_MANAGER!!.forceStopPackage(packageName, currentUser)
    }

    private var ACTIVITY_TASK_MANAGER: IActivityTaskManager? = null
    private fun requiresActivityTaskManager() {
        if (ACTIVITY_TASK_MANAGER != null) return
        val am = SystemServiceHelper.getSystemService("activity_task")
        ACTIVITY_TASK_MANAGER = IActivityTaskManager.Stub.asInterface(am)
    }

    override fun getFirstRunningTaskPackage(): String {
        requiresActivityTaskManager()
        val runningTask =
            try {
                ACTIVITY_TASK_MANAGER!!.getTasks(1, false, false, -1).first()
            } catch (e: NoSuchMethodError) {
                Log.w(
                    BuildConfig.APPLICATION_ID,
                    "getTasks failed, trying again without displayId, error: ${e.stackTraceToString()}",
                )
                ACTIVITY_TASK_MANAGER!!.getTasks(1, false, false).first()
            }
        return runningTask.topActivity?.packageName ?: ""
    }
}
