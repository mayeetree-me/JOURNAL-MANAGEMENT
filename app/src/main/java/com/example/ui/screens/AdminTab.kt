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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminEmailEntry
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WorkflowTemplate
import com.example.data.repository.JournalRepository
import com.example.domain.IsoDateUtils
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.TaskCard
import com.example.ui.components.WorkloadBarChart
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.JournalViewModel

@Composable
fun AdminTab(
  currentUserId: String,
  targets: List<Target>,
  tasks: List<EditorialTask>,
  teams: List<Team>,
  users: List<UserProfile>,
  adminEmails: List<AdminEmailEntry>,
  templates: List<WorkflowTemplate>,
  isActionInFlight: Boolean,
  onNewTaskClick: () -> Unit,
  onAddAdminEmail: (String, () -> Unit) -> Unit,
  onRemoveAdminEmail: (String) -> Unit,
  onRoleChange: (UserProfile, UserRole) -> Unit,
  onDeleteTemplate: (WorkflowTemplate) -> Unit,
  onToggleTaskStatus: (EditorialTask) -> Unit,
  onTaskClick: (EditorialTask) -> Unit,
  modifier: Modifier = Modifier,
) {
  val todayIso = remember { IsoDateUtils.todayIso() }
  var newAdminEmailInput by remember { mutableStateOf("") }
  val statusColors = LocalStatusColors.current
  val targetSummaries = remember(targets, tasks) {
    JournalViewModel.buildTargetProgress(targets, tasks, todayIso)
  }
  val memberWorkloads = remember(users, teams, tasks) {
    JournalViewModel.buildMemberWorkload(users, teams, tasks, todayIso)
  }
  val overdueTasks = remember(tasks) {
    tasks.filter { JournalViewModel.isTaskOverdue(it, todayIso) }
  }
  val targetNames = remember(targets) { targets.associate { it.id to it.name } }
  val userNames = remember(users) { users.associate { it.userId to it.displayName } }
  val teamNames = remember(teams) { teams.associate { it.id to it.name } }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Admin Console",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Society-wide progress, workload analytics, templates, and roles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (targets.isNotEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedButton(onClick = onNewTaskClick) {
            Text("+ Task")
          }
        }
      }
    }

    // Progress per Target
    item {
      Text(
        text = "Progress per Target",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    if (targetSummaries.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No targets to measure",
          description = "Generate a target workflow in the Targets tab to track progress here.",
        )
      }
    } else {
      items(targetSummaries, key = { "admin_tg_${it.target.id}" }) { summary ->
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

    // Workload Bar Chart per Person
    item {
      Text(
        text = "Workload per Member",
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          if (memberWorkloads.isEmpty()) {
            Text(
              text = "No members registered yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            WorkloadBarChart(workloads = memberWorkloads)
          }
        }
      }
    }

    // Overdue Tasks List
    item {
      Text(
        text = "Overdue Tasks (${overdueTasks.size})",
        style = MaterialTheme.typography.titleLarge,
        color = if (overdueTasks.isNotEmpty()) statusColors.overdueFg else MaterialTheme.colorScheme.onBackground,
      )
    }

    if (overdueTasks.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No overdue tasks",
          description = "All scheduled editorial milestones are on track.",
        )
      }
    } else {
      items(overdueTasks, key = { "admin_ov_${it.id}" }) { task ->
        val assigneeLabel = if (task.assigneeTypeEnum == AssigneeType.TEAM) {
          teamNames[task.assigneeId] ?: "Team"
        } else {
          userNames[task.assigneeId] ?: "Member"
        }
        TaskCard(
          task = task,
          targetName = targetNames[task.targetId] ?: "Target",
          assigneeLabel = assigneeLabel,
          canEditStatus = true,
          onToggleStatus = { onToggleTaskStatus(task) },
          onClick = { onTaskClick(task) },
        )
      }
    }

    // Saved Workflow Templates
    item {
      Text(
        text = "Workflow Templates (${templates.size})",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    if (templates.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No saved templates",
          description = "Save any customized stage layout inside the Workflow Generator to reuse it later.",
        )
      }
    } else {
      items(templates, key = { it.id }) { tpl ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(tpl.name, style = MaterialTheme.typography.titleMedium)
              Text(
                text = tpl.stages.joinToString(" · ") { "${it.name} (${it.offsetDays}d)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(onClick = { onDeleteTemplate(tpl) }) {
              Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete template",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }

    // Admin Email Allowlist Management
    item {
      Text(
        text = "Admin Email Allowlist",
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Only listed Google emails sign in as Admin; all other accounts join as Members.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = newAdminEmailInput,
            onValueChange = { newAdminEmailInput = it },
            label = { Text("Authorize Admin email") },
            placeholder = { Text("coeditor@gmail.com") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_email_input"),
          )
          Button(
            onClick = {
              val trimmed = newAdminEmailInput.trim()
              if (trimmed.isNotEmpty()) {
                onAddAdminEmail(trimmed) { newAdminEmailInput = "" }
              }
            },
            enabled = !isActionInFlight && newAdminEmailInput.isNotBlank(),
            modifier = Modifier.testTag("add_admin_email_button"),
          ) {
            Text(if (isActionInFlight) "Saving…" else "Allow Admin Email")
          }

          val displayedEmails = remember(adminEmails) {
            val ownerPresent = adminEmails.any {
              it.email.equals(JournalRepository.OWNER_ADMIN_EMAIL, ignoreCase = true)
            }
            if (ownerPresent) {
              adminEmails
            } else {
              listOf(
                AdminEmailEntry(
                  id = JournalRepository.OWNER_ADMIN_EMAIL,
                  email = JournalRepository.OWNER_ADMIN_EMAIL,
                  isOwner = true,
                ),
              ) + adminEmails
            }
          }

          displayedEmails.forEach { entry ->
            val isOwnerEntry = entry.isOwner ||
              entry.email.equals(JournalRepository.OWNER_ADMIN_EMAIL, ignoreCase = true)
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = entry.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = if (isOwnerEntry) "Primary Owner (Protected)" else "Authorized Admin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                if (!isOwnerEntry) {
                  IconButton(
                    onClick = { onRemoveAdminEmail(entry.email) },
                    enabled = !isActionInFlight,
                    modifier = Modifier.testTag("remove_admin_email_${entry.email}"),
                  ) {
                    Icon(
                      Icons.Outlined.DeleteOutline,
                      contentDescription = "Remove ${entry.email} from Admin allowlist",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Member Roles Management
    item {
      Text(
        text = "Society Roles (${users.size})",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    items(users, key = { "role_${it.userId}" }) { user ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (user.userId == currentUserId) "${user.displayName} (You)" else user.displayName,
              style = MaterialTheme.typography.titleSmall,
            )
            Surface(
              shape = MaterialTheme.shapes.small,
              color = if (user.isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            ) {
              Text(
                text = user.roleEnum.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (user.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = user.roleEnum == UserRole.MEMBER,
              onClick = { onRoleChange(user, UserRole.MEMBER) },
              label = { Text("Member") },
            )
            FilterChip(
              selected = user.roleEnum == UserRole.ADMIN,
              onClick = { onRoleChange(user, UserRole.ADMIN) },
              label = { Text("Admin") },
            )
          }
        }
      }
    }
  }
}
