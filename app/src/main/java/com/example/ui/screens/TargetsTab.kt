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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GanttTimelineChart
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.viewmodel.JournalViewModel

@Composable
fun TargetsTab(
  isAdmin: Boolean,
  targets: List<Target>,
  tasks: List<EditorialTask>,
  onNewTargetClick: () -> Unit,
  onEditTargetWorkflow: (Target) -> Unit,
  onDeleteTarget: (Target) -> Unit,
  onAddTaskForTarget: (Target) -> Unit,
  modifier: Modifier = Modifier,
) {
  val summaries = remember(targets, tasks) {
    JournalViewModel.buildTargetProgress(targets, tasks)
  }
  var targetPendingDelete by remember { mutableStateOf<Target?>(null) }

  if (targetPendingDelete != null) {
    val t = targetPendingDelete!!
    AlertDialog(
      onDismissRequest = { targetPendingDelete = null },
      title = { Text("Delete target?") },
      text = { Text("Remove \"${t.name}\"? Existing tasks referencing it will remain visible in history until deleted.") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteTarget(t)
            targetPendingDelete = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { targetPendingDelete = null }) { Text("Cancel") }
      },
    )
  }

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
            text = "Targets & Workflows",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Issues, volumes, and backward-scheduled milestones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isAdmin) {
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onNewTargetClick,
            modifier = Modifier.testTag("new_target_button"),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Generate")
          }
        }
      }
    }

    if (summaries.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Outlined.CalendarMonth,
          title = "No publication targets yet",
          description = if (isAdmin) {
            "Tap Generate to pick a deadline and auto-build stages from Call for Papers (-60d) to Publish (0d)."
          } else {
            "No journal issues have been scheduled yet."
          },
          actionLabel = if (isAdmin) "Generate first workflow" else null,
          onAction = if (isAdmin) onNewTargetClick else null,
        )
      }
    } else {
      items(summaries, key = { it.target.id }) { summary ->
        val target = summary.target
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Text(
                    text = "Deadline ${target.deadline}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                if (target.description.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = target.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (isAdmin) {
                Row {
                  IconButton(onClick = { onEditTargetWorkflow(target) }) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = "Edit workflow")
                  }
                  IconButton(onClick = { targetPendingDelete = target }) {
                    Icon(
                      Icons.Outlined.DeleteOutline,
                      contentDescription = "Delete target",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }

            SegmentedProgressBar(summary = summary)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            GanttTimelineChart(target = target, tasks = tasks)

            if (isAdmin) {
              OutlinedButton(
                onClick = { onAddTaskForTarget(target) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Assign task in ${target.name}")
              }
            }
          }
        }
      }
    }
  }
}
