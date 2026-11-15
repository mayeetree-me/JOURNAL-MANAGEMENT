package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserRole
import com.example.data.model.WORKSPACE_DEFAULT
import com.example.domain.WorkflowScheduleGenerator
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JournalRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ownerAndAllowlistedEmailsBecomeAdmin_unlistedBecomeMember_assignedMemberCanUpdateStatusAndComment() = runBlocking {
    val ownerEmail = JournalRepository.OWNER_ADMIN_EMAIL
    val adminUid = signInTestUser(ownerEmail)
    val adminRepo = JournalRepository(firestore, auth)
    val adminProfile = withTimeout(TIMEOUT_MS) {
      adminRepo.ensureCurrentUserBootstrapped("Mayeetree", ownerEmail, null).getOrThrow()
    }
    assertEquals(UserRole.ADMIN.value, adminProfile.role)

    val suffix = UUID.randomUUID().toString().take(6)
    val chosenAdminEmail = "coeditor_$suffix@journal.org"
    val memberEmail = "member_$suffix@journal.org"
    val outsiderEmail = "outsider_$suffix@journal.org"

    // Pre-authorize a second Admin email before that user signs in
    val allowResult = withTimeout(TIMEOUT_MS) {
      adminRepo.addAdminEmail(chosenAdminEmail, emptyList())
    }
    assertTrue("addAdminEmail failed: ${allowResult.exceptionOrNull()?.stackTraceToString()}", allowResult.isSuccess)

    signInTestUser(chosenAdminEmail)
    val chosenAdminRepo = JournalRepository(firestore, auth)
    val chosenAdminProfile = withTimeout(TIMEOUT_MS) {
      chosenAdminRepo.ensureCurrentUserBootstrapped("Co-Editor", chosenAdminEmail, null).getOrThrow()
    }
    assertEquals(UserRole.ADMIN.value, chosenAdminProfile.role)

    val memberUid = signInTestUser(memberEmail)
    val memberRepo = JournalRepository(firestore, auth)
    val memberProfile = withTimeout(TIMEOUT_MS) {
      memberRepo.ensureCurrentUserBootstrapped("Copyeditor", memberEmail, null).getOrThrow()
    }
    assertEquals(UserRole.MEMBER.value, memberProfile.role)

    val outsiderUid = signInTestUser(outsiderEmail)
    val outsiderRepo = JournalRepository(firestore, auth)
    val outsiderProfile = withTimeout(TIMEOUT_MS) {
      outsiderRepo.ensureCurrentUserBootstrapped("Outsider", outsiderEmail, null).getOrThrow()
    }
    assertEquals(UserRole.MEMBER.value, outsiderProfile.role)

    // Verify existing member upgrades to admin once their email is allowlisted, and reverts when removed
    signInTestUser(ownerEmail)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        adminRepo.addAdminEmail(chosenAdminEmail.uppercase(), listOf(outsiderUid))
      }.isSuccess,
    )
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        adminRepo.addAdminEmail(memberEmail, emptyList())
      }.isSuccess,
    )
    signInTestUser(memberEmail)
    val upgradedMember = withTimeout(TIMEOUT_MS) {
      memberRepo.ensureCurrentUserBootstrapped("Copyeditor", memberEmail, null).getOrThrow()
    }
    assertEquals(UserRole.ADMIN.value, upgradedMember.role)

    signInTestUser(ownerEmail)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        adminRepo.removeAdminEmail(memberEmail, listOf(memberUid))
      }.isSuccess,
    )
    signInTestUser(memberEmail)
    val revertedMember = withTimeout(TIMEOUT_MS) {
      memberRepo.ensureCurrentUserBootstrapped("Copyeditor", memberEmail, null).getOrThrow()
    }
    assertEquals(UserRole.MEMBER.value, revertedMember.role)

    // Switch back to Admin to create Target, Team, and Task
    signInTestUser(ownerEmail)
    val stages = WorkflowScheduleGenerator.generateDefaultStages("2026-12-20")
    val targetId = withTimeout(TIMEOUT_MS) {
      adminRepo.saveTargetWithWorkflow(
        targetId = null,
        name = "Vol 15 Issue 1 ($suffix)",
        description = "Winter Special",
        deadline = "2026-12-20",
        stages = stages,
        createStageTasks = false,
        defaultAssigneeType = AssigneeType.USER,
        defaultAssigneeId = adminUid,
      ).getOrThrow()
    }

    val teamId = withTimeout(TIMEOUT_MS) {
      adminRepo.saveTeam(
        teamId = null,
        name = "Proofing $suffix",
        description = "Proofreaders",
        leadId = adminUid,
        memberIds = listOf(memberUid),
      ).getOrThrow()
    }

    val taskId = withTimeout(TIMEOUT_MS) {
      adminRepo.saveTask(
        taskId = null,
        title = "Check proofs $suffix",
        description = "Verify footnotes",
        targetId = targetId,
        stageName = "Proofreading",
        dueDate = "2026-12-13",
        priority = TaskPriority.HIGH,
        status = TaskStatus.TODO,
        assigneeType = AssigneeType.TEAM,
        assigneeId = teamId,
      ).getOrThrow()
    }

    // Assigned team member can update status & add comment
    signInTestUser("member_$suffix@journal.org")
    val statusResult = withTimeout(TIMEOUT_MS) {
      memberRepo.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS)
    }
    assertTrue(statusResult.isSuccess)

    val commentResult = withTimeout(TIMEOUT_MS) {
      memberRepo.addComment(taskId, "Copyeditor", "Started checking footnotes.")
    }
    assertTrue(commentResult.isSuccess)

    val emittedTasks = withTimeout(TIMEOUT_MS) {
      memberRepo.observeTasks().first { list ->
        list.any { it.id == taskId && it.status == TaskStatus.IN_PROGRESS.value }
      }
    }
    assertTrue(emittedTasks.any { it.id == taskId })

    val emittedComments = withTimeout(TIMEOUT_MS) {
      memberRepo.observeComments(taskId).first { it.isNotEmpty() }
    }
    assertTrue(emittedComments.any { it.body == "Started checking footnotes." })

    // Assigned member CANNOT modify non-status task fields, create/delete tasks, or edit targets/events
    val memberEditTask = withTimeout(TIMEOUT_MS) {
      memberRepo.saveTask(
        taskId = taskId,
        title = "Member Edited Title",
        description = "Member edited description",
        targetId = targetId,
        stageName = "Proofreading",
        dueDate = "2026-12-13",
        priority = TaskPriority.LOW,
        status = TaskStatus.IN_PROGRESS,
        assigneeType = AssigneeType.TEAM,
        assigneeId = teamId,
      )
    }
    assertTrue(memberEditTask.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      memberEditTask.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val memberCreateTask = withTimeout(TIMEOUT_MS) {
      memberRepo.saveTask(
        taskId = null,
        title = "Unauthorized Member Task",
        description = "",
        targetId = targetId,
        stageName = "Proofreading",
        dueDate = "2026-12-13",
        priority = TaskPriority.MEDIUM,
        status = TaskStatus.TODO,
        assigneeType = AssigneeType.USER,
        assigneeId = memberUid,
      )
    }
    assertTrue(memberCreateTask.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      memberCreateTask.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val memberDeleteTask = withTimeout(TIMEOUT_MS) {
      memberRepo.deleteTask(taskId)
    }
    assertTrue(memberDeleteTask.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      memberDeleteTask.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val memberEditTarget = withTimeout(TIMEOUT_MS) {
      memberRepo.saveTargetWithWorkflow(
        targetId = targetId,
        name = "Member Edited Event",
        description = "Unauthorized edit",
        deadline = "2026-12-20",
        stages = stages,
        createStageTasks = false,
        defaultAssigneeType = AssigneeType.USER,
        defaultAssigneeId = memberUid,
      )
    }
    assertTrue(memberEditTarget.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      memberEditTarget.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    // Unassigned outsider CANNOT update task status or create target
    signInTestUser("outsider_$suffix@journal.org")
    val outsiderStatus = withTimeout(TIMEOUT_MS) {
      outsiderRepo.updateTaskStatus(taskId, TaskStatus.DONE)
    }
    assertTrue(outsiderStatus.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      outsiderStatus.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val outsiderTarget = withTimeout(TIMEOUT_MS) {
      outsiderRepo.saveTargetWithWorkflow(
        targetId = null,
        name = "Illegal Target",
        description = "",
        deadline = "2026-12-30",
        stages = stages,
        createStageTasks = false,
        defaultAssigneeType = AssigneeType.USER,
        defaultAssigneeId = outsiderUid,
      )
    }
    assertTrue(outsiderTarget.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      outsiderTarget.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )
  }

  @Test
  fun unauthenticatedCaller_firestoreSnapshotQuery_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    var caught: Exception? = null
    try {
      withTimeout(TIMEOUT_MS) {
        firestore.collection("tasks")
          .whereEqualTo("workspaceId", WORKSPACE_DEFAULT)
          .snapshots()
          .first()
      }
    } catch (e: Exception) {
      caught = e
    }
    val fsEx = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  private companion object {
    const val TIMEOUT_MS = 6000L
  }
}
