package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.domain.IsoDateUtils
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.TaskFilterState

@Composable
fun MyWorkTab(
  currentUserId: String,
  targets: List<Target>,
  tasks: List<EditorialTask>,
  teams: List<Team>,
  users: List<UserProfile>,
  filterState: TaskFilterState,
  onUpdateFilter: ((TaskFilterState) -> TaskFilterState) -> Unit,
  onClearFilters: () -> Unit,
  onToggleTaskStatus: (EditorialTask) -> Unit,
  onTaskClick: (EditorialTask) -> Unit,
  modifier: Modifier = Modifier,
) {
  val todayIso = remember { IsoDateUtils.todayIso() }
  val statusColors = LocalStatusColors.current
  val myTeamIds = remember(teams, currentUserId) {
    teams.filter { it.includesUser(currentUserId) }.map { it.id }.toSet()
  }
  val targetNames = remember(targets) { targets.associate { it.id to it.name } }
  val userNames = remember(users) { users.associate { it.userId to it.displayName } }
  val teamNames = remember(teams) { teams.associate { it.id to it.name } }

  val myTasks = remember(tasks, currentUserId, myTeamIds) {
    tasks.filter { JournalViewModel.isTaskAssignedToUser(it, currentUserId, myTeamIds) }
  }
  val filteredMyTasks = remember(myTasks, filterState, teams) {
    JournalViewModel.filterTasks(myTasks, filterState, teams, todayIso)
  }

  val overdueTasks = filteredMyTasks.filter { JournalViewModel.isTaskOverdue(it, todayIso) }
  val dueThisWeekTasks = filteredMyTasks.filter { task ->
    val d = IsoDateUtils.daysBetween(todayIso, task.dueDate)
    task.statusEnum != TaskStatus.DONE && d in 0L..7L
  }
  val openLaterTasks = filteredMyTasks.filter { task ->
    val d = IsoDateUtils.daysBetween(todayIso, task.dueDate)
    task.statusEnum != TaskStatus.DONE && d > 7L
  }
  val doneTasks = filteredMyTasks.filter { it.statusEnum == TaskStatus.DONE }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column {
        Text(
          text = "My Work",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Tasks assigned directly to you or any team you belong to. Tap any status badge to advance progress.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WorkSummaryBadge(
            text = "${overdueTasks.size} Overdue",
            bg = statusColors.overdueBg,
            fg = statusColors.overdueFg,
          )
          WorkSummaryBadge(
            text = "${dueThisWeekTasks.size} Due This Week",
            bg = statusColors.inProgressBg,
            fg = statusColors.inProgressFg,
          )
          WorkSummaryBadge(
            text = "${openLaterTasks.size + dueThisWeekTasks.size + overdueTasks.size} Open",
            bg = MaterialTheme.colorScheme.primaryContainer,
            fg = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }

    item {
      SearchAndFilterBar(
        filterState = filterState,
        targets = targets,
        users = users,
        teams = teams,
        onUpdateFilter = onUpdateFilter,
        onClearFilters = onClearFilters,
      )
    }

    if (filteredMyTasks.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Outlined.TaskAlt,
          title = "Your editorial queue is clear",
          description = "No tasks assigned to you or your teams match the current filters.",
          actionLabel = if (filterState != TaskFilterState()) "Clear filters" else null,
          onAction = if (filterState != TaskFilterState()) onClearFilters else null,
        )
      }
    } else {
      if (overdueTasks.isNotEmpty()) {
        item {
          SectionLabel(text = "Overdue (${overdueTasks.size})", color = statusColors.overdueFg)
        }
        items(overdueTasks, key = { "ov_${it.id}" }) { task ->
          val label = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
            teamNames[task.assigneeId] ?: "Team"
          } else {
            userNames[task.assigneeId] ?: "You"
          }
          TaskCard(
            task = task,
            targetName = targetNames[task.targetId] ?: "Target",
            assigneeLabel = label,
            canEditStatus = true,
            onToggleStatus = { onToggleTaskStatus(task) },
            onClick = { onTaskClick(task) },
          )
        }
      }

      if (dueThisWeekTasks.isNotEmpty()) {
        item {
          SectionLabel(text = "Due This Week (${dueThisWeekTasks.size})", color = statusColors.inProgressFg)
        }
        items(dueThisWeekTasks, key = { "wk_${it.id}" }) { task ->
          val label = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
            teamNames[task.assigneeId] ?: "Team"
          } else {
            userNames[task.assigneeId] ?: "You"
          }
          TaskCard(
            task = task,
            targetName = targetNames[task.targetId] ?: "Target",
            assigneeLabel = label,
            canEditStatus = true,
            onToggleStatus = { onToggleTaskStatus(task) },
            onClick = { onTaskClick(task) },
          )
        }
      }

      if (openLaterTasks.isNotEmpty()) {
        item {
          SectionLabel(text = "Upcoming Open (${openLaterTasks.size})", color = MaterialTheme.colorScheme.primary)
        }
        items(openLaterTasks, key = { "op_${it.id}" }) { task ->
          val label = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
            teamNames[task.assigneeId] ?: "Team"
          } else {
            userNames[task.assigneeId] ?: "You"
          }
          TaskCard(
            task = task,
            targetName = targetNames[task.targetId] ?: "Target",
            assigneeLabel = label,
            canEditStatus = true,
            onToggleStatus = { onToggleTaskStatus(task) },
            onClick = { onTaskClick(task) },
          )
        }
      }

      if (doneTasks.isNotEmpty()) {
        item {
          SectionLabel(text = "Completed (${doneTasks.size})", color = statusColors.doneFg)
        }
        items(doneTasks, key = { "dn_${it.id}" }) { task ->
          val label = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
            teamNames[task.assigneeId] ?: "Team"
          } else {
            userNames[task.assigneeId] ?: "You"
          }
          TaskCard(
            task = task,
            targetName = targetNames[task.targetId] ?: "Target",
            assigneeLabel = label,
            canEditStatus = true,
            onToggleStatus = { onToggleTaskStatus(task) },
            onClick = { onTaskClick(task) },
          )
        }
      }
    }
  }
}

@Composable
private fun WorkSummaryBadge(
  text: String,
  bg: androidx.compose.ui.graphics.Color,
  fg: androidx.compose.ui.graphics.Color,
) {
  Surface(shape = MaterialTheme.shapes.small, color = bg) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}

@Composable
private fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = color,
    modifier = Modifier.padding(top = 4.dp),
  )
}
