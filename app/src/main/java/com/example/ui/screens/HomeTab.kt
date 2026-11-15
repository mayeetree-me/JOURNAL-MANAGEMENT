package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.TaskFilterState

@Composable
fun HomeTab(
  currentUser: UserProfile?,
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
  val currentUid = currentUser?.userId.orEmpty()
  val isAdmin = currentUser?.isAdmin == true
  val myTeamIds = remember(teams, currentUid) {
    teams.filter { it.includesUser(currentUid) }.map { it.id }.toSet()
  }
  val targetNames = remember(targets) { targets.associate { it.id to it.name } }
  val userNames = remember(users) { users.associate { it.userId to it.displayName } }
  val teamNames = remember(teams) { teams.associate { it.id to it.name } }

  val targetSummaries = remember(targets, tasks) {
    JournalViewModel.buildTargetProgress(targets, tasks, todayIso)
  }
  val filteredTasks = remember(tasks, filterState, teams) {
    JournalViewModel.filterTasks(tasks, filterState, teams, todayIso)
  }

  val openCount = tasks.count { it.statusEnum != TaskStatus.DONE }
  val overdueCount = tasks.count { JournalViewModel.isTaskOverdue(it, todayIso) }
  val dueWeekCount = tasks.count { task ->
    val d = IsoDateUtils.daysBetween(todayIso, task.dueDate)
    task.statusEnum != TaskStatus.DONE && d in 0L..7L
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Editorial Desk",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = if (isAdmin) "Admin" else "Member",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Welcome back, ${currentUser?.displayName ?: "Member"}. Society targets and tasks stay synced live.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        HomeStatCard(
          label = "Targets",
          value = targets.size.toString(),
          accent = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        HomeStatCard(
          label = "Open",
          value = openCount.toString(),
          accent = statusColors.inProgressFg,
          modifier = Modifier.weight(1f),
        )
        HomeStatCard(
          label = "This Week",
          value = dueWeekCount.toString(),
          accent = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.weight(1f),
        )
        HomeStatCard(
          label = "Overdue",
          value = overdueCount.toString(),
          accent = statusColors.overdueFg,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Text(
        text = "Publication Targets Progress",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    if (targetSummaries.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Outlined.AutoStories,
          title = "No publication issues yet",
          description = if (isAdmin) {
            "Create a publication target in the Targets tab to start tracking editorial progress."
          } else {
            "Your society Admin has not published an active target cycle yet."
          },
        )
      }
    } else {
      items(targetSummaries, key = { it.target.id }) { summary ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            SegmentedProgressBar(summary = summary)
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "All Society Tasks (${filteredTasks.size})",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
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

    if (filteredTasks.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No matching tasks",
          description = "Adjust your search or filters to view other editorial assignments.",
          actionLabel = if (filterState != TaskFilterState()) "Clear filters" else null,
          onAction = if (filterState != TaskFilterState()) onClearFilters else null,
        )
      }
    } else {
      items(filteredTasks, key = { it.id }) { task ->
        val assigneeLabel = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
          teamNames[task.assigneeId] ?: "Team"
        } else {
          userNames[task.assigneeId] ?: "Member"
        }
        val canEdit = isAdmin || JournalViewModel.isTaskAssignedToUser(task, currentUid, myTeamIds)
        TaskCard(
          task = task,
          targetName = targetNames[task.targetId] ?: "Target",
          assigneeLabel = assigneeLabel,
          canEditStatus = canEdit,
          onToggleStatus = { onToggleTaskStatus(task) },
          onClick = { onTaskClick(task) },
        )
      }
    }
  }
}

@Composable
private fun HomeStatCard(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = accent,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
