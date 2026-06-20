package vegabobo.languageselector.ui.screen.recordedapps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import vegabobo.languageselector.R
import vegabobo.languageselector.ui.components.AppListItem
import vegabobo.languageselector.ui.components.BackButton
import vegabobo.languageselector.ui.screen.BaseScreen
import vegabobo.languageselector.ui.screen.main.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedAppsScreen(
    navigateBack: () -> Unit,
    navigateToAppScreen: (String) -> Unit,
    vm: RecordedAppsVm = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    BaseScreen(
        title = stringResource(R.string.recorded_apps),
        navIcon = { BackButton(navigateBack) },
    ) { padding ->
        if (uiState.apps.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Spacer(modifier = Modifier.weight(2f))
                Text(
                    text = stringResource(R.string.no_recorded_apps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.no_recorded_apps_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.weight(3f))
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(uiState.apps) { app ->
                    AppListItem(
                        app = app.toAppInfo(),
                        extraLabel = app.language,
                        enabled = app.isInstalled,
                        onClickApp = { if (app.isInstalled) navigateToAppScreen(it) },
                        modifier = Modifier.padding(
                            horizontal = 26.dp,
                            vertical = 4.dp,
                        ),
                    )
                }
            }
        }
    }
}

private fun RecordedAppRow.toAppInfo() = AppInfo(
    icon = icon,
    name = name,
    pkg = pkg,
)
