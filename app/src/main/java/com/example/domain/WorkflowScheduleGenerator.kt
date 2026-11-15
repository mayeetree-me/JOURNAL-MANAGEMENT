package com.example.domain

import com.example.data.model.WorkflowStage
import java.util.UUID

object WorkflowScheduleGenerator {

  private data class StageBlueprint(val name: String, val offsetDays: Long)

  private val defaultBlueprints = listOf(
    StageBlueprint("Call for papers", -60L),
    StageBlueprint("Peer review", -45L),
    StageBlueprint("Author revisions", -32L),
    StageBlueprint("Copyediting", -22L),
    StageBlueprint("Layout & typesetting", -14L),
    StageBlueprint("Proofreading", -7L),
    StageBlueprint("Publish", 0L),
  )

  fun generateDefaultStages(deadlineIso: String): List<WorkflowStage> {
    val deadline = IsoDateUtils.normalizeOrToday(deadlineIso)
    return defaultBlueprints.map { blueprint ->
      WorkflowStage(
        id = UUID.randomUUID().toString().take(8),
        name = blueprint.name,
        offsetDays = blueprint.offsetDays,
        dueDate = IsoDateUtils.plusDays(deadline, blueprint.offsetDays),
      )
    }
  }

  fun applyDeadlineToStages(
    stages: List<WorkflowStage>,
    deadlineIso: String,
  ): List<WorkflowStage> {
    val deadline = IsoDateUtils.normalizeOrToday(deadlineIso)
    return stages.map { stage ->
      stage.copy(
        id = stage.id.ifBlank { UUID.randomUUID().toString().take(8) },
        dueDate = IsoDateUtils.plusDays(deadline, stage.offsetDays),
      )
    }
  }

  fun computeSingleStageDueDate(deadlineIso: String, offsetDays: Long): String {
    val deadline = IsoDateUtils.normalizeOrToday(deadlineIso)
    return IsoDateUtils.plusDays(deadline, offsetDays)
  }
}
