package com.example.domain

import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.DueDateFilter
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.TaskFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowScheduleGeneratorTest {

  @Test
  fun generateDefaultStages_countsBackCanonicalOffsetsFromDeadline() {
    val stages = WorkflowScheduleGenerator.generateDefaultStages("2026-12-31")
    assertEquals(7, stages.size)
    assertEquals("Call for papers", stages[0].name)
    assertEquals(-60L, stages[0].offsetDays)
    assertEquals("2026-11-01", stages[0].dueDate)

    assertEquals("Peer review", stages[1].name)
    assertEquals("2026-11-16", stages[1].dueDate)

    assertEquals("Publish", stages[6].name)
    assertEquals(0L, stages[6].offsetDays)
    assertEquals("2026-12-31", stages[6].dueDate)
  }

  @Test
  fun filterAndAnalytics_computesOverdueProgressAndTeamWorkload() {
    val today = "2026-11-14"
    val team = Team(id = "team_1", name = "Production", leadId = "u1", memberIds = listOf("u1", "u2"))
    val target = Target(id = "tg_1", name = "Vol 14", deadline = "2026-12-01")
    val users = listOf(
      UserProfile(id = "u1", userId = "u1", displayName = "Alice"),
      UserProfile(id = "u2", userId = "u2", displayName = "Bob"),
    )
    val tasks = listOf(
      EditorialTask(
        id = "t1",
        title = "Overdue check",
        targetId = "tg_1",
        dueDate = "2026-11-10",
        status = TaskStatus.IN_PROGRESS.value,
        assigneeType = AssigneeType.TEAM.value,
        assigneeId = "team_1",
      ),
      EditorialTask(
        id = "t2",
        title = "Completed item",
        targetId = "tg_1",
        dueDate = "2026-11-12",
        status = TaskStatus.DONE.value,
        assigneeType = AssigneeType.USER.value,
        assigneeId = "u1",
      ),
    )

    assertTrue(JournalViewModel.isTaskOverdue(tasks[0], today))
    assertFalse(JournalViewModel.isTaskOverdue(tasks[1], today))

    val progress = JournalViewModel.buildTargetProgress(listOf(target), tasks, today).single()
    assertEquals(1, progress.doneCount)
    assertEquals(1, progress.overdueCount)
    assertEquals(50, progress.completionPercent)

    val workloads = JournalViewModel.buildMemberWorkload(users, listOf(team), tasks, today)
    val bobWorkload = workloads.first { it.user.userId == "u2" }
    assertEquals(1, bobWorkload.openCount)
    assertEquals(1, bobWorkload.overdueCount)

    val overdueFiltered = JournalViewModel.filterTasks(
      tasks,
      TaskFilterState(dueFilter = DueDateFilter.OVERDUE),
      listOf(team),
      today,
    )
    assertEquals(listOf("t1"), overdueFiltered.map { it.id })
  }
}
