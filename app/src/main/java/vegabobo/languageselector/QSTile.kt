package vegabobo.languageselector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.LocaleList
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vegabobo.languageselector.di.appSingletonEntryPoint
import vegabobo.languageselector.service.PrivilegedAcquisitionPolicy
import vegabobo.languageselector.service.PrivilegedAcquisitionResult
import vegabobo.languageselector.service.PrivilegedServiceManager
import vegabobo.languageselector.ui.screen.appinfo.PrefConstants
import vegabobo.languageselector.ui.screen.appinfo.SingleLocale
import vegabobo.languageselector.ui.screen.main.getLabel

class QSTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isLoaded = false
    private val locales = mutableListOf<SingleLocale>()
    private lateinit var targetPackage: ApplicationInfo
    private val recordedLanguageStore by lazy(LazyThreadSafetyMode.NONE) {
        appSingletonEntryPoint(applicationContext).recordedLanguageStore()
    }

    private fun getNextSingleLocale(localeList: LocaleList): SingleLocale {
        if (locales.isEmpty()) {
            throw Exception(
                "getNextSingleLocale() should not be called with empty locales",
            )
        }
        if (localeList.isEmpty) return locales[1]
        for ((i, thisLocale) in locales.withIndex()) {
            if (localeList[0].toLanguageTag() ==
                thisLocale.languageTag
            ) {
                return if (i == locales.lastIndex) {
                    locales.first()
                } else {
                    locales[i + 1]
                }
            }
        }
        return locales.first()
    }

    private fun setDisabledTile() {
        qsTile.label = getString(R.string.app_name)
        qsTile.subtitle = getString(R.string.unavailable)
        qsTile.state = Tile.STATE_UNAVAILABLE
        qsTile.updateTile()
    }

    private fun updateTile() {
        scope.launch {
            val outcome = PrivilegedServiceManager.acquireLease(
                applicationContext,
                PrivilegedAcquisitionPolicy.AUTO,
            )
            if (outcome is PrivilegedAcquisitionResult.NoPrivilege ||
                outcome is PrivilegedAcquisitionResult.TransientFailure
            ) {
                setDisabledTile()
                return@launch
            }
            val lease = (outcome as PrivilegedAcquisitionResult.Acquired).lease
            try {
                val service = lease.service
                val currentAppPackage = service.firstRunningTaskPackage
                targetPackage = packageManager.getApplicationInfo(
                    currentAppPackage,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
                if ((targetPackage.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    targetPackage.packageName == BuildConfig.APPLICATION_ID
                ) {
                    setDisabledTile()
                    return@launch
                }
                var isCustomLocale = false
                val currentLocale = try {
                    val appLocales = service.getApplicationLocales(currentAppPackage)
                    if (!appLocales.isEmpty) {
                        isCustomLocale = true
                        appLocales[0].capDisplayName()
                    } else {
                        ""
                    }
                } catch (_: Exception) {
                    ""
                }.ifBlank { getString(R.string.system_default) }
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.updateTile()
                qsTile.label = currentLocale
                qsTile.subtitle = packageManager.getLabel(targetPackage)
                qsTile.state = if (isCustomLocale) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                qsTile.updateTile()
            } finally {
                lease.release()
            }
        }
    }

    private fun loadLangs() {
        if (!isLoaded) {
            val sp = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)
            val set = sp.getStringSet(PrefConstants.PINNED_LOCALES, emptySet()) ?: emptySet()
            if (set.isNotEmpty()) {
                locales.add(SingleLocale("", ""))
                locales.addAll(set.parseLocales())
            }
            isLoaded = true
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        setDisabledTile()
        loadLangs()
        if (locales.isNotEmpty()) updateTile()
    }

    override fun onStopListening() {
        isLoaded = false
        locales.clear()
        scope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!this::targetPackage.isInitialized) return
        scope.launch {
            val outcome = PrivilegedServiceManager.acquireLease(
                applicationContext,
                PrivilegedAcquisitionPolicy.AUTO,
            )
            if (outcome is PrivilegedAcquisitionResult.NoPrivilege ||
                outcome is PrivilegedAcquisitionResult.TransientFailure
            ) {
                return@launch
            }
            val lease = (outcome as PrivilegedAcquisitionResult.Acquired).lease
            try {
                val service = lease.service
                val currentLocale = service.getApplicationLocales(targetPackage.packageName)
                val nextLocale = getNextSingleLocale(currentLocale)
                val localeList =
                    if (nextLocale.languageTag.isEmpty()) {
                        LocaleList()
                    } else {
                        LocaleList(
                            nextLocale.toLocale(),
                        )
                    }
                service.setApplicationLocales(targetPackage.packageName, localeList)
                if (nextLocale.languageTag.isEmpty()) {
                    recordedLanguageStore.clearRecordedLanguage(
                        targetPackage.packageName,
                    )
                } else {
                    recordedLanguageStore.recordLanguageSelection(
                        targetPackage.packageName,
                        packageManager.getLabel(targetPackage),
                        nextLocale.languageTag,
                    )
                }
                updateTile()
            } finally {
                lease.release()
            }
        }
    }
}
