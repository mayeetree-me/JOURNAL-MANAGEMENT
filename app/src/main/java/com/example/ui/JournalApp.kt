package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.Team
import com.example.data.repository.JournalRepository
import com.example.ui.auth.signOut
import com.example.ui.components.LoadingStateView
import com.example.ui.components.NotificationsSheet
import com.example.ui.components.TaskDetailSheet
import com.example.ui.components.TaskEditorDialog
import com.example.ui.components.TeamEditorDialog
import com.example.ui.components.WorkflowGeneratorSheet
import com.example.ui.screens.AdminTab
import com.example.ui.screens.HomeTab
import com.example.ui.screens.MyWorkTab
import com.example.ui.screens.TargetsTab
import com.example.ui.screens.TeamsTab
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

enum class JournalTab(
  val titleRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
  MY_WORK(R.string.nav_my_work, Icons.Filled.AssignmentInd, Icons.Outlined.AssignmentInd),
  TARGETS(R.string.nav_targets, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
  TEAMS(R.string.nav_teams, Icons.Filled.Groups, Icons.Outlined.Groups),
  ADMIN(R.string.nav_admin, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalMainScreen(
  firebaseUser: FirebaseUser,
  themeMode: ThemeMode,
  onCycleTheme: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: JournalViewModel = viewModel(
    key = firebaseUser.uid,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
        val dbId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(dbId)
        JournalViewModel(JournalRepository(db), firebaseUser.uid)
      }
    },
  )

  LaunchedEffect(firebaseUser.uid) {
    viewModel.bootstrapCurrentUser(
      displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Journal Member",
      email = firebaseUser.email,
      photoUrl = firebaseUser.photoUrl?.toString(),
    )
  }

  val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()
  val currentUserState by viewModel.currentUserState.collectAsStateWithLifecycle()
  val adminEmailsState by viewModel.adminEmailsState.collectAsStateWithLifecycle()
  val usersState by viewModel.usersState.collectAsStateWithLifecycle()
  val teamsState by viewModel.teamsState.collectAsStateWithLifecycle()
  val targetsState by viewModel.targetsState.collectAsStateWithLifecycle()
  val templatesState by viewModel.templatesState.collectAsStateWithLifecycle()
  val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
  val notifications by viewModel.notifications.collectAsStateWithLifecycle()
  val filterState by viewModel.filterState.collectAsStateWithLifecycle()
  val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
  val isActionInFlight by viewModel.isActionInFlight.collectAsStateWithLifecycle()

  val currentUser = (currentUserState as? UiState.Success)?.data
  val isAdmin = currentUser?.isAdmin == true
  val adminEmails = (adminEmailsState as? UiState.Success)?.data.orEmpty()
  val users = (usersState as? UiState.Success)?.data.orEmpty()
  val teams = (teamsState as? UiState.Success)?.data.orEmpty()
  val targets = (targetsState as? UiState.Success)?.data.orEmpty()
  val templates = (templatesState as? UiState.Success)?.data.orEmpty()
  val tasks = (tasksState as? UiState.Success)?.data.orEmpty()

  var currentTab by remember { mutableStateOf(JournalTab.HOME) }
  LaunchedEffect(isAdmin) {
    if (!isAdmin && currentTab == JournalTab.ADMIN) {
      currentTab = JournalTab.HOME
    }
  }
  BackHandler(enabled = currentTab != JournalTab.HOME) {
    currentTab = JournalTab.HOME
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(feedbackMessage) {
    feedbackMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearFeedback()
    }
  }

  var showAccountMenu by remember { mutableStateOf(false) }
  var showNotificationsSheet by remember { mutableStateOf(false) }
  var selectedTaskForDetail by remember { mutableStateOf<EditorialTask?>(null) }
  var showTaskEditor by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<EditorialTask?>(null) }
  var preselectedTargetForTask by remember { mutableStateOf<Target?>(null) }
  var showWorkflowGenerator by remember { mutableStateOf(false) }
  var editingTarget by remember { mutableStateOf<Target?>(null) }
  var showTeamEditor by remember { mutableStateOf(false) }
  var editingTeam by remember { mutableStateOf<Team?>(null) }

  val visibleTabs = remember(isAdmin) {
    if (isAdmin) JournalTab.entries else JournalTab.entries.filter { it != JournalTab.ADMIN }
  }

  val targetNames = remember(targets) { targets.associate { it.id to it.name } }
  val userNames = remember(users) { users.associate { it.userId to it.displayName } }
  val teamNames = remember(teams) { teams.associate { it.id to it.name } }
  val myTeamIds = remember(teams, firebaseUser.uid) {
    teams.filter { it.includesUser(firebaseUser.uid) }.map { it.id }.toSet()
  }

  // Keep detail sheet synced with live task updates
  val activeDetailTask = selectedTaskForDetail?.let { current ->
    tasks.firstOrNull { it.id == current.id } ?: current
  }

  if (showNotificationsSheet) {
    NotificationsSheet(
      notifications = notifications,
      targetNames = targetNames,
      onTaskClick = { selectedTaskForDetail = it },
      onDismiss = { showNotificationsSheet = false },
    )
  }

  if (activeDetailTask != null) {
    val assigneeLabel = if (activeDetailTask.assigneeType == "team") {
      teamNames[activeDetailTask.assigneeId] ?: "Team"
    } else {
      userNames[activeDetailTask.assigneeId] ?: "Member"
    }
    val canMutate = isAdmin || JournalViewModel.isTaskAssignedToUser(
      activeDetailTask,
      firebaseUser.uid,
      myTeamIds,
    )
    TaskDetailSheet(
      task = activeDetailTask,
      targetName = targetNames[activeDetailTask.targetId] ?: "Target",
      assigneeLabel = assigneeLabel,
      creatorName = userNames[activeDetailTask.createdBy] ?: "Admin",
      isAdmin = isAdmin,
      canMutateTask = canMutate,
      currentUserName = currentUser?.displayName ?: firebaseUser.displayName ?: "Member",
      viewModel = viewModel,
      onEditClick = {
        editingTask = activeDetailTask
        selectedTaskForDetail = null
        showTaskEditor = true
      },
      onDeleteTask = {
        val id = activeDetailTask.id
        selectedTaskForDetail = null
        viewModel.deleteTask(id)
      },
      onDismiss = { selectedTaskForDetail = null },
    )
  }

  if (isAdmin && showTaskEditor) {
    TaskEditorDialog(
      existingTask = editingTask,
      defaultTargetId = preselectedTargetForTask?.id,
      targets = targets,
      users = users,
      teams = teams,
      currentUserId = firebaseUser.uid,
      isSaving = isActionInFlight,
      onSave = { taskId, title, desc, targetId, stageName, dueDate, priority, status, assigneeType, assigneeId ->
        viewModel.saveTask(
          taskId = taskId,
          title = title,
          description = desc,
          targetId = targetId.ifBlank { preselectedTargetForTask?.id.orEmpty() },
          stageName = stageName,
          dueDate = dueDate,
          priority = priority,
          status = status,
          assigneeType = assigneeType,
          assigneeId = assigneeId,
          onSuccess = {
            showTaskEditor = false
            editingTask = null
            preselectedTargetForTask = null
          },
        )
      },
      onDismiss = {
        showTaskEditor = false
        editingTask = null
        preselectedTargetForTask = null
      },
    )
  }

  if (isAdmin && showWorkflowGenerator) {
    WorkflowGeneratorSheet(
      existingTarget = editingTarget,
      allTargets = targets,
      templates = templates,
      users = users,
      teams = teams,
      currentUserId = firebaseUser.uid,
      isSaving = isActionInFlight,
      onSaveTarget = { targetId, name, desc, deadline, stages, createTasks, assigneeType, assigneeId ->
        viewModel.saveTargetWithWorkflow(
          targetId = targetId,
          name = name,
          description = desc,
          deadline = deadline,
          stages = stages,
          createStageTasks = createTasks,
          defaultAssigneeType = assigneeType,
          defaultAssigneeId = assigneeId,
          onSuccess = {
            showWorkflowGenerator = false
            editingTarget = null
          },
        )
      },
      onSaveTemplate = { tplName, stages ->
        viewModel.saveTemplate(templateId = null, name = tplName, stages = stages)
      },
      onDismiss = {
        showWorkflowGenerator = false
        editingTarget = null
      },
    )
  }

  if (isAdmin && showTeamEditor) {
    TeamEditorDialog(
      existingTeam = editingTeam,
      users = users,
      currentUserId = firebaseUser.uid,
      isSaving = isActionInFlight,
      onSave = { teamId, name, desc, leadId, memberIds ->
        viewModel.saveTeam(
          teamId = teamId,
          name = name,
          description = desc,
          leadId = leadId,
          memberIds = memberIds,
          onSuccess = {
            showTeamEditor = false
            editingTeam = null
          },
        )
      },
      onDismiss = {
        showTeamEditor = false
        editingTeam = null
      },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          actions = {
            // Contextual Notifications: only shown if any task matches the user
            if (notifications.isNotEmpty()) {
              IconButton(
                onClick = { showNotificationsSheet = true },
                modifier = Modifier.testTag("notifications_button"),
              ) {
                BadgedBox(
                  badge = {
                    Badge { Text(notifications.size.toString()) }
                  },
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications (${notifications.size})",
                  )
                }
              }
            }

            IconButton(
              onClick = onCycleTheme,
              modifier = Modifier.testTag("theme_toggle_button"),
            ) {
              Icon(
                imageVector = if (themeMode == ThemeMode.DARK) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "Switch theme (current: ${themeMode.label})",
              )
            }

            Box {
              IconButton(onClick = { showAccountMenu = true }) {
                val photo = currentUser?.photoUrl ?: firebaseUser.photoUrl?.toString()
                if (!photo.isNullOrBlank()) {
                  AsyncImage(
                    model = photo,
                    contentDescription = "Account menu",
                    modifier = Modifier
                      .size(30.dp)
                      .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                  )
                } else {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Person,
                      contentDescription = "Account menu",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(5.dp),
                    )
                  }
                }
              }
              DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false },
              ) {
                DropdownMenuItem(
                  text = {
                    val accountEmail = firebaseUser.email
                    val label = if (!accountEmail.isNullOrBlank()) {
                      "${currentUser?.displayName ?: "Member"} (${currentUser?.roleEnum?.label ?: "Member"})\n$accountEmail"
                    } else {
                      "${currentUser?.displayName ?: "Member"} (${currentUser?.roleEnum?.label ?: "Member"})"
                    }
                    Text(label)
                  },
                  onClick = { showAccountMenu = false },
                )
                DropdownMenuItem(
                  text = { Text("Theme: ${themeMode.label}") },
                  onClick = {
                    onCycleTheme()
                    showAccountMenu = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Logout, contentDescription = null)
                  },
                  text = { Text("Sign out") },
                  onClick = {
                    showAccountMenu = false
                    signOut(credentialManager, coroutineScope)
                  },
                )
              }
            }
          },
        )
      },
      floatingActionButton = {
        if (isAdmin && targets.isNotEmpty() && (currentTab == JournalTab.HOME || currentTab == JournalTab.MY_WORK)) {
          FloatingActionButton(
            onClick = {
              editingTask = null
              preselectedTargetForTask = null
              showTaskEditor = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("fab_new_task"),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = "Create task")
          }
        }
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
          ) {
            visibleTabs.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.titleRes),
                  )
                },
                label = { Text(stringResource(tab.titleRes)) },
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            Spacer(modifier = Modifier.weight(0.1f))
            visibleTabs.forEach { tab ->
              val selected = currentTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.titleRes),
                  )
                },
                label = { Text(stringResource(tab.titleRes)) },
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
              )
            }
            Spacer(modifier = Modifier.weight(1f))
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(
            modifier = Modifier
              .widthIn(max = 840.dp)
              .fillMaxSize(),
          ) {
            val isLoadingInitial = isBootstrapping ||
              (currentUserState is UiState.Loading && tasksState is UiState.Loading)

            if (isLoadingInitial) {
              LoadingStateView("Loading Journal Workflow…")
            } else {
              when (currentTab) {
                JournalTab.HOME -> HomeTab(
                  currentUser = currentUser,
                  targets = targets,
                  tasks = tasks,
                  teams = teams,
                  users = users,
                  filterState = filterState,
                  onUpdateFilter = viewModel::updateFilter,
                  onClearFilters = viewModel::clearFilters,
                  onToggleTaskStatus = viewModel::cycleTaskStatus,
                  onTaskClick = { selectedTaskForDetail = it },
                )
                JournalTab.MY_WORK -> MyWorkTab(
                  currentUserId = firebaseUser.uid,
                  targets = targets,
                  tasks = tasks,
                  teams = teams,
                  users = users,
                  filterState = filterState,
                  onUpdateFilter = viewModel::updateFilter,
                  onClearFilters = viewModel::clearFilters,
                  onToggleTaskStatus = viewModel::cycleTaskStatus,
                  onTaskClick = { selectedTaskForDetail = it },
                )
                JournalTab.TARGETS -> TargetsTab(
                  isAdmin = isAdmin,
                  targets = targets,
                  tasks = tasks,
                  onNewTargetClick = {
                    editingTarget = null
                    showWorkflowGenerator = true
                  },
                  onEditTargetWorkflow = { target ->
                    editingTarget = target
                    showWorkflowGenerator = true
                  },
                  onDeleteTarget = { target -> viewModel.deleteTarget(target.id) },
                  onAddTaskForTarget = { target ->
                    editingTask = null
                    preselectedTargetForTask = target
                    showTaskEditor = true
                  },
                )
                JournalTab.TEAMS -> TeamsTab(
                  currentUserId = firebaseUser.uid,
                  isAdmin = isAdmin,
                  teams = teams,
                  users = users,
                  tasks = tasks,
                  onCreateTeam = {
                    editingTeam = null
                    showTeamEditor = true
                  },
                  onEditTeam = { team ->
                    editingTeam = team
                    showTeamEditor = true
                  },
                  onDeleteTeam = { team -> viewModel.deleteTeam(team.id) },
                )
                JournalTab.ADMIN -> AdminTab(
                  currentUserId = firebaseUser.uid,
                  targets = targets,
                  tasks = tasks,
                  teams = teams,
                  users = users,
                  adminEmails = adminEmails,
                  templates = templates,
                  isActionInFlight = isActionInFlight,
                  onNewTaskClick = {
                    editingTask = null
                    preselectedTargetForTask = null
                    showTaskEditor = true
                  },
                  onAddAdminEmail = { emailInput, onClear ->
                    viewModel.addAdminEmail(emailInput, users, onSuccess = onClear)
                  },
                  onRemoveAdminEmail = { email -> viewModel.removeAdminEmail(email, users) },
                  onRoleChange = { user, role -> viewModel.updateUserRole(user, role, users) },
                  onDeleteTemplate = { tpl -> viewModel.deleteTemplate(tpl.id) },
                  onToggleTaskStatus = viewModel::cycleTaskStatus,
                  onTaskClick = { selectedTaskForDetail = it },
                )
              }
            }
          }
        }
      }
    }
  }
}
