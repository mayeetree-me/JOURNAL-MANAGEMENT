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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AssigneeType
import com.example.data.model.Target
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.WorkflowStage
import com.example.data.model.WorkflowTemplate
import com.example.domain.IsoDateUtils
import com.example.domain.WorkflowScheduleGenerator
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowGeneratorSheet(
  existingTarget: Target?,
  allTargets: List<Target>,
  templates: List<WorkflowTemplate>,
  users: List<UserProfile>,
  teams: List<Team>,
  currentUserId: String,
  isSaving: Boolean,
  onSaveTarget: (
    targetId: String?,
    name: String,
    description: String,
    deadline: String,
    stages: List<WorkflowStage>,
    createTasks: Boolean,
    assigneeType: AssigneeType,
    assigneeId: String,
  ) -> Unit,
  onSaveTemplate: (name: String, stages: List<WorkflowStage>) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val defaultDeadline = existingTarget?.deadline?.ifBlank { null }
    ?: IsoDateUtils.plusDays(IsoDateUtils.todayIso(), 60L)

  var selectedTargetId by remember(existingTarget) { mutableStateOf(existingTarget?.id) }
  var name by remember(existingTarget) { mutableStateOf(existingTarget?.name ?: "") }
  var description by remember(existingTarget) { mutableStateOf(existingTarget?.description ?: "") }
  var deadline by remember(existingTarget) { mutableStateOf(defaultDeadline) }
  var stages by remember(existingTarget) {
    mutableStateOf(
      if (existingTarget != null && existingTarget.stages.isNotEmpty()) {
        WorkflowScheduleGenerator.applyDeadlineToStages(existingTarget.stages, deadline)
      } else {
        WorkflowScheduleGenerator.generateDefaultStages(deadline)
      },
    )
  }

  var showTargetPicker by remember { mutableStateOf(false) }
  var showTemplatePicker by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showSaveTemplateField by remember { mutableStateOf(false) }
  var templateName by remember { mutableStateOf("") }

  var createStageTasks by remember { mutableStateOf(existingTarget == null) }
  var assigneeType by remember { mutableStateOf(AssigneeType.USER) }
  var assigneeId by remember { mutableStateOf(currentUserId) }
  var showAssigneeMenu by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val initialMillis = IsoDateUtils.isoToUtcMillis(deadline)
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              val picked = IsoDateUtils.utcMillisToIso(millis)
              deadline = picked
              stages = WorkflowScheduleGenerator.applyDeadlineToStages(stages, picked)
            }
            showDatePicker = false
          },
        ) { Text("Apply") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = pickerState)
    }
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
          text = if (selectedTargetId == null) "Target & Workflow Generator" else "Edit Target Workflow",
          style = MaterialTheme.typography.headlineSmall,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "Close workflow generator")
        }
      }

      if (existingTarget == null && allTargets.isNotEmpty()) {
        Box {
          OutlinedButton(onClick = { showTargetPicker = true }) {
            Text(
              if (selectedTargetId == null) "Mode: Create new target (tap to pick existing)"
              else "Target: ${name.ifBlank { "Selected" }}",
            )
          }
          DropdownMenu(expanded = showTargetPicker, onDismissRequest = { showTargetPicker = false }) {
            DropdownMenuItem(
              text = { Text("Create new target") },
              onClick = {
                selectedTargetId = null
                name = ""
                description = ""
                showTargetPicker = false
              },
            )
            allTargets.forEach { target ->
              DropdownMenuItem(
                text = { Text(target.name) },
                onClick = {
                  selectedTargetId = target.id
                  name = target.name
                  description = target.description
                  deadline = target.deadline
                  stages = if (target.stages.isNotEmpty()) {
                    WorkflowScheduleGenerator.applyDeadlineToStages(target.stages, target.deadline)
                  } else {
                    WorkflowScheduleGenerator.generateDefaultStages(target.deadline)
                  }
                  showTargetPicker = false
                },
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Target / Issue Name") },
        placeholder = { Text("e.g., Vol. 15, Issue 1 — Winter") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("target_name_input"),
        singleLine = true,
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Editorial Focus / Description") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 3,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = deadline,
          onValueChange = {
            deadline = it
            if (it.length == 10) {
              stages = WorkflowScheduleGenerator.applyDeadlineToStages(stages, it)
            }
          },
          label = { Text("Publish Deadline (YYYY-MM-DD)") },
          modifier = Modifier.weight(1f),
          singleLine = true,
        )
        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.height(54.dp),
        ) {
          Icon(Icons.Outlined.CalendarToday, contentDescription = "Pick deadline date")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { showTemplatePicker = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Outlined.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Apply template")
          }
          DropdownMenu(expanded = showTemplatePicker, onDismissRequest = { showTemplatePicker = false }) {
            DropdownMenuItem(
              text = { Text("Default 7-stage cycle (-60d to 0d)") },
              onClick = {
                stages = WorkflowScheduleGenerator.generateDefaultStages(deadline)
                showTemplatePicker = false
              },
            )
            templates.forEach { tpl ->
              DropdownMenuItem(
                text = { Text("${tpl.name} (${tpl.stages.size} stages)") },
                onClick = {
                  stages = WorkflowScheduleGenerator.applyDeadlineToStages(tpl.stages, deadline)
                  showTemplatePicker = false
                },
              )
            }
          }
        }

        OutlinedButton(
          onClick = { showSaveTemplateField = !showSaveTemplateField },
        ) {
          Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save template")
        }
      }

      if (showSaveTemplateField) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = templateName,
            onValueChange = { templateName = it },
            label = { Text("Template name") },
            modifier = Modifier.weight(1f),
            singleLine = true,
          )
          Button(
            onClick = {
              if (templateName.isNotBlank() && stages.isNotEmpty()) {
                onSaveTemplate(templateName.trim(), stages)
                templateName = ""
                showSaveTemplateField = false
              }
            },
            enabled = templateName.isNotBlank() && stages.isNotEmpty(),
          ) {
            Text("Save")
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Generated Stages (${stages.size})",
          style = MaterialTheme.typography.titleMedium,
        )
        TextButton(
          onClick = {
            stages = stages + WorkflowStage(
              id = UUID.randomUUID().toString().take(8),
              name = "Custom stage",
              offsetDays = -10L,
              dueDate = WorkflowScheduleGenerator.computeSingleStageDueDate(deadline, -10L),
            )
          },
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add stage")
        }
      }

      stages.forEachIndexed { index, stage ->
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = stage.name,
                onValueChange = { updated ->
                  stages = stages.toMutableList().also {
                    it[index] = stage.copy(name = updated)
                  }
                },
                label = { Text("Stage") },
                modifier = Modifier.weight(1f),
                singleLine = true,
              )
              OutlinedTextField(
                value = stage.offsetDays.toString(),
                onValueChange = { raw ->
                  val parsed = raw.toLongOrNull()
                  stages = stages.toMutableList().also {
                    val safeOffset = parsed ?: stage.offsetDays
                    it[index] = stage.copy(
                      offsetDays = safeOffset,
                      dueDate = WorkflowScheduleGenerator.computeSingleStageDueDate(deadline, safeOffset),
                    )
                  }
                },
                label = { Text("Days") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(86.dp),
                singleLine = true,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Due ${stage.dueDate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Row {
                IconButton(
                  onClick = {
                    if (index > 0) {
                      val list = stages.toMutableList()
                      val item = list.removeAt(index)
                      list.add(index - 1, item)
                      stages = list
                    }
                  },
                  enabled = index > 0,
                ) {
                  Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move stage up")
                }
                IconButton(
                  onClick = {
                    if (index < stages.lastIndex) {
                      val list = stages.toMutableList()
                      val item = list.removeAt(index)
                      list.add(index + 1, item)
                      stages = list
                    }
                  },
                  enabled = index < stages.lastIndex,
                ) {
                  Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move stage down")
                }
                IconButton(
                  onClick = {
                    stages = stages.toMutableList().also { it.removeAt(index) }
                  },
                  enabled = stages.size > 1,
                ) {
                  Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove stage")
                }
              }
            }
          }
        }
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Auto-create tasks for each stage",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Seeds one editorial milestone task per stage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = createStageTasks,
              onCheckedChange = { createStageTasks = it },
            )
          }

          if (createStageTasks) {
            Spacer(modifier = Modifier.height(8.dp))
            Box {
              val currentAssigneeName = if (assigneeType == AssigneeType.TEAM) {
                teams.firstOrNull { it.id == assigneeId }?.name ?: "Select team"
              } else {
                users.firstOrNull { it.userId == assigneeId }?.displayName ?: "Me"
              }
              OutlinedButton(onClick = { showAssigneeMenu = true }) {
                Text("Default assignee: $currentAssigneeName")
              }
              DropdownMenu(expanded = showAssigneeMenu, onDismissRequest = { showAssigneeMenu = false }) {
                users.forEach { user ->
                  DropdownMenuItem(
                    text = { Text("Member: ${user.displayName}") },
                    onClick = {
                      assigneeType = AssigneeType.USER
                      assigneeId = user.userId
                      showAssigneeMenu = false
                    },
                  )
                }
                teams.forEach { team ->
                  DropdownMenuItem(
                    text = { Text("Team: ${team.name}") },
                    onClick = {
                      assigneeType = AssigneeType.TEAM
                      assigneeId = team.id
                      showAssigneeMenu = false
                    },
                  )
                }
              }
            }
          }
        }
      }

      Button(
        onClick = {
          onSaveTarget(
            selectedTargetId,
            name.trim(),
            description.trim(),
            deadline.trim(),
            stages,
            createStageTasks,
            assigneeType,
            assigneeId,
          )
        },
        enabled = !isSaving && name.isNotBlank() && deadline.length == 10 && stages.isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_target_workflow_button"),
      ) {
        Text(if (isSaving) "Saving…" else "Save Target Workflow")
      }
    }
  }
}
