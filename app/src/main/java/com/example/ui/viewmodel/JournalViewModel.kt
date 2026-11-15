package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminEmailEntry
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskComment
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WorkflowStage
import com.example.data.model.WorkflowTemplate
import com.example.data.remote.unwrapFirestoreMessage
import com.example.data.repository.JournalRepository
import com.example.domain.IsoDateUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class DueDateFilter(val label: String) {
  ALL("All dates"),
  OVERDUE("Overdue"),
  DUE_3_DAYS("Due <= 3d"),
  THIS_WEEK("This week"),
}

data class TaskFilterState(
  val query: String = "",
  val targetId: String? = null,
  val assigneeUserId: String? = null,
  val teamId: String? = null,
  val status: TaskStatus? = null,
  val dueFilter: DueDateFilter = DueDateFilter.ALL,
)

data class TargetProgressSummary(
  val target: Target,
  val todoCount: Int,
  val inProgressCount: Int,
  val doneCount: Int,
  val overdueCount: Int,
) {
  val totalCount: Int get() = todoCount + inProgressCount + doneCount + overdueCount
  val completionPercent: Int
    get() = if (totalCount == 0) 0 else ((doneCount * 100f) / totalCount).toInt()
}

data class MemberWorkloadSummary(
  val user: UserProfile,
  val openCount: Int,
  val doneCount: Int,
  val overdueCount: Int,
) {
  val totalCount: Int get() = openCount + doneCount
}

data class NotificationItem(
  val task: EditorialTask,
  val reason: String,
  val isUrgent: Boolean,
)

class JournalViewModel(
  private val repository: JournalRepository,
  val currentUserId: String,
) : ViewModel() {

  private val _isBootstrapping = MutableStateFlow(true)
  val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val _isActionInFlight = MutableStateFlow(false)
  val isActionInFlight: StateFlow<Boolean> = _isActionInFlight.asStateFlow()

  private val _filterState = MutableStateFlow(TaskFilterState())
  val filterState: StateFlow<TaskFilterState> = _filterState.asStateFlow()

  val currentUserState: StateFlow<UiState<UserProfile?>> = repository.observeCurrentUser(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("currentUser", e, "Failed to load profile")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  @OptIn(ExperimentalCoroutinesApi::class)
  val adminEmailsState: StateFlow<UiState<List<AdminEmailEntry>>> = currentUserState
    .flatMapLatest { userUi ->
      val user = (userUi as? UiState.Success)?.data
      if (user?.isAdmin == true) {
        repository.observeAdminEmails()
          .map<List<AdminEmailEntry>, UiState<List<AdminEmailEntry>>> { UiState.Success(it) }
          .catch { e -> emit(handleStreamFailure("admin_emails", e, "Failed to load admin emails")) }
      } else {
        flowOf(UiState.Success(emptyList()))
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

  val usersState: StateFlow<UiState<List<UserProfile>>> = repository.observeUsers()
    .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("users", e, "Failed to load members")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val teamsState: StateFlow<UiState<List<Team>>> = repository.observeTeams()
    .map<List<Team>, UiState<List<Team>>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("teams", e, "Failed to load teams")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val targetsState: StateFlow<UiState<List<Target>>> = repository.observeTargets()
    .map<List<Target>, UiState<List<Target>>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("targets", e, "Failed to load targets")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val templatesState: StateFlow<UiState<List<WorkflowTemplate>>> = repository.observeTemplates()
    .map<List<WorkflowTemplate>, UiState<List<WorkflowTemplate>>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("templates", e, "Failed to load templates")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val tasksState: StateFlow<UiState<List<EditorialTask>>> = repository.observeTasks()
    .map<List<EditorialTask>, UiState<List<EditorialTask>>> { UiState.Success(it) }
    .catch { e -> emit(handleStreamFailure("tasks", e, "Failed to load tasks")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val notifications: StateFlow<List<NotificationItem>> = combine(
    tasksState,
    teamsState,
  ) { tasksUi, teamsUi ->
    val tasks = (tasksUi as? UiState.Success)?.data ?: return@combine emptyList()
    val teams = (teamsUi as? UiState.Success)?.data ?: emptyList()
    val myTeamIds = teams.filter { it.includesUser(currentUserId) }.map { it.id }.toSet()
    val todayIso = IsoDateUtils.todayIso()
    val nowSeconds = Timestamp.now().seconds

    tasks.filter { task ->
      task.statusEnum != TaskStatus.DONE && isTaskAssignedToUser(task, currentUserId, myTeamIds)
    }.mapNotNull { task ->
      val daysUntil = IsoDateUtils.daysBetween(todayIso, task.dueDate)
      val isRecentAssignment = task.createdAt?.let { (nowSeconds - it.seconds) <= 48L * 3600L } == true &&
        task.statusEnum == TaskStatus.TODO
      when {
        daysUntil < 0L -> NotificationItem(task, "Overdue by ${-daysUntil}d", isUrgent = true)
        daysUntil <= 3L -> NotificationItem(
          task,
          if (daysUntil == 0L) "Due today" else "Due in ${daysUntil}d",
          isUrgent = true,
        )
        isRecentAssignment -> NotificationItem(task, "New assignment", isUrgent = false)
        else -> null
      }
    }.sortedWith(compareByDescending<NotificationItem> { it.isUrgent }.thenBy { it.task.dueDate })
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  fun bootstrapCurrentUser(displayName: String, email: String?, photoUrl: String?) {
    viewModelScope.launch {
      _isBootstrapping.value = true
      val result = repository.ensureCurrentUserBootstrapped(displayName, email, photoUrl)
      if (result.isFailure) {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Could not initialize member profile.",
        )
      }
      _isBootstrapping.value = false
    }
  }

  fun updateFilter(transform: (TaskFilterState) -> TaskFilterState) {
    _filterState.value = transform(_filterState.value)
  }

  fun clearFilters() {
    _filterState.value = TaskFilterState()
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  private fun requireAdminAction(): Boolean {
    val isAdmin = (currentUserState.value as? UiState.Success)?.data?.isAdmin == true
    if (!isAdmin) {
      _feedbackMessage.value = "Only Admins can add or modify tasks, events, teams, or roles."
    }
    return isAdmin
  }

  fun cycleTaskStatus(task: EditorialTask) {
    val next = task.statusEnum.next()
    viewModelScope.launch {
      val result = repository.updateTaskStatus(task.id, next)
      if (result.isFailure) {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Unable to update task status.",
        )
      }
    }
  }

  fun saveTask(
    taskId: String?,
    title: String,
    description: String,
    targetId: String,
    stageName: String,
    dueDate: String,
    priority: TaskPriority,
    status: TaskStatus,
    assigneeType: AssigneeType,
    assigneeId: String,
    onSuccess: () -> Unit,
  ) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.saveTask(
        taskId = taskId,
        title = title,
        description = description,
        targetId = targetId,
        stageName = stageName,
        dueDate = dueDate,
        priority = priority,
        status = status,
        assigneeType = assigneeType,
        assigneeId = assigneeId,
      )
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = if (taskId == null) "Task created." else "Task updated."
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to save task.",
        )
      }
    }
  }

  fun deleteTask(taskId: String, onSuccess: () -> Unit = {}) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.deleteTask(taskId)
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = "Task deleted."
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to delete task.",
        )
      }
    }
  }

  fun saveTargetWithWorkflow(
    targetId: String?,
    name: String,
    description: String,
    deadline: String,
    stages: List<WorkflowStage>,
    createStageTasks: Boolean,
    defaultAssigneeType: AssigneeType,
    defaultAssigneeId: String,
    onSuccess: () -> Unit,
  ) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.saveTargetWithWorkflow(
        targetId = targetId,
        name = name,
        description = description,
        deadline = deadline,
        stages = stages,
        createStageTasks = createStageTasks,
        defaultAssigneeType = defaultAssigneeType,
        defaultAssigneeId = defaultAssigneeId,
      )
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = if (targetId == null) {
          "Publication target saved."
        } else {
          "Target workflow updated."
        }
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to save publication target.",
        )
      }
    }
  }

  fun deleteTarget(targetId: String) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      val result = repository.deleteTarget(targetId)
      if (result.isSuccess) {
        _feedbackMessage.value = "Target deleted."
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to delete target.",
        )
      }
    }
  }

  fun saveTemplate(
    templateId: String?,
    name: String,
    stages: List<WorkflowStage>,
    onSuccess: () -> Unit = {},
  ) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.saveTemplate(templateId, name, stages)
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = "Workflow template saved."
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to save template.",
        )
      }
    }
  }

  fun deleteTemplate(templateId: String) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      val result = repository.deleteTemplate(templateId)
      if (result.isSuccess) {
        _feedbackMessage.value = "Template removed."
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to delete template.",
        )
      }
    }
  }

  fun saveTeam(
    teamId: String?,
    name: String,
    description: String,
    leadId: String,
    memberIds: List<String>,
    onSuccess: () -> Unit,
  ) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.saveTeam(teamId, name, description, leadId, memberIds)
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = if (teamId == null) "Team created." else "Team updated."
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to save team.",
        )
      }
    }
  }

  fun deleteTeam(teamId: String) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      val result = repository.deleteTeam(teamId)
      if (result.isSuccess) {
        _feedbackMessage.value = "Team removed."
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to delete team.",
        )
      }
    }
  }

  fun addAdminEmail(email: String, allUsers: List<UserProfile>, onSuccess: () -> Unit = {}) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.addAdminEmail(email, allUsers.map { it.userId })
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = "${email.trim().lowercase()} authorized as Admin."
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to authorize admin email.",
        )
      }
    }
  }

  fun removeAdminEmail(email: String, allUsers: List<UserProfile>) {
    if (!requireAdminAction()) return
    viewModelScope.launch {
      _isActionInFlight.value = true
      val result = repository.removeAdminEmail(email, allUsers.map { it.userId })
      _isActionInFlight.value = false
      if (result.isSuccess) {
        _feedbackMessage.value = "${email.trim().lowercase()} removed from Admin allowlist."
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to remove admin email.",
        )
      }
    }
  }

  fun updateUserRole(targetUser: UserProfile, newRole: UserRole, allUsers: List<UserProfile>) {
    if (!requireAdminAction()) return
    if (newRole == UserRole.MEMBER) {
      val adminCount = allUsers.count { it.isAdmin }
      if (targetUser.isAdmin && adminCount <= 1) {
        _feedbackMessage.value = "At least one Admin must remain in the society."
        return
      }
    }
    viewModelScope.launch {
      val result = repository.updateUserRole(targetUser.userId, newRole)
      if (result.isSuccess) {
        _feedbackMessage.value = "${targetUser.displayName} is now ${newRole.label}."
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to update member role.",
        )
      }
    }
  }

  fun observeTaskComments(taskId: String): Flow<UiState<List<TaskComment>>> =
    repository.observeComments(taskId)
      .map<List<TaskComment>, UiState<List<TaskComment>>> { UiState.Success(it) }
      .catch { e -> emit(handleStreamFailure("comments:$taskId", e, "Failed to load comments")) }

  fun addComment(taskId: String, authorName: String, body: String, onSuccess: () -> Unit) {
    if (body.isBlank()) return
    viewModelScope.launch {
      val result = repository.addComment(taskId, authorName, body)
      if (result.isSuccess) {
        onSuccess()
      } else {
        _feedbackMessage.value = unwrapFirestoreMessage(
          result.exceptionOrNull()!!,
          "Failed to post comment.",
        )
      }
    }
  }

  private fun <T> handleStreamFailure(label: String, error: Throwable, fallback: String): UiState<T> {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Stream error on $label", rootError)
    return UiState.Error(rootError.localizedMessage ?: fallback)
  }

  companion object {
    private const val TAG = "JournalVM"
    private const val STOP_TIMEOUT_MS = 5000L

    fun isTaskOverdue(task: EditorialTask, todayIso: String = IsoDateUtils.todayIso()): Boolean {
      if (task.statusEnum == TaskStatus.DONE) return false
      return IsoDateUtils.isBefore(task.dueDate, todayIso)
    }

    fun isTaskAssignedToUser(
      task: EditorialTask,
      uid: String,
      userTeamIds: Set<String>,
    ): Boolean =
      (task.assigneeTypeEnum == AssigneeType.USER && task.assigneeId == uid) ||
        (task.assigneeTypeEnum == AssigneeType.TEAM && task.assigneeId in userTeamIds)

    fun filterTasks(
      tasks: List<EditorialTask>,
      filter: TaskFilterState,
      teams: List<Team>,
      todayIso: String = IsoDateUtils.todayIso(),
    ): List<EditorialTask> {
      val q = filter.query.trim().lowercase()
      return tasks.filter { task ->
        val matchesQuery = q.isEmpty() ||
          task.title.lowercase().contains(q) ||
          task.description.lowercase().contains(q) ||
          task.stageName.lowercase().contains(q)
        val matchesTarget = filter.targetId == null || task.targetId == filter.targetId
        val matchesStatus = filter.status == null || task.statusEnum == filter.status
        val matchesAssignee = filter.assigneeUserId == null || run {
          val userTeams = teams.filter { it.includesUser(filter.assigneeUserId) }.map { it.id }.toSet()
          isTaskAssignedToUser(task, filter.assigneeUserId, userTeams)
        }
        val matchesTeam = filter.teamId == null ||
          (task.assigneeTypeEnum == AssigneeType.TEAM && task.assigneeId == filter.teamId)
        val daysUntil = IsoDateUtils.daysBetween(todayIso, task.dueDate)
        val matchesDue = when (filter.dueFilter) {
          DueDateFilter.ALL -> true
          DueDateFilter.OVERDUE -> task.statusEnum != TaskStatus.DONE && daysUntil < 0L
          DueDateFilter.DUE_3_DAYS -> task.statusEnum != TaskStatus.DONE && daysUntil in 0L..3L
          DueDateFilter.THIS_WEEK -> task.statusEnum != TaskStatus.DONE && daysUntil in 0L..7L
        }
        matchesQuery && matchesTarget && matchesStatus && matchesAssignee && matchesTeam && matchesDue
      }
    }

    fun buildTargetProgress(
      targets: List<Target>,
      tasks: List<EditorialTask>,
      todayIso: String = IsoDateUtils.todayIso(),
    ): List<TargetProgressSummary> = targets.map { target ->
      val targetTasks = tasks.filter { it.targetId == target.id }
      var todo = 0
      var inProgress = 0
      var done = 0
      var overdue = 0
      targetTasks.forEach { task ->
        when {
          task.statusEnum == TaskStatus.DONE -> done++
          isTaskOverdue(task, todayIso) -> overdue++
          task.statusEnum == TaskStatus.IN_PROGRESS -> inProgress++
          else -> todo++
        }
      }
      TargetProgressSummary(target, todo, inProgress, done, overdue)
    }

    fun buildMemberWorkload(
      users: List<UserProfile>,
      teams: List<Team>,
      tasks: List<EditorialTask>,
      todayIso: String = IsoDateUtils.todayIso(),
    ): List<MemberWorkloadSummary> = users.map { user ->
      val userTeamIds = teams.filter { it.includesUser(user.userId) }.map { it.id }.toSet()
      val userTasks = tasks.filter { isTaskAssignedToUser(it, user.userId, userTeamIds) }
      val done = userTasks.count { it.statusEnum == TaskStatus.DONE }
      val overdue = userTasks.count { isTaskOverdue(it, todayIso) }
      val open = userTasks.count { it.statusEnum != TaskStatus.DONE }
      MemberWorkloadSummary(user, open, done, overdue)
    }
  }
}
