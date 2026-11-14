package com.kodraliu.localrock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.devices.DeviceListScreen
import com.kodraliu.localrock.ui.devices.DeviceListViewModel
import com.kodraliu.localrock.ui.intro.SplashScreen
import com.kodraliu.localrock.ui.intro.WelcomeScreen
import com.kodraliu.localrock.ui.login.LoginScreen
import com.kodraliu.localrock.ui.onboarding.AddVacuumScreen
import com.kodraliu.localrock.ui.onboarding.ServerSetupScreen
import com.kodraliu.localrock.ui.settings.APP_VERSION
import com.kodraliu.localrock.ui.settings.SettingsScreen
import com.kodraliu.localrock.ui.vacuum.CameraLiveViewScreen
import com.kodraliu.localrock.ui.vacuum.ScheduleScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDetailScreen
import com.kodraliu.localrock.ui.vacuum.VacuumHistoryScreen
import com.kodraliu.localrock.ui.vacuum.VacuumPinGoScreen
import com.kodraliu.localrock.ui.vacuum.VacuumRemoteScreen
import com.kodraliu.localrock.ui.vacuum.VacuumSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel
import com.kodraliu.localrock.ui.vacuum.MapEditScreen
import com.kodraliu.localrock.ui.vacuum.ZoneCleanScreen
import com.kodraliu.localrock.ui.whatsnew.WhatsNewSheet
import com.kodraliu.localrock.ui.whatsnew.shouldShowWhatsNew
import com.kodraliu.localrock.ui.whatsnew.whatsNewFor
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.kodraliu.localrock.ui.components.ComingSoonScreen
import com.kodraliu.localrock.ui.strings.MapStrings
import com.kodraliu.localrock.ui.strings.SettingsStrings
import com.kodraliu.localrock.ui.vacuum.FirmwareUpdateScreen
import com.kodraliu.localrock.ui.vacuum.VacuumBetaFeaturesScreen
import com.kodraliu.localrock.ui.vacuum.VacuumCarpetScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDndScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDockSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumFloorSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumMaintenanceDetailScreen
import com.kodraliu.localrock.ui.vacuum.VacuumMaintenanceScreen
import com.kodraliu.localrock.ui.vacuum.VacuumMatterScreen
import com.kodraliu.localrock.ui.vacuum.VacuumObstacleSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumMoreOptionsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumRobotVoiceScreen
import com.kodraliu.localrock.ui.vacuum.VacuumProductInfoScreen
import com.kodraliu.localrock.ui.vacuum.VacuumChargingModeScreen

@Composable
fun AppNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val userData by container.authRepository.userData.collectAsState()
    val serverUrl by container.appSettings.serverBaseUrl.collectAsState()
    val introSeen by container.appSettings.introSeen.collectAsState()
    val demoMode by container.appSettings.demoMode.collectAsState()
    val lastSeenVersion by container.appSettings.lastSeenVersion.collectAsState()
    var splashDone by remember { mutableStateOf(false) }


    LaunchedEffect(splashDone, introSeen, userData, serverUrl, demoMode) {
        if (!splashDone) return@LaunchedEffect
        val destination: Any = when {
            !introSeen -> Welcome
            demoMode -> DeviceList
            serverUrl.isNullOrBlank() -> ServerSetup
            userData == null -> Login
            else -> DeviceList
        }
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // A tapped notification names a robot. Go there once the app is past its first screens and
    // the list that robot lives in is loaded, replacing whatever was open above the list.
    val pendingOpen by container.pendingOpenDuid.collectAsState()
    val devices by container.deviceRepository.devices.collectAsState()
    LaunchedEffect(splashDone, userData, pendingOpen, devices) {
        val duid = pendingOpen ?: return@LaunchedEffect
        if (!splashDone || userData == null) return@LaunchedEffect
        if (devices.none { it.duid == duid && it.localKey != null }) return@LaunchedEffect
        container.consumePendingOpen()
        navController.navigate(VacuumGraph(duid)) { popUpTo<DeviceList>() }
    }

    NavHost(navController = navController, startDestination = Splash) {

        composable<Splash> {
            SplashScreen(onTimeout = { splashDone = true })
        }

        composable<Welcome> {
            WelcomeScreen()
        }

        composable<ServerSetup> { entry ->
            // Reached either as the first-run step (nothing to go back to) or from Login.
            val canGoBack = remember(entry) { navController.previousBackStackEntry != null }
            ServerSetupScreen(
                onDone = {
                    navController.navigate(Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onBack = if (canGoBack) ({ navController.popBackStack(); Unit }) else null,
            )
        }

        composable<Login> {
            LoginScreen(onServerSetup = { navController.navigate(ServerSetup) })
        }

        composable<DeviceList> {
            val viewModel: DeviceListViewModel = viewModel { DeviceListViewModel(container) }
            DeviceListScreen(
                viewModel = viewModel,
                onSettings = { navController.navigate(AppSettings) },
                onDeviceClick = { device -> navController.navigate(VacuumGraph(device.duid)) },
                onAddVacuum = { navController.navigate(AddVacuum()) },
                onPairDevice = { duid -> navController.navigate(AddVacuum(duid)) },
            )
            // Once after an update, over the first screen that is really the app.
            val release = whatsNewFor(APP_VERSION)
            if (release != null && shouldShowWhatsNew(introSeen, lastSeenVersion, APP_VERSION)) {
                WhatsNewSheet(release = release, onDismiss = { container.appSettings.setLastSeenVersion(APP_VERSION) })
            }
        }

        composable<AppSettings> {
            SettingsScreen(
                onDone = { navController.popBackStack() },
                allowCancel = !serverUrl.isNullOrBlank(),
            )
        }

        composable<AddVacuum> { entry ->
            AddVacuumScreen(onBack = { navController.popBackStack() }, initialDuid = entry.toRoute<AddVacuum>().duid)
        }

        navigation<VacuumGraph>(startDestination = VacuumMain) {
            val back: () -> Unit = { navController.popBackStack() }
            val open: (Any) -> Unit = { navController.navigate(it) }

            vacuumComposable<VacuumMain>(navController, container) { viewModel ->
                VacuumDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack<VacuumGraph>(inclusive = true) },
                    onSettings = { navController.navigate(VacuumSettings) },
                    onZoneClean = { navController.navigate(VacuumZoneClean) },
                    onMapEdit = { navController.navigate(VacuumMapEdit) },
                    onSchedule = { navController.navigate(VacuumSchedule) },
                    onCamera = { navController.navigate(VacuumCamera) },
                    onRemote = { navController.navigate(VacuumRemote) },
                    onPinGo = { navController.navigate(VacuumPinGo) },
                    onHistory = { navController.navigate(VacuumHistory) },
                    onNavigate = open,
                )
            }
            vacuumComposable<VacuumRemote>(navController, container) { VacuumRemoteScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumPinGo>(navController, container) { VacuumPinGoScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumHistory>(navController, container) { VacuumHistoryScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumCamera>(navController, container) { CameraLiveViewScreen(viewModel = it, onBack = back, onNavigate = open) }
            vacuumComposable<VacuumZoneClean>(navController, container) { ZoneCleanScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumMapEdit>(navController, container) { MapEditScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSchedule>(navController, container) { ScheduleScreen(viewModel = it, onBack = back) }

            // Settings and the pages it opens.
            vacuumComposable<VacuumSettings>(navController, container) {
                VacuumSettingsScreen(viewModel = it, onBack = back, onNavigate = open)
            }
            vacuumComposable<VacuumMaintenance>(navController, container) {
                VacuumMaintenanceScreen(viewModel = it, onBack = back, onOpen = { name -> open(VacuumMaintenanceDetail(name)) })
            }
            vacuumComposable<VacuumMaintenanceDetail>(navController, container) { viewModel ->
                VacuumMaintenanceDetailScreen(viewModel = viewModel, consumable = toRoute<VacuumMaintenanceDetail>().consumable, onBack = back)
            }
            vacuumComposable<VacuumSettingsDock>(navController, container) {
                VacuumDockSettingsScreen(viewModel = it, onBack = back)
            }
            vacuumComposable<VacuumSettingsValley>(navController, container) { VacuumChargingModeScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsFloor>(navController, container) { VacuumFloorSettingsScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsCarpet>(navController, container) { VacuumCarpetScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsObstacle>(navController, container) { VacuumObstacleSettingsScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsRobot>(navController, container) { VacuumMoreOptionsScreen(viewModel = it, onBack = back, onNavigate = open) }
            vacuumComposable<VacuumSettingsDnd>(navController, container) { VacuumDndScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsBeta>(navController, container) { VacuumBetaFeaturesScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumSettingsMatter>(navController, container) { VacuumMatterScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumFirmware>(navController, container) { FirmwareUpdateScreen(viewModel = it, onBack = back) }

            // Declared for the redesign; each opens today's closest page or a placeholder until its track replaces the body.
            vacuumComposable<VacuumSettingsMoreOptions>(navController, container) { VacuumMoreOptionsScreen(viewModel = it, onBack = back, onNavigate = open) }
            vacuumComposable<VacuumSettingsChargingMode>(navController, container) {
                VacuumChargingModeScreen(viewModel = it, onBack = back)
            }
            vacuumComposable<VacuumSettingsRobotVoice>(navController, container) {
                VacuumRobotVoiceScreen(viewModel = it, onBack = back)
            }
            vacuumComposable<VacuumSettingsRemoteViewing>(navController, container) {
                com.kodraliu.localrock.ui.vacuum.RemoteViewingSettingsScreen(viewModel = it, onBack = back)
            }
            vacuumComposable<VacuumSettingsProductInfo>(navController, container) {
                VacuumProductInfoScreen(viewModel = it, onBack = back)
            }
            vacuumComposable<VacuumManageMaps>(navController, container) { MapEditScreen(viewModel = it, onBack = back) }
            vacuumComposable<VacuumMapDetails>(navController, container) {
                ComingSoonScreen(title = MapStrings.MAP_DETAILS, onBack = back)
            }
        }
    }
}

/** A destination inside VacuumGraph; every one shares the graph entry's VacuumViewModel. */
private inline fun <reified T : Any> NavGraphBuilder.vacuumComposable(
    navController: NavHostController,
    container: AppContainer,
    noinline content: @Composable NavBackStackEntry.(VacuumViewModel) -> Unit,
) {
    composable<T> { entry ->
        val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
        val duid = graphEntry.toRoute<VacuumGraph>().duid
        val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
        entry.content(viewModel)
    }
}
