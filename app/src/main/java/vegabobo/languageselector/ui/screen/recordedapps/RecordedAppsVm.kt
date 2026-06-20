package vegabobo.languageselector.ui.screen.recordedapps

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.DrawablePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vegabobo.languageselector.dao.AppInfoDb
import vegabobo.languageselector.ui.screen.appinfo.capDisplayName
import vegabobo.languageselector.ui.screen.main.getAppIcon
import vegabobo.languageselector.ui.screen.main.getLabel

data class RecordedAppsState(
    val apps: List<RecordedAppRow> = emptyList(),
)

data class RecordedAppRow(
    val pkg: String,
    val name: String,
    val language: String,
    val icon: Painter,
    val isInstalled: Boolean,
)

@HiltViewModel
class RecordedAppsVm @Inject constructor(
    private val app: Application,
    db: AppInfoDb,
) : ViewModel() {
    private val dao = db.appInfoDao()
    private val _uiState = MutableStateFlow(RecordedAppsState())
    val uiState: StateFlow<RecordedAppsState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = app.packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0),
            ).map { it.packageName }.toSet()
            _uiState.update {
                it.copy(
                    apps = dao.getRecordedApps().mapNotNull { row ->
                        val language = row.recordedLanguageTag?.let { tag ->
                            Locale.forLanguageTag(tag).capDisplayName()
                        }.orEmpty()
                        runCatching {
                            val ai = app.packageManager.getApplicationInfo(row.pkg, 0)
                            RecordedAppRow(
                                pkg = row.pkg,
                                name = app.packageManager.getLabel(ai),
                                language = language,
                                icon = DrawablePainter(app.packageManager.getAppIcon(ai)),
                                isInstalled = true,
                            )
                        }.getOrElse {
                            if (installed.contains(row.pkg)) return@mapNotNull null
                            RecordedAppRow(
                                pkg = row.pkg,
                                name = row.name,
                                language = language,
                                icon = DrawablePainter(app.packageManager.defaultActivityIcon),
                                isInstalled = false,
                            )
                        }
                    },
                )
            }
        }
    }
}
