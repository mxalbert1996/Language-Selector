package vegabobo.languageselector.ui.screen.appinfo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.LocaleList
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.DrawablePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.IUserService
import vegabobo.languageselector.LocaleManager
import vegabobo.languageselector.capDisplayName
import vegabobo.languageselector.dao.RecordedLanguageStore
import vegabobo.languageselector.parseLocales
import vegabobo.languageselector.service.PrivilegedAcquisitionResult
import vegabobo.languageselector.service.PrivilegedServiceLease
import vegabobo.languageselector.service.PrivilegedServiceManager
import vegabobo.languageselector.ui.screen.main.getAppIcon
import vegabobo.languageselector.ui.screen.main.getLabel

object PrefConstants {
    const val PINNED_LOCALES = "pinned_locales"
}

@HiltViewModel
class AppInfoVm @Inject constructor(
    private val app: Application,
    private val localeManager: LocaleManager,
    private val recordedLanguageStore: RecordedLanguageStore,
    private val appCoroutineScope: CoroutineScope,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppInfoState())
    val uiState: StateFlow<AppInfoState> = _uiState.asStateFlow()

    private lateinit var appInfo: ApplicationInfo
    private var heldLease: PrivilegedServiceLease? = null

    fun initFromAppId(appId: String) {
        appInfo = app.packageManager.getApplicationInfo(
            appId,
            PackageManager.ApplicationInfoFlags.of(0),
        )
        _uiState.update {
            it.copy(
                appName = app.packageManager.getLabel(appInfo),
                appPackage = appInfo.packageName,
                appIcon = DrawablePainter(app.packageManager.getAppIcon(appInfo)),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            ensureHeldLease()?.let { service ->
                val suggestedLanguages = buildSuggestedLanguages(service.systemLocales)
                val currentLanguage = readCurrentLanguageDisplay(service)
                _uiState.update {
                    it.copy(
                        listOfSuggestedLanguages = suggestedLanguages,
                        currentLanguage = currentLanguage,
                    )
                }
            }
        }

        _uiState.update { it.copy(listOfAllLanguages = localeManager.localeList) }
    }

    private fun updateCurrentLanguageState() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureHeldLease()?.let { service ->
                _uiState.update { it.copy(currentLanguage = readCurrentLanguageDisplay(service)) }
            }
        }
    }

    fun onClickSingleLanguage(index: Int) {
        _uiState.update { it.copy(selectedLanguage = index) }
    }

    fun onBackWhenSelectedLang() {
        _uiState.update { it.copy(selectedLanguage = -1) }
    }

    fun onClickLocale(singleLocale: SingleLocale) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ensureHeldLease()?.let { service ->
                service.setApplicationLocales(
                    appInfo.packageName,
                    LocaleList(singleLocale.toLocale()),
                )
                _uiState.update { it.copy(currentLanguage = readCurrentLanguageDisplay(service)) }
                true
            } ?: false
            if (ok) {
                recordedLanguageStore.recordLanguageSelection(
                    appInfo.packageName,
                    app.packageManager.getLabel(appInfo),
                    singleLocale.languageTag,
                )
            }
        }
    }

    fun onClickSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", appInfo.packageName, null)
        intent.setData(uri)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    fun onClickOpen() {
        val launchIntent =
            app.packageManager.getLaunchIntentForPackage(appInfo.packageName)
        launchIntent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        app.startActivity(launchIntent)
    }

    fun onClickResetLang() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ensureHeldLease()?.let { service ->
                service.setApplicationLocales(appInfo.packageName, LocaleList())
                _uiState.update { it.copy(currentLanguage = readCurrentLanguageDisplay(service)) }
                true
            } ?: false
            if (ok) recordedLanguageStore.clearRecordedLanguage(appInfo.packageName)
        }
    }

    fun onClickForceClose() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureHeldLease()?.forceStopPackage(appInfo.packageName)
        }
    }

    private suspend fun ensureHeldLease(): IUserService? {
        heldLease?.service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        releaseHeldLease()
        return when (val acquired = PrivilegedServiceManager.acquireLease(app)) {
            is PrivilegedAcquisitionResult.Acquired ->
                acquired.lease.also { heldLease = it }.service
            else -> null
        }
    }

    private fun buildSuggestedLanguages(systemLocales: LocaleList): MutableList<SingleLocale> =
        MutableList(systemLocales.size()) { index ->
            val locale = systemLocales[index]
            SingleLocale(locale.capDisplayName(), locale.toLanguageTag())
        }

    private fun readCurrentLanguageDisplay(service: IUserService): String {
        val currentLocale = service.getApplicationLocales(appInfo.packageName)
        return if (currentLocale.isEmpty) "" else currentLocale[0].capDisplayName()
    }

    private suspend fun releaseHeldLease() {
        heldLease?.release()
        heldLease = null
    }

    override fun onCleared() {
        appCoroutineScope.launch {
            releaseHeldLease()
        }
    }

    private fun getSp(): SharedPreferences =
        app.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)

    fun onPinLang(singleLocale: SingleLocale) {
        val sp = getSp()
        val set = sp.getStringSet(PrefConstants.PINNED_LOCALES, emptySet()) ?: emptySet()
        val mset = set.toMutableSet()
        mset.add("${singleLocale.name},${singleLocale.languageTag}")
        sp.edit { putStringSet(PrefConstants.PINNED_LOCALES, mset) }
        updatePinnedLangsFromSP()
    }

    fun onRemovePin(singleLocale: SingleLocale) {
        val sp = getSp()
        val set = sp.getStringSet(PrefConstants.PINNED_LOCALES, emptySet()) ?: emptySet()
        val newSet = mutableSetOf<String>()
        set.forEach {
            if (!it.contains(singleLocale.languageTag)) {
                newSet.add(it)
            }
        }
        sp.edit { putStringSet(PrefConstants.PINNED_LOCALES, newSet) }
        updatePinnedLangsFromSP()
    }

    fun updatePinnedLangsFromSP() {
        val sp = getSp()
        val set = sp.getStringSet(PrefConstants.PINNED_LOCALES, emptySet()) ?: return
        val pinnedLocaleList = set.parseLocales()
        _uiState.update { it.copy(listOfPinnedLanguages = pinnedLocaleList) }
    }
}
