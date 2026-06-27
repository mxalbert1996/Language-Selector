package vegabobo.languageselector.ui.screen.main

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.DrawablePainter
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.IUserService
import vegabobo.languageselector.dao.AppInfoDb
import vegabobo.languageselector.dao.RecordedLanguageStore
import vegabobo.languageselector.logE
import vegabobo.languageselector.service.PrivilegedAcquisitionResult
import vegabobo.languageselector.service.PrivilegedServiceLease
import vegabobo.languageselector.service.PrivilegedServiceManager

@HiltViewModel
class MainScreenVm @Inject constructor(
    val app: Application,
    appInfoDb: AppInfoDb,
    private val recordedLanguageStore: RecordedLanguageStore,
    private val appCoroutineScope: CoroutineScope,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()
    var lastSelectedApp: AppInfo? = null
    val dao = appInfoDb.appInfoDao()
    private var heldLease: PrivilegedServiceLease? = null

    fun getIndexFromAppInfoItem(): Int = _uiState.value.listOfApps.indexOfFirst {
        it.pkg == lastSelectedApp?.pkg
    }

    private fun loadOperationMode() {
        if (Shell.getShell().isAlive) {
            Shell.getShell().close()
        }
        Shell.getShell()
        if (Shell.isAppGrantedRoot() == true) {
            _uiState.update { it.copy(operationMode = OperationMode.ROOT) }
            return
        }

        val isAvail = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (isAvail) {
            _uiState.update { it.copy(operationMode = OperationMode.SHIZUKU) }
            return
        }

        _uiState.update { it.copy(operationMode = OperationMode.NONE) }
    }

    init {
        fillListOfApps()
    }

    private fun parseAppInfo(a: ApplicationInfo, languagePreferences: LocaleList?): AppInfo {
        val isSystemApp = (a.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val labels = arrayListOf<AppLabels>()
        if (isSystemApp) {
            labels.add(AppLabels.SYSTEM_APP)
        }
        if (languagePreferences?.isEmpty == false) {
            labels.add(AppLabels.MODIFIED)
        }
        return AppInfo(
            icon = DrawablePainter(app.packageManager.getAppIcon(a)),
            name = app.packageManager.getLabel(a),
            pkg = a.packageName,
            labels = labels,
        )
    }

    private fun fillListOfApps() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.operationMode == OperationMode.NONE) {
                loadOperationMode()
            }
            val packageList = buildAppList()
            val sortedList =
                packageList.sortedBy { it.name.lowercase() }.sortedBy { !it.isModified() }
            _uiState.value.listOfApps.clear()
            _uiState.value.listOfApps.addAll(sortedList)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun buildAppList(): List<AppInfo> {
        val packages = getInstalledPackages()
        val service = ensureHeldLease()
        val localesByPackage = mutableMapOf<String, LocaleList?>()
        if (service != null) {
            packages.forEach { pkg ->
                localesByPackage[pkg.packageName] = try {
                    service.getApplicationLocales(pkg.packageName)
                } catch (e: Throwable) {
                    logE(e = e)
                    null
                }
            }
        }
        return packages.map { pkg ->
            parseAppInfo(
                pkg,
                localesByPackage[pkg.packageName],
            )
        }
    }

    private suspend fun fetchAppLocalesWithRetry(packageName: String): LocaleList? {
        repeat(2) {
            var localeList: LocaleList? = null
            val service = ensureHeldLease()
            val ok = if (service == null) {
                false
            } else {
                try {
                    localeList = service.getApplicationLocales(packageName)
                    true
                } catch (_: Throwable) {
                    releaseHeldLease()
                    false
                }
            }
            if (ok) localeList?.let { return it }
        }
        return null
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

    private suspend fun releaseHeldLease() {
        heldLease?.release()
        heldLease = null
    }

    private fun getInstalledPackages(): List<ApplicationInfo> =
        app.packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0),
        ).mapNotNull {
            if (!it.enabled || BuildConfig.APPLICATION_ID == it.packageName) {
                null
            } else {
                it
            }
        }

    fun toggleDropdown() {
        val newDropdownVisibility = !uiState.value.isDropdownVisible
        _uiState.update { it.copy(isDropdownVisible = newDropdownVisibility) }
    }

    fun toggleSystemAppsVisibility() {
        val newShowSystemApps = !uiState.value.isShowSystemAppsHome
        _uiState.update {
            it.copy(
                isLoading = true,
                isShowSystemAppsHome = newShowSystemApps,
            )
        }
        fillListOfApps()
        toggleDropdown()
    }

    fun onClickProceedShizuku() {
        loadOperationMode()
    }

    val searchQuery = mutableStateOf("")
    private val handler = Handler(Looper.getMainLooper())
    private var workRunnable: Runnable? = null

    fun onSearchTextFieldChange(newText: String) {
        _uiState.update { it.copy(searchTextFieldValue = newText) }

        workRunnable?.let {
            handler.removeCallbacks(it)
        }

        Runnable { searchQuery.value = newText }.also {
            workRunnable = it
            handler.postDelayed(it, 1000)
        }
    }

    fun onSearchExpandedChange() {
        val isExpanded = !uiState.value.isExpanded
        _uiState.update { it.copy(isExpanded = isExpanded) }
        if (isExpanded) {
            updateHistory()
        } else {
            _uiState.update { it.copy(searchTextFieldValue = "") }
        }
    }

    fun onSelectedLabelChange(label: AppLabels) {
        val lb = _uiState.value.selectLabels
        if (lb.contains(label)) {
            lb.remove(label)
        } else {
            lb.add(label)
        }
    }

    private fun updateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val appInfoList = dao.getHistory().map { it.pkg }
            val history = appInfoList.mapNotNull { pkg ->
                val listOfApps = _uiState.value.listOfApps
                val idx = listOfApps.indexOfFirst { it.pkg == pkg }
                if (idx == -1) {
                    null
                } else {
                    listOfApps[idx]
                }
            }
            _uiState.value.history.clear()
            _uiState.value.history.addAll(history)
        }
    }

    private fun addAppToHistory(ai: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            recordedLanguageStore.recordHistorySelection(
                ai.pkg,
                ai.name,
                System.currentTimeMillis(),
            )
            updateHistory()
        }
    }

    fun onClickClear() {
        viewModelScope.launch(Dispatchers.IO) {
            recordedLanguageStore.clearAllHistory()
            updateHistory()
        }
    }

    suspend fun reloadLastSelectedItem() {
        val lastSelectedApp = lastSelectedApp ?: return
        val pkg = app.packageManager.getApplicationInfo(lastSelectedApp.pkg, 0)
        val languagePreferences = fetchAppLocalesWithRetry(pkg.packageName)
        val updatedAi = parseAppInfo(pkg, languagePreferences)
        val apps = _uiState.value.listOfApps
        val idx = apps.indexOfFirst { it.pkg == updatedAi.pkg }
        if (idx != -1 && updatedAi.labels != apps[idx].labels) {
            apps[idx] = updatedAi
            val newList = _uiState.value.listOfApps.sortedBy { it.name.lowercase() }
                .sortedBy { !it.isModified() }.toMutableList()
            _uiState.update {
                it.copy(
                    listOfApps = newList,
                    snackBarDisplay = if (updatedAi.isModified()) {
                        SnackBarDisplay.MOVED_TO_TOP
                    } else {
                        SnackBarDisplay.MOVED_TO_BOTTOM
                    },
                )
            }
            return
        }
    }

    fun resetSnackBarDisplay() = _uiState.update { it.copy(snackBarDisplay = SnackBarDisplay.NONE) }

    fun onClickApp(ai: AppInfo) {
        lastSelectedApp = ai
        addAppToHistory(ai)
    }

    override fun onCleared() {
        appCoroutineScope.launch {
            releaseHeldLease()
        }
    }
}
