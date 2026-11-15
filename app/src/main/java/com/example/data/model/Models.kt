package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val value: String, val label: String) {
  ADMIN("admin", "Admin"),
  MEMBER("member", "Member");

  companion object {
    fun fromValue(value: String): UserRole =
      entries.firstOrNull { it.value == value } ?: MEMBER
  }
}

enum class TaskStatus(val value: String, val label: String) {
  TODO("todo", "To do"),
  IN_PROGRESS("in_progress", "In progress"),
  DONE("done", "Done");

  fun next(): TaskStatus = when (this) {
    TODO -> IN_PROGRESS
    IN_PROGRESS -> DONE
    DONE -> TODO
  }

  companion object {
    fun fromValue(value: String): TaskStatus =
      entries.firstOrNull { it.value == value } ?: TODO
  }
}

enum class TaskPriority(val value: String, val label: String) {
  LOW("low", "Low"),
  MEDIUM("medium", "Medium"),
  HIGH("high", "High");

  companion object {
    fun fromValue(value: String): TaskPriority =
      entries.firstOrNull { it.value == value } ?: MEDIUM
  }
}

enum class AssigneeType(val value: String) {
  USER("user"),
  TEAM("team");

  companion object {
    fun fromValue(value: String): AssigneeType =
      entries.firstOrNull { it.value == value } ?: USER
  }
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  val role: String = UserRole.MEMBER.value,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val roleEnum: UserRole get() = UserRole.fromValue(role)
  val isAdmin: Boolean get() = roleEnum == UserRole.ADMIN
}

@IgnoreExtraProperties
data class AdminEmailEntry(
  @DocumentId val id: String = "",
  val email: String = "",
  val addedBy: String = "",
  val isOwner: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Team(
  @DocumentId val id: String = "",
  val workspaceId: String = WORKSPACE_DEFAULT,
  val name: String = "",
  val description: String = "",
  val leadId: String = "",
  val memberIds: List<String> = emptyList(),
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun includesUser(uid: String): Boolean = leadId == uid || uid in memberIds
}

@IgnoreExtraProperties
data class WorkflowStage(
  val id: String = "",
  val name: String = "",
  val offsetDays: Long = 0L,
  val dueDate: String = "",
) {
  fun toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "offsetDays" to offsetDays,
    "dueDate" to dueDate,
  )
}

@IgnoreExtraProperties
data class Target(
  @DocumentId val id: String = "",
  val workspaceId: String = WORKSPACE_DEFAULT,
  val name: String = "",
  val description: String = "",
  val deadline: String = "",
  val stages: List<WorkflowStage> = emptyList(),
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WorkflowTemplate(
  @DocumentId val id: String = "",
  val workspaceId: String = WORKSPACE_DEFAULT,
  val name: String = "",
  val stages: List<WorkflowStage> = emptyList(),
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class EditorialTask(
  @DocumentId val id: String = "",
  val workspaceId: String = WORKSPACE_DEFAULT,
  val title: String = "",
  val description: String = "",
  val targetId: String = "",
  val stageName: String = "",
  val dueDate: String = "",
  val priority: String = TaskPriority.MEDIUM.value,
  val status: String = TaskStatus.TODO.value,
  val assigneeType: String = AssigneeType.USER.value,
  val assigneeId: String = "",
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val statusEnum: TaskStatus get() = TaskStatus.fromValue(status)
  val priorityEnum: TaskPriority get() = TaskPriority.fromValue(priority)
  val assigneeTypeEnum: AssigneeType get() = AssigneeType.fromValue(assigneeType)
}

@IgnoreExtraProperties
data class TaskComment(
  @DocumentId val id: String = "",
  val taskId: String = "",
  val authorId: String = "",
  val authorName: String = "",
  val body: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

const val WORKSPACE_DEFAULT = "default"
