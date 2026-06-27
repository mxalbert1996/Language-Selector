package vegabobo.languageselector.service

import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.ILocaleManager
import android.os.Build
import android.os.LocaleList
import android.os.Process
import kotlin.system.exitProcess
import rikka.shizuku.SystemServiceHelper
import vegabobo.hiddenapi.getCurrentUser
import vegabobo.languageselector.IUserService
import vegabobo.languageselector.logW

class UserService : IUserService.Stub() {

    override fun exit() {
        destroy()
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    private var localeManager: ILocaleManager? = null
    private fun requiresLocaleManager() {
        if (localeManager != null) return
        val localeBinder = SystemServiceHelper.getSystemService("locale")
        localeManager = ILocaleManager.Stub.asInterface(localeBinder)
    }

    override fun setApplicationLocales(packageName: String?, locales: LocaleList?) {
        requiresLocaleManager()
        val currentUser = getCurrentUser()
        if (Build.VERSION.SDK_INT == 33 && Build.VERSION.RELEASE_OR_CODENAME != "UpsideDownCake") {
            localeManager!!.setApplicationLocales(packageName, currentUser, locales)
            return
        }
        localeManager!!.setApplicationLocales(packageName, currentUser, locales, true)
    }

    override fun getApplicationLocales(packageName: String?): LocaleList {
        requiresLocaleManager()
        val currentUser = getCurrentUser()
        return localeManager!!.getApplicationLocales(packageName, currentUser)
    }

    override fun getSystemLocales(): LocaleList {
        requiresLocaleManager()
        return localeManager!!.systemLocales
    }

    private var activityManager: IActivityManager? = null
    private fun requiresActivityManager() {
        if (activityManager != null) return
        val am = SystemServiceHelper.getSystemService("activity")
        activityManager = IActivityManager.Stub.asInterface(am)
    }

    override fun forceStopPackage(packageName: String?) {
        requiresActivityManager()
        val currentUser = getCurrentUser()
        activityManager!!.forceStopPackage(packageName, currentUser)
    }

    private var activityTaskManager: IActivityTaskManager? = null
    private fun requiresActivityTaskManager() {
        if (activityTaskManager != null) return
        val am = SystemServiceHelper.getSystemService("activity_task")
        activityTaskManager = IActivityTaskManager.Stub.asInterface(am)
    }

    override fun getFirstRunningTaskPackage(): String {
        requiresActivityTaskManager()
        val runningTask =
            try {
                activityTaskManager!!.getTasks(1, false, false, -1).first()
            } catch (e: NoSuchMethodError) {
                logW(
                    "getTasks failed, trying again without displayId, error: ${e.stackTraceToString()}",
                )
                activityTaskManager!!.getTasks(1, false, false).first()
            }
        return runningTask.topActivity?.packageName ?: ""
    }
}
