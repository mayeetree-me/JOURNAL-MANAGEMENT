package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamsTab(
  currentUserId: String,
  isAdmin: Boolean,
  teams: List<Team>,
  users: List<UserProfile>,
  tasks: List<EditorialTask>,
  onCreateTeam: () -> Unit,
  onEditTeam: (Team) -> Unit,
  onDeleteTeam: (Team) -> Unit,
  modifier: Modifier = Modifier,
) {
  val userMap = remember(users) { users.associateBy { it.userId } }
  var pendingDelete by remember { mutableStateOf<Team?>(null) }

  if (pendingDelete != null) {
    val team = pendingDelete!!
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("Remove team?") },
      text = { Text("Delete \"${team.name}\"?") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteTeam(team)
            pendingDelete = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Remove") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Editorial Teams",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Tasks assigned to a team appear in every member's My Work feed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isAdmin) {
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onCreateTeam,
            modifier = Modifier.testTag("create_team_button"),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Team")
          }
        }
      }
    }

    if (teams.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Outlined.Groups,
          title = "No teams created yet",
          description = if (isAdmin) {
            "Create editorial committees such as Peer Review or Production & Proofing."
          } else {
            "Teams created by your Admin will appear here."
          },
          actionLabel = if (isAdmin) "Create first team" else null,
          onAction = if (isAdmin) onCreateTeam else null,
        )
      }
    } else {
      items(teams, key = { it.id }) { team ->
        val teamTasks = tasks.filter { it.assigneeTypeEnum == AssigneeType.TEAM && it.assigneeId == team.id }
        val openCount = teamTasks.count { it.statusEnum != TaskStatus.DONE }
        val doneCount = teamTasks.count { it.statusEnum == TaskStatus.DONE }
        val leadName = userMap[team.leadId]?.displayName ?: "Unassigned lead"
        val isMember = team.includesUser(currentUserId)

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(
            width = 1.dp,
            color = if (isMember) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
          ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = team.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  if (isMember) {
                    Surface(
                      shape = MaterialTheme.shapes.small,
                      color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                      Text(
                        text = "Your team",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
                if (team.description.isNotBlank()) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = team.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (isAdmin) {
                Row {
                  IconButton(onClick = { onEditTeam(team) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit team")
                  }
                  IconButton(onClick = { pendingDelete = team }) {
                    Icon(
                      Icons.Outlined.DeleteOutline,
                      contentDescription = "Delete team",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Lead: $leadName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "$openCount open · $doneCount done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              team.memberIds.distinct().forEach { uid ->
                val memberName = userMap[uid]?.displayName ?: "Member"
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                  Text(
                    text = if (uid == team.leadId) "$memberName ★" else memberName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
