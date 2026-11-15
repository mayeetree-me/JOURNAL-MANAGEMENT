package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.EditorialTask
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.NotificationItem

@Composable
fun TeamEditorDialog(
  existingTeam: Team?,
  users: List<UserProfile>,
  currentUserId: String,
  isSaving: Boolean,
  onSave: (
    teamId: String?,
    name: String,
    description: String,
    leadId: String,
    memberIds: List<String>,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(existingTeam?.name ?: "") }
  var description by remember { mutableStateOf(existingTeam?.description ?: "") }
  var leadId by remember {
    mutableStateOf(
      existingTeam?.leadId ?: users.firstOrNull { it.userId == currentUserId }?.userId
      ?: users.firstOrNull()?.userId ?: currentUserId,
    )
  }
  var selectedMemberIds by remember {
    mutableStateOf((existingTeam?.memberIds ?: listOf(leadId)).toSet())
  }
  var showLeadMenu by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existingTeam == null) "Create Editorial Team" else "Edit Team") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Team name") },
          placeholder = { Text("e.g., Copyediting Board") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("team_name_input"),
          singleLine = true,
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Mandate / description") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2,
          maxLines = 3,
        )

        Box {
          val leadName = users.firstOrNull { it.userId == leadId }?.displayName ?: "Select lead"
          OutlinedButton(
            onClick = { showLeadMenu = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Team Lead: $leadName")
          }
          DropdownMenu(expanded = showLeadMenu, onDismissRequest = { showLeadMenu = false }) {
            users.forEach { user ->
              DropdownMenuItem(
                text = { Text(user.displayName) },
                onClick = {
                  leadId = user.userId
                  selectedMemberIds = selectedMemberIds + user.userId
                  showLeadMenu = false
                },
              )
            }
          }
        }

        Text("Team Members", style = MaterialTheme.typography.labelLarge)
        users.forEach { user ->
          val checked = user.userId in selectedMemberIds || user.userId == leadId
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(enabled = user.userId != leadId) {
                selectedMemberIds = if (user.userId in selectedMemberIds) {
                  selectedMemberIds - user.userId
                } else {
                  selectedMemberIds + user.userId
                }
              }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = checked,
              enabled = user.userId != leadId,
              onCheckedChange = { isChecked ->
                selectedMemberIds = if (isChecked) {
                  selectedMemberIds + user.userId
                } else {
                  selectedMemberIds - user.userId
                }
              },
            )
            Column {
              Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
              if (user.userId == leadId) {
                Text(
                  text = "Team Lead",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
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
            existingTeam?.id,
            name.trim(),
            description.trim(),
            leadId,
            (selectedMemberIds + leadId).toList(),
          )
        },
        enabled = !isSaving && name.isNotBlank() && leadId.isNotBlank(),
        modifier = Modifier.testTag("save_team_button"),
      ) {
        Text(if (isSaving) "Saving…" else "Save Team")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
  notifications: List<NotificationItem>,
  targetNames: Map<String, String>,
  onTaskClick: (EditorialTask) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val statusColors = LocalStatusColors.current

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
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Alerts & Upcoming Deadlines",
            style = MaterialTheme.typography.headlineSmall,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "Close notifications")
        }
      }

      if (notifications.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Outlined.NotificationsActive,
          title = "All clear",
          description = "You have no newly assigned tasks or open items due within 3 days.",
        )
      } else {
        notifications.forEach { item ->
          val badgeBg = if (item.isUrgent) statusColors.overdueBg else MaterialTheme.colorScheme.primaryContainer
          val badgeFg = if (item.isUrgent) statusColors.overdueFg else MaterialTheme.colorScheme.onPrimaryContainer
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                onDismiss()
                onTaskClick(item.task)
              },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = targetNames[item.task.targetId] ?: "Target",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = item.task.title,
                  style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "Due ${item.task.dueDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Surface(
                shape = MaterialTheme.shapes.small,
                color = badgeBg,
              ) {
                Text(
                  text = item.reason,
                  style = MaterialTheme.typography.labelSmall,
                  color = badgeFg,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
