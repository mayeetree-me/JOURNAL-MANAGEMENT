package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.DueDateFilter
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.TaskFilterState

@Composable
fun TaskCard(
  task: EditorialTask,
  targetName: String,
  assigneeLabel: String,
  canEditStatus: Boolean,
  onToggleStatus: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val statusColors = LocalStatusColors.current
  val isOverdue = JournalViewModel.isTaskOverdue(task)

  val (statusBg, statusFg, statusLabel, statusIcon) = when {
    isOverdue -> Quad(statusColors.overdueBg, statusColors.overdueFg, "Overdue · ${task.statusEnum.label}", Icons.Outlined.WarningAmber)
    task.statusEnum == TaskStatus.DONE -> Quad(statusColors.doneBg, statusColors.doneFg, "Done", Icons.Outlined.CheckCircle)
    task.statusEnum == TaskStatus.IN_PROGRESS -> Quad(statusColors.inProgressBg, statusColors.inProgressFg, "In progress", Icons.Outlined.Timelapse)
    else -> Quad(statusColors.todoBg, statusColors.todoFg, "To do", Icons.Outlined.RadioButtonUnchecked)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("task_card_${task.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(
      width = 1.dp,
      color = if (isOverdue) statusColors.overdueFg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
    ),
  ) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
      Box(
        modifier = Modifier
          .width(5.dp)
          .fillMaxHeight()
          .background(statusFg),
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 14.dp, vertical = 12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "$targetName · ${task.stageName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          PriorityBadge(priority = task.priorityEnum)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = task.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        if (task.description.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = task.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = if (task.assigneeTypeEnum == AssigneeType.TEAM) Icons.Outlined.Groups else Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = assigneeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (isOverdue) statusColors.overdueFg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Due ${task.dueDate}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverdue) statusColors.overdueFg else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            shape = MaterialTheme.shapes.small,
            color = statusBg,
            border = BorderStroke(1.dp, statusFg.copy(alpha = 0.25f)),
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .let { mod ->
                if (canEditStatus) mod.clickable(onClick = onToggleStatus) else mod
              }
              .testTag("task_status_chip_${task.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = statusIcon,
                contentDescription = "Task status: $statusLabel",
                tint = statusFg,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusFg,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PriorityBadge(priority: TaskPriority) {
  val (bg, fg) = when (priority) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    TaskPriority.LOW -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(
    shape = MaterialTheme.shapes.small,
    color = bg,
  ) {
    Text(
      text = priority.label,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun SearchAndFilterBar(
  filterState: TaskFilterState,
  targets: List<Target>,
  users: List<UserProfile>,
  teams: List<Team>,
  onUpdateFilter: ((TaskFilterState) -> TaskFilterState) -> Unit,
  onClearFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showTargetMenu by remember { mutableStateOf(false) }
  var showAssigneeMenu by remember { mutableStateOf(false) }
  var showTeamMenu by remember { mutableStateOf(false) }
  var showStatusMenu by remember { mutableStateOf(false) }
  var showDueMenu by remember { mutableStateOf(false) }

  val hasActiveFilter = filterState != TaskFilterState()

  Column(modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = filterState.query,
      onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("task_search_input"),
      placeholder = { Text("Search tasks, stages, or descriptions…") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search tasks") },
      trailingIcon = {
        if (filterState.query.isNotEmpty()) {
          IconButton(onClick = { onUpdateFilter { it.copy(query = "") } }) {
            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
          }
        }
      },
      singleLine = true,
      shape = MaterialTheme.shapes.medium,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.FilterList,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )

      // Status filter
      Box {
        FilterChip(
          selected = filterState.status != null,
          onClick = { showStatusMenu = true },
          label = { Text(filterState.status?.label ?: "Status") },
        )
        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
          DropdownMenuItem(
            text = { Text("All statuses") },
            onClick = {
              onUpdateFilter { it.copy(status = null) }
              showStatusMenu = false
            },
          )
          TaskStatus.entries.forEach { st ->
            DropdownMenuItem(
              text = { Text(st.label) },
              onClick = {
                onUpdateFilter { it.copy(status = st) }
                showStatusMenu = false
              },
            )
          }
        }
      }

      // Due date filter
      Box {
        FilterChip(
          selected = filterState.dueFilter != DueDateFilter.ALL,
          onClick = { showDueMenu = true },
          label = { Text(filterState.dueFilter.label) },
        )
        DropdownMenu(expanded = showDueMenu, onDismissRequest = { showDueMenu = false }) {
          DueDateFilter.entries.forEach { df ->
            DropdownMenuItem(
              text = { Text(df.label) },
              onClick = {
                onUpdateFilter { it.copy(dueFilter = df) }
                showDueMenu = false
              },
            )
          }
        }
      }

      // Target filter
      Box {
        val selectedTargetName = targets.firstOrNull { it.id == filterState.targetId }?.name
        FilterChip(
          selected = filterState.targetId != null,
          onClick = { showTargetMenu = true },
          label = { Text(selectedTargetName ?: "Target") },
        )
        DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
          DropdownMenuItem(
            text = { Text("All targets") },
            onClick = {
              onUpdateFilter { it.copy(targetId = null) }
              showTargetMenu = false
            },
          )
          targets.forEach { target ->
            DropdownMenuItem(
              text = { Text(target.name) },
              onClick = {
                onUpdateFilter { it.copy(targetId = target.id) }
                showTargetMenu = false
              },
            )
          }
        }
      }

      // Assignee filter
      Box {
        val selectedUserName = users.firstOrNull { it.userId == filterState.assigneeUserId }?.displayName
        FilterChip(
          selected = filterState.assigneeUserId != null,
          onClick = { showAssigneeMenu = true },
          label = { Text(selectedUserName ?: "Assignee") },
        )
        DropdownMenu(expanded = showAssigneeMenu, onDismissRequest = { showAssigneeMenu = false }) {
          DropdownMenuItem(
            text = { Text("All assignees") },
            onClick = {
              onUpdateFilter { it.copy(assigneeUserId = null) }
              showAssigneeMenu = false
            },
          )
          users.forEach { u ->
            DropdownMenuItem(
              text = { Text(u.displayName) },
              onClick = {
                onUpdateFilter { it.copy(assigneeUserId = u.userId) }
                showAssigneeMenu = false
              },
            )
          }
        }
      }

      // Team filter
      Box {
        val selectedTeamName = teams.firstOrNull { it.id == filterState.teamId }?.name
        FilterChip(
          selected = filterState.teamId != null,
          onClick = { showTeamMenu = true },
          label = { Text(selectedTeamName ?: "Team") },
        )
        DropdownMenu(expanded = showTeamMenu, onDismissRequest = { showTeamMenu = false }) {
          DropdownMenuItem(
            text = { Text("All teams") },
            onClick = {
              onUpdateFilter { it.copy(teamId = null) }
              showTeamMenu = false
            },
          )
          teams.forEach { team ->
            DropdownMenuItem(
              text = { Text(team.name) },
              onClick = {
                onUpdateFilter { it.copy(teamId = team.id) }
                showTeamMenu = false
              },
            )
          }
        }
      }

      if (hasActiveFilter) {
        TextButton(onClick = onClearFilters) {
          Text("Reset")
        }
      }
    }
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector = Icons.Outlined.AutoStories,
  title: String,
  description: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(12.dp),
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onAction) {
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
fun LoadingStateView(message: String = "Syncing society workspace…") {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(40.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
