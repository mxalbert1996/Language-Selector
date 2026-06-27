package vegabobo.languageselector.ui.screen.main

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.painter.Painter

enum class SnackBarDisplay {
    NONE,
    MOVED_TO_TOP,
    MOVED_TO_BOTTOM,
}

data class MainScreenState(
    val listOfApps: MutableList<AppInfo> = mutableStateListOf(),
    val history: MutableList<AppInfo> = mutableStateListOf(),
    val hasPrivilegedBackend: Boolean = false,
    val isDropdownVisible: Boolean = false,
    val isAboutDialogVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isShowSystemAppsHome: Boolean = false,
    val snackBarDisplay: SnackBarDisplay = SnackBarDisplay.NONE,

    /* Search bar */
    val isExpanded: Boolean = false,
    val searchTextFieldValue: String = "",
    val selectLabels: MutableList<AppLabels> = mutableStateListOf(),
)

enum class AppLabels {
    SYSTEM_APP,
    MODIFIED,
}

data class AppInfo(
    val icon: Painter,
    val name: String,
    val pkg: String,
    val labels: List<AppLabels> = emptyList(),
) {
    fun isSystemApp() = labels.contains(AppLabels.SYSTEM_APP)
    fun isModified() = labels.contains(AppLabels.MODIFIED)
}

fun PackageManager.getLabel(applicationInfo: ApplicationInfo): String = applicationInfo.loadLabel(
    this,
).toString()

fun PackageManager.getAppIcon(applicationInfo: ApplicationInfo): Drawable = this.getApplicationIcon(
    applicationInfo,
)
