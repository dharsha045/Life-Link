package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TimerEntity
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemVersion = Build.VERSION.SDK_INT

    // Navigation state variables for the Immersive bottom navigation: "Active", "Logs", "Troops", "Config"
    var selectedTab by remember { mutableStateOf("Active") }

    // Notification states & runtime requests
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (systemVersion >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled! App notifications scheduled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications disabled. Timers will not alert in background.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission dialog on mount for Android 13+
    LaunchedEffect(Unit) {
        if (systemVersion >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Timer States & Search Flows
    val allTimersList by viewModel.filteredTimers.collectAsState()
    
    // Split screen data: Active timers or Log history timers
    val activeTimers = allTimersList.filter { it.getRemainingSeconds() > 0 }
    val completedTimers = allTimersList.filter { it.getRemainingSeconds() <= 0 }

    val searchText by viewModel.searchText.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var timerToEdit by remember { mutableStateOf<TimerEntity?>(null) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    // Preset selection helper
    var presetNameToLoad by remember { mutableStateOf("") }
    var presetCategoryToLoad by remember { mutableStateOf("Building") }
    var presetTimeInSeconds by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        // Custom medieval gold emblem drawing icon matching Immersive UI CSS mockup
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ClashLightGold, ClashGold)
                                    )
                                )
                                .shadow(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "👑", 
                                fontSize = 18.sp, 
                                modifier = Modifier.offset(y = (-1).dp)
                            )
                        }
                        Column {
                            Text(
                                "CLASH TIMER",
                                color = ClashGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 0.5.sp,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "COMMAND CENTER",
                                color = ClashGrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ClashDarkBg,
                    titleContentColor = ClashGold
                ),
                actions = {
                    if (selectedTab == "Active") {
                        Box {
                            IconButton(onClick = { sortDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Sort Options",
                                    tint = ClashGold
                                )
                            }
                            DropdownMenu(
                                expanded = sortDropdownExpanded,
                                onDismissRequest = { sortDropdownExpanded = false },
                                modifier = Modifier.background(ClashCardBg)
                            ) {
                                SortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                order.displayName,
                                                color = if (sortOrder == order) ClashLightGold else Color.White,
                                                fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            sortDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Elegant navigation bar completely matching Immersive UI specs (Active, Logs, Troops, Config)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = ClashCardBg.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        TabItem("Active", Icons.Default.PlayArrow, Icons.Default.PlayArrow),
                        TabItem("Logs", Icons.Default.Done, Icons.Default.Done),
                        TabItem("Barracks", Icons.Default.Star, Icons.Default.Star),
                        TabItem("Mobile Builder", Icons.Default.Settings, Icons.Default.Settings)
                    )

                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab.name
                        val tint = if (isSelected) ClashGold else ClashGrayText
                        val iconScale = if (isSelected) 1.1f else 1.0f

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = tab.name }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.name,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = tab.name.uppercase(Locale.ROOT),
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == "Active") {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = ClashGold,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .testTag("floating_add_button")
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Upgrade Timer",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        containerColor = ClashDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                "Active" -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // Search info header
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { viewModel.setSearchText(it) },
                            placeholder = { Text("Search upgrades...", color = ClashGrayText) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = ClashGold
                                )
                            },
                            maxLines = 1,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_field_input")
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ClashGold,
                                unfocusedBorderColor = ClashCardBg,
                                focusedContainerColor = ClashCardBg,
                                unfocusedContainerColor = ClashCardBg
                            )
                        )

                        // Category Selection list row
                        val categories = listOf("All", "Building", "Troops", "Spells", "Clan War")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { category ->
                                val isSelected = selectedCategory == category
                                val containerColor = if (isSelected) ClashGold else ClashCardBg
                                val contentColor = if (isSelected) Color.Black else Color.White
                                val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0x33FFFFFF))
                                val icon = when (category) {
                                    "Building" -> "🔨"
                                    "Troops" -> "⚔️"
                                    "Spells" -> "🧪"
                                    "Clan War" -> "🏆"
                                    else -> "🌍"
                                }

                                Card(
                                    onClick = { viewModel.setSelectedCategory(category) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = containerColor),
                                    border = borderStroke,
                                    modifier = Modifier
                                        .testTag("category_pill_${category.lowercase().replace(" ", "_")}")
                                        .height(34.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(icon, fontSize = 12.sp)
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (activeTimers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(40.dp))
                                            .background(ClashCardBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔨", fontSize = 36.sp)
                                    }
                                    Text(
                                        text = "No Active Upgrades",
                                        style = MaterialTheme.typography.titleLarge.copy(color = ClashGold)
                                    )
                                    Text(
                                        text = "All builders are sleeping! Tap the '+' button or head to the Barracks Presets to schedule upgrades.",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = ClashGrayText)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(activeTimers, key = { it.id }) { timer ->
                                    TimerItemCard(
                                        timer = timer,
                                        onTogglePause = { viewModel.togglePause(timer) },
                                        onReset = { viewModel.resetTimer(timer) },
                                        onDelete = { viewModel.deleteTimer(timer) },
                                        onEdit = { timerToEdit = timer }
                                    )
                                }
                            }
                        }
                    }
                }

                "Logs" -> {
                    // Completed logs list
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "COMPLETED UPGRADES LOGS",
                            color = ClashGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "History logs of completed village builds.",
                            color = ClashGrayText,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (completedTimers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("📜", fontSize = 48.sp)
                                    Text(
                                        "No completed timers yet",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Once some upgrades complete, they'll gather in this archives log section.",
                                        color = ClashGrayText,
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(completedTimers, key = { it.id }) { timer ->
                                    TimerItemCard(
                                        timer = timer,
                                        onTogglePause = { viewModel.togglePause(timer) },
                                        onReset = { viewModel.resetTimer(timer) },
                                        onDelete = { viewModel.deleteTimer(timer) },
                                        onEdit = { timerToEdit = timer }
                                    )
                                }
                            }
                        }
                    }
                }

                "Barracks" -> {
                    // Predefined Clash barracks with custom timers which launches instantly or pre-populates
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "BARRACKS PRESET TIMERS",
                            color = ClashGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Tap any predefined upgrade unit to launch its timer instantly.",
                            color = ClashGrayText,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val presets = listOf(
                            BarrackPreset("Barbarian Level 10", "Troops", 120L, "⚔️"),
                            BarrackPreset("P.E.K.K.A Level 8", "Troops", 14400L, "⚔️"),
                            BarrackPreset("Super Dragon Lv 4", "Troops", 86400L, "⚔️"),
                            BarrackPreset("Hidden Tesla Level 9", "Building", 172800L, "🔨"),
                            BarrackPreset("Archer Tower Level 12", "Building", 259200L, "🔨"),
                            BarrackPreset("Town Hall Town Upgrade", "Building", 604800L, "🔨"),
                            BarrackPreset("Rage Spell Level 6", "Spells", 18000L, "🧪"),
                            BarrackPreset("Freeze Spell Level 7", "Spells", 43200L, "🧪"),
                            BarrackPreset("Healing Spell Level 5", "Spells", 7200L, "🧪"),
                            BarrackPreset("Clan War Preparation", "Clan War", 82800L, "🏆"),
                            BarrackPreset("Clan War Battle State", "Clan War", 86400L, "🏆")
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(presets) { preset ->
                                Card(
                                    onClick = {
                                        presetNameToLoad = preset.name
                                        presetCategoryToLoad = preset.category
                                        presetTimeInSeconds = preset.duration
                                    },
                                    colors = CardDefaults.cardColors(containerColor = ClashCardBg),
                                    border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F1522)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(preset.icon, fontSize = 16.sp)
                                            }
                                            Column {
                                                Text(
                                                    preset.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    preset.category,
                                                    color = ClashGrayText,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                formatSecondsToTime(preset.duration),
                                                fontFamily = FontFamily.Monospace,
                                                color = ClashGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Quick Add",
                                                tint = ClashGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Mobile Builder" -> {
                    // Mobile user easy APK instructions build helper (Fully localized offline offline)
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                            .fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "MOBILE BUILD COMPILER INSTRUCTIONS",
                            color = ClashGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Get your APK up and running easily directly from your phone.",
                            color = ClashGrayText,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ClashCardBg),
                            border = BorderStroke(1.dp, ClashGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "✨ Easy Method: AI Studio Sidebar APK compiler (Easiest)",
                                    color = ClashGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "1. Tap on the Settings menu of the web interface in the upper-right corner of AI Studio.\n" +
                                    "2. Click on \"Generate APK\" or \"Export ZIP project\".\n" +
                                    "3. If you download the ZIP, you can put it into an online Android Builder web service (e.g. CodeSandbox, Replit, or native compilation portals) to compile it for your phone in 1 click.\n" +
                                    "4. Run the generated installation app directly on your Android phone and click open!",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ClashCardBg),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "🎯 Mobile Phone Native Method (AIDE / Termux)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "1. Install Termux app on your mobile phone from F-Droid.\n" +
                                    "2. Type package setup commands inside Termux terminal:\n" +
                                    "   pkg install openjdk-17\n" +
                                    "3. Put the exported files folder inside repository path.\n" +
                                    "4. Run command inside project folder to compile APK:\n" +
                                    "   ./gradlew assembleDebug\n" +
                                    "5. Locate complete installation APK from app build outputs at target directory and tap to install instantly!",
                                    color = ClashGrayText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ClashCardBg),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "🔇 Troubleshooting Background Sounds",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "If alerts do not play gaming sound effects instantly when completed: make sure to approve the post notifications popup dialog, whitelist Clash Timer from battery saving optimizations, and enable background notifications permissions on your system settings configurations drawer.",
                                    color = ClashGrayText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Modal adding timer dialog
    if (showAddDialog) {
        AddEditTimerDialog(
            titleLabel = "Create Clash Upgrade",
            onDismiss = { showAddDialog = false },
            onSave = { name, category, days, hours, mins, secs ->
                viewModel.addTimer(name, category, days, hours, mins, secs)
                showAddDialog = false
            }
        )
    }

    // Modal presets quick setup initiator
    if (presetNameToLoad.isNotBlank()) {
        val totalSeconds = presetTimeInSeconds
        val d = (totalSeconds / (24 * 3600)).toInt()
        val h = ((totalSeconds % (24 * 3600)) / 3600).toInt()
        val m = ((totalSeconds % 3600) / 60).toInt()
        val s = (totalSeconds % 60).toInt()

        AddEditTimerDialog(
            titleLabel = "Launch Preset Upgrade",
            initialName = presetNameToLoad,
            initialCategory = presetCategoryToLoad,
            initialDays = d,
            initialHours = h,
            initialMins = m,
            initialSecs = s,
            onDismiss = { presetNameToLoad = "" },
            onSave = { name, category, days, hours, mins, secs ->
                viewModel.addTimer(name, category, days, hours, mins, secs)
                presetNameToLoad = ""
            }
        )
    }

    if (timerToEdit != null) {
        val currentTimer = timerToEdit!!
        val remaining = currentTimer.getRemainingSeconds()
        val days = (remaining / (24 * 3600)).toInt()
        val hours = ((remaining % (24 * 3600)) / 3600).toInt()
        val mins = ((remaining % 3600) / 60).toInt()
        val secs = (remaining % 60).toInt()

        AddEditTimerDialog(
            titleLabel = "Modify Clash Upgrade",
            initialName = currentTimer.name,
            initialCategory = currentTimer.category,
            initialDays = days,
            initialHours = hours,
            initialMins = mins,
            initialSecs = secs,
            editMode = true,
            onDismiss = { timerToEdit = null },
            onSave = { name, category, d, h, m, s ->
                val newDuration = (d * 24 * 3600L) + (h * 3600L) + (m * 60L) + s
                viewModel.updateTimer(currentTimer, name, category, newDuration)
                timerToEdit = null
            }
        )
    }
}

@Composable
fun TimerItemCard(
    timer: TimerEntity,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val remaining = timer.getRemainingSeconds()
    val isCompleted = remaining <= 0
    val progress = timer.getProgress()

    // Interactive themed colors based on category matching exact Immersive UI CSS colors
    val categoryColor = when (timer.category) {
        "Building" -> ClashElixir
        "Troops" -> ClashDarkElixir
        "Spells" -> ClashBlueSpells
        "Clan War" -> ClashGold
        else -> ClashLightGold
    }

    val categoryIcon = when (timer.category) {
        "Building" -> "🔨"
        "Troops" -> "⚔️"
        "Spells" -> "🧪"
        "Clan War" -> "🏆"
        else -> "🏰"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timer_card_item_${timer.id}")
            .clickable { onEdit() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ClashCardBg),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCompleted) ClashGreen else Color(0x1FFFFFFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .shadow(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryIcon, fontSize = 20.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timer.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timer.category,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = ClashGrayText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Completion Badge
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClashGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "FINISHED",
                            color = ClashGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else if (timer.isPaused) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PASSED",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClashGold.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ACTIVE",
                            color = ClashGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Running clock text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Large styled Countdown Timer Text
                Text(
                    text = formatSecondsToTime(remaining),
                    color = if (isCompleted) ClashGreen else Color.White,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("countdown_label_${timer.id}")
                )

                // Percentage text
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isCompleted) ClashGreen else categoryColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                )
            }

            // High aesthetic glowing progress bar matching CSS exact instructions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0B0E14))
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    categoryColor.copy(alpha = 0.6f),
                                    categoryColor
                                )
                            )
                        )
                )
            }

            // Bottom Action control utilities
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${formatSecondsToTime(timer.durationSeconds)}",
                    fontSize = 11.sp,
                    color = ClashGrayText
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Restart Button
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1522))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Timer",
                            tint = ClashGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play/Pause Button
                    if (!isCompleted) {
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F1522))
                        ) {
                            Icon(
                                imageVector = if (timer.isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                                contentDescription = if (timer.isPaused) "Resume" else "Pause",
                                tint = if (timer.isPaused) ClashGreen else ClashLightGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Delete Trash Icon Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1522))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete timer",
                            tint = ClashElixir,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab and Preset structs helper declarations
 */
data class TabItem(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

data class BarrackPreset(
    val name: String,
    val category: String,
    val duration: Long,
    val icon: String
)

/**
 * Custom gold-themed medieval Modal Dialog for inputting Timer Specs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTimerDialog(
    titleLabel: String,
    initialName: String = "",
    initialCategory: String = "Building",
    initialDays: Int = 0,
    initialHours: Int = 0,
    initialMins: Int = 0,
    initialSecs: Int = 0,
    editMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, days: Int, hours: Int, minutes: Int, seconds: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCat by remember { mutableStateOf(initialCategory) }

    var d by remember { mutableIntStateOf(initialDays) }
    var h by remember { mutableIntStateOf(initialHours) }
    var m by remember { mutableIntStateOf(initialMins) }
    var s by remember { mutableIntStateOf(initialSecs) }

    val categories = listOf("Building", "Troops", "Spells", "Clan War")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ClashCardBg),
            border = BorderStroke(2.dp, ClashGold)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = titleLabel.uppercase(Locale.ROOT),
                    color = ClashGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = ClashGold.copy(alpha = 0.3f), thickness = 1.dp)

                // Input Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Upgrade Name (e.g., Tesla Lv 4)", color = ClashGrayText) },
                    placeholder = { Text("Clash Upgrade", color = ClashGrayText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ClashGold,
                        unfocusedBorderColor = ClashDarkBg,
                        focusedContainerColor = ClashDarkBg,
                        unfocusedContainerColor = ClashDarkBg
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category selection chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Upgrades Category", color = ClashGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCat == category
                            val icon = when (category) {
                                "Building" -> "🔨"
                                "Troops" -> "⚔️"
                                "Spells" -> "🧪"
                                "Clan War" -> "🏆"
                                else -> "🏡"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ClashGold else ClashDarkBg)
                                    .clickable { selectedCat = category }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(icon, fontSize = 16.sp)
                                    Text(
                                        text = category.split(" ")[0],
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Scroll selection sliders for time intervals
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Upgrade Timer Duration", color = ClashGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    TimeSliderRow(label = "Days", value = d, maxValue = 30) { d = it }
                    TimeSliderRow(label = "Hours", value = h, maxValue = 23) { h = it }
                    TimeSliderRow(label = "Minutes", value = m, maxValue = 59) { m = it }
                    TimeSliderRow(label = "Seconds", value = s, maxValue = 59) { s = it }
                }

                // Actions Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ClashGrayText),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }

                    // Save / Start glowing button
                    val isActionEnabled = d > 0 || h > 0 || m > 0 || s > 0
                    Button(
                        onClick = { onSave(name, selectedCat, d, h, m, s) },
                        enabled = isActionEnabled,
                        modifier = Modifier
                            .weight(1.5f)
                            .shadow(if (isActionEnabled) 6.dp else 0.dp, shape = RoundedCornerShape(12.dp))
                            .testTag("dialog_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClashGold,
                            disabledContainerColor = ClashDarkBg,
                            contentColor = Color.Black,
                            disabledContentColor = ClashGrayText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (editMode) "UPDATE TIMER" else "START UPGRADE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeSliderRow(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: $value",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..maxValue.toFloat(),
            steps = if (maxValue > 23) maxValue - 1 else maxValue - 1,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = ClashGold,
                activeTrackColor = ClashGold,
                inactiveTrackColor = ClashDarkBg
            )
        )
    }
}

/**
 * Standard visual formatting utility conversion helper
 */
fun formatSecondsToTime(seconds: Long): String {
    if (seconds <= 0) return "00:00:00"
    
    val d = seconds / (24 * 3600)
    val h = (seconds % (24 * 3600)) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    
    return if (d > 0) {
        String.format(Locale.getDefault(), "%dd %02dh %02dm %02ds", d, h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }
}
