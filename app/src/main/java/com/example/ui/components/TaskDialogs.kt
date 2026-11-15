package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskComment
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.domain.IsoDateUtils
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
  task: EditorialTask,
  targetName: String,
  assigneeLabel: String,
  creatorName: String,
  isAdmin: Boolean,
  canMutateTask: Boolean,
  currentUserName: String,
  viewModel: JournalViewModel,
  onEditClick: () -> Unit,
  onDeleteTask: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val commentsFlow = remember(task.id) { viewModel.observeTaskComments(task.id) }
  val commentsState by commentsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
  var commentText by remember { mutableStateOf("") }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val statusColors = LocalStatusColors.current
  val isOverdue = JournalViewModel.isTaskOverdue(task)
  val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete task?") },
      text = { Text("This removes \"${task.title}\" and its discussion from the publication cycle.") },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirm = false
            onDeleteTask()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      },
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "$targetName · ${task.stageName}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        Row {
          if (isAdmin) {
            IconButton(onClick = onEditClick) {
              Icon(Icons.Outlined.Edit, contentDescription = "Edit task")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
              Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete task",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close task details")
          }
        }
      }

      Text(
        text = task.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (task.description.isNotBlank()) {
        Text(
          text = task.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = if (task.assigneeTypeEnum == AssigneeType.TEAM) Icons.Outlined.Groups else Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(assigneeLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
              text = if (isOverdue) "Overdue · ${task.dueDate}" else "Due ${task.dueDate}",
              style = MaterialTheme.typography.labelSmall,
              color = if (isOverdue) statusColors.overdueFg else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = "Priority: ${task.priorityEnum.label} · Created by $creatorName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Status: ${task.statusEnum.label}",
          style = MaterialTheme.typography.titleSmall,
        )
        if (canMutateTask) {
          Button(
            onClick = { viewModel.cycleTaskStatus(task) },
            modifier = Modifier.testTag("detail_cycle_status_button"),
          ) {
            Text("Mark ${task.statusEnum.next().label}")
          }
        } else {
          Text(
            text = "Read-only (assigned elsewhere)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!isAdmin && canMutateTask) {
        Text(
          text = "Members can update task status only. Only Admins can edit or delete tasks.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Text(
        text = "Comments",
        style = MaterialTheme.typography.titleMedium,
      )

      when (val state = commentsState) {
        is UiState.Loading -> Text(
          text = "Loading thread…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is UiState.Error -> Text(
          text = state.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
        is UiState.Success -> {
          val comments = state.data
          if (comments.isEmpty()) {
            Text(
              text = "No editorial notes yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            comments.forEach { comment ->
              CommentItemCard(comment = comment, dateFmt = dateFmt)
            }
          }
        }
      }

      if (canMutateTask) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = { Text("Add an editorial note…") },
            modifier = Modifier
              .weight(1f)
              .testTag("task_comment_input"),
            maxLines = 3,
          )
          IconButton(
            onClick = {
              val body = commentText.trim()
              if (body.isNotEmpty()) {
                viewModel.addComment(task.id, currentUserName, body) {
                  commentText = ""
                }
              }
            },
            enabled = commentText.isNotBlank(),
            modifier = Modifier
              .size(48.dp)
              .testTag("task_comment_send_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Send,
              contentDescription = "Send comment",
              tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CommentItemCard(
  comment: TaskComment,
  dateFmt: SimpleDateFormat,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = comment.authorName,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        val ts = comment.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"
        Text(
          text = ts,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = comment.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
  existingTask: EditorialTask?,
  defaultTargetId: String? = null,
  targets: List<Target>,
  users: List<UserProfile>,
  teams: List<Team>,
  currentUserId: String,
  isSaving: Boolean,
  onSave: (
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
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val initialTargetId = existingTask?.targetId ?: defaultTargetId ?: targets.firstOrNull()?.id.orEmpty()
  var targetId by remember { mutableStateOf(initialTargetId) }
  val selectedTarget = targets.firstOrNull { it.id == targetId }

  var title by remember { mutableStateOf(existingTask?.title ?: "") }
  var description by remember { mutableStateOf(existingTask?.description ?: "") }
  var stageName by remember {
    mutableStateOf(
      existingTask?.stageName ?: selectedTarget?.stages?.firstOrNull()?.name ?: "Peer review",
    )
  }
  var dueDate by remember {
    mutableStateOf(
      existingTask?.dueDate ?: selectedTarget?.stages?.firstOrNull()?.dueDate
      ?: IsoDateUtils.plusDays(IsoDateUtils.todayIso(), 7L),
    )
  }
  var priority by remember { mutableStateOf(existingTask?.priorityEnum ?: TaskPriority.MEDIUM) }
  var status by remember { mutableStateOf(existingTask?.statusEnum ?: TaskStatus.TODO) }
  var assigneeType by remember { mutableStateOf(existingTask?.assigneeTypeEnum ?: AssigneeType.USER) }
  var assigneeId by remember {
    mutableStateOf(
      existingTask?.assigneeId
        ?: users.firstOrNull { it.userId == currentUserId }?.userId
        ?: users.firstOrNull()?.userId
        ?: currentUserId,
    )
  }

  var showTargetMenu by remember { mutableStateOf(false) }
  var showStageMenu by remember { mutableStateOf(false) }
  var showAssigneeMenu by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val initialMillis = IsoDateUtils.isoToUtcMillis(dueDate)
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              dueDate = IsoDateUtils.utcMillisToIso(millis)
            }
            showDatePicker = false
          },
        ) { Text("Set date") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = pickerState)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existingTask == null) "New Editorial Task" else "Edit Task") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Task title") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_title_input"),
          singleLine = true,
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Notes / instructions") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2,
          maxLines = 3,
        )

        // Target picker
        Box {
          OutlinedButton(
            onClick = { showTargetMenu = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Target: ${selectedTarget?.name ?: "Select target"}")
          }
          DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
            targets.forEach { t ->
              DropdownMenuItem(
                text = { Text(t.name) },
                onClick = {
                  targetId = t.id
                  t.stages.firstOrNull()?.let { st ->
                    stageName = st.name
                    if (existingTask == null && st.dueDate.length == 10) dueDate = st.dueDate
                  }
                  showTargetMenu = false
                },
              )
            }
          }
        }

        // Stage picker
        Box {
          OutlinedButton(
            onClick = { showStageMenu = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Stage: $stageName")
          }
          DropdownMenu(expanded = showStageMenu, onDismissRequest = { showStageMenu = false }) {
            val stages = selectedTarget?.stages.orEmpty()
            stages.forEach { st ->
              DropdownMenuItem(
                text = { Text("${st.name} (${st.dueDate})") },
                onClick = {
                  stageName = st.name
                  if (st.dueDate.length == 10) dueDate = st.dueDate
                  showStageMenu = false
                },
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due (YYYY-MM-DD)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
          )
          IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = "Pick due date")
          }
        }

        Text("Priority", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TaskPriority.entries.forEach { p ->
            FilterChip(
              selected = priority == p,
              onClick = { priority = p },
              label = { Text(p.label) },
            )
          }
        }

        Text("Assign to", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = assigneeType == AssigneeType.USER,
            onClick = {
              assigneeType = AssigneeType.USER
              if (users.none { it.userId == assigneeId }) {
                assigneeId = users.firstOrNull()?.userId ?: currentUserId
              }
            },
            label = { Text("Person") },
          )
          FilterChip(
            selected = assigneeType == AssigneeType.TEAM,
            onClick = {
              assigneeType = AssigneeType.TEAM
              if (teams.none { it.id == assigneeId }) {
                assigneeId = teams.firstOrNull()?.id.orEmpty()
              }
            },
            enabled = teams.isNotEmpty(),
            label = { Text("Team") },
          )
        }

        Box {
          val label = if (assigneeType == AssigneeType.TEAM) {
            teams.firstOrNull { it.id == assigneeId }?.name ?: "Select team"
          } else {
            users.firstOrNull { it.userId == assigneeId }?.displayName ?: "Select member"
          }
          OutlinedButton(
            onClick = { showAssigneeMenu = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(label)
          }
          DropdownMenu(expanded = showAssigneeMenu, onDismissRequest = { showAssigneeMenu = false }) {
            if (assigneeType == AssigneeType.TEAM) {
              teams.forEach { team ->
                DropdownMenuItem(
                  text = { Text(team.name) },
                  onClick = {
                    assigneeId = team.id
                    showAssigneeMenu = false
                  },
                )
              }
            } else {
              users.forEach { user ->
                DropdownMenuItem(
                  text = { Text(user.displayName) },
                  onClick = {
                    assigneeId = user.userId
                    showAssigneeMenu = false
                  },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            existingTask?.id,
            title.trim(),
            description.trim(),
            targetId,
            stageName.trim(),
            dueDate.trim(),
            priority,
            status,
            assigneeType,
            assigneeId,
          )
        },
        enabled = !isSaving &&
          title.isNotBlank() &&
          targetId.isNotBlank() &&
          dueDate.length == 10 &&
          assigneeId.isNotBlank(),
        modifier = Modifier.testTag("save_task_button"),
      ) {
        Text(if (isSaving) "Saving…" else "Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
