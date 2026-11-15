package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminEmailEntry
import com.example.data.model.AssigneeType
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskComment
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WORKSPACE_DEFAULT
import com.example.data.model.WorkflowStage
import com.example.data.model.WorkflowTemplate
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.IsoDateUtils
import com.example.domain.WorkflowScheduleGenerator
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JournalRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  suspend fun ensureCurrentUserBootstrapped(
    displayName: String,
    email: String?,
    photoUrl: String?,
  ): Result<UserProfile> {
    val firebaseUser = auth.currentUser
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    val uid = firebaseUser.uid
    val userRef = db.collection("users").document(uid)
    val privateRef = userRef.collection("private").document("info")

    return try {
      val existingUserSnap = userRef.get().await()
      val safeName = displayName.trim().ifBlank { "Journal Member" }.take(80)
      val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
      val rawEmail = (firebaseUser.email ?: email)?.trim().orEmpty()
      val normalizedEmail = rawEmail.lowercase()
      val safeEmail = normalizedEmail.takeIf { it.length >= 3 }?.take(160) ?: "$uid@society.local"
      val isVerified = firebaseUser.isEmailVerified

      val isOwner = isVerified && normalizedEmail == OWNER_ADMIN_EMAIL
      val isAllowlisted = if (isOwner) {
        true
      } else if (isVerified && isValidAllowlistEmail(normalizedEmail)) {
        try {
          db.collection("admin_emails").document(normalizedEmail).get().await().exists()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          false
        }
      } else {
        false
      }

      val assignedRole = if (isOwner || isAllowlisted) {
        UserRole.ADMIN.value
      } else {
        UserRole.MEMBER.value
      }

      val ownerAllowRef = db.collection("admin_emails").document(OWNER_ADMIN_EMAIL)
      val shouldSeedOwnerEntry = isOwner && try {
        !ownerAllowRef.get().await().exists()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        false
      }

      val batch = db.batch()
      if (shouldSeedOwnerEntry) {
        batch.set(
          ownerAllowRef,
          mapOf(
            "email" to OWNER_ADMIN_EMAIL,
            "addedBy" to uid,
            "isOwner" to true,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (!existingUserSnap.exists()) {
        val profilePayload = buildMap<String, Any> {
          put("userId", uid)
          put("displayName", safeName)
          if (safePhoto != null) put("photoUrl", safePhoto)
          put("role", assignedRole)
          put("createdAt", FieldValue.serverTimestamp())
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.set(userRef, profilePayload)
      } else {
        val updates = buildMap<String, Any> {
          put("displayName", safeName)
          if (safePhoto != null) put("photoUrl", safePhoto)
          put("role", assignedRole)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(userRef, updates)
      }

      batch.set(
        privateRef,
        mapOf(
          "userId" to uid,
          "email" to safeEmail,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()

      val fresh = userRef.get().await()
        .toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
        ?: UserProfile(
          id = uid,
          userId = uid,
          displayName = safeName,
          photoUrl = safePhoto,
          role = assignedRole,
        )
      Result.success(fresh)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  fun observeCurrentUser(uid: String): Flow<UserProfile?> {
    val path = "users/$uid"
    return db.collection("users").document(uid)
      .snapshots()
      .map { it.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logSnapshotError(error, OperationType.GET, path, uid)
        throw error
      }
  }

  fun observeUsers(): Flow<List<UserProfile>> {
    val uid = requireUserId()
    val path = "users"
    return db.collection("users")
      .whereIn("role", listOf(UserRole.ADMIN.value, UserRole.MEMBER.value))
      .snapshots()
      .map { snap ->
        snap.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.displayName.lowercase() }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeTeams(): Flow<List<Team>> {
    val uid = requireUserId()
    val path = "teams"
    return db.collection("teams")
      .whereEqualTo("workspaceId", WORKSPACE_DEFAULT)
      .snapshots()
      .map { snap ->
        snap.toObjects(Team::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.name.lowercase() }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeTargets(): Flow<List<Target>> {
    val uid = requireUserId()
    val path = "targets"
    return db.collection("targets")
      .whereEqualTo("workspaceId", WORKSPACE_DEFAULT)
      .snapshots()
      .map { snap ->
        snap.toObjects(Target::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.deadline }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeTemplates(): Flow<List<WorkflowTemplate>> {
    val uid = requireUserId()
    val path = "templates"
    return db.collection("templates")
      .whereEqualTo("workspaceId", WORKSPACE_DEFAULT)
      .snapshots()
      .map { snap ->
        snap.toObjects(WorkflowTemplate::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.name.lowercase() }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeTasks(): Flow<List<EditorialTask>> {
    val uid = requireUserId()
    val path = "tasks"
    return db.collection("tasks")
      .whereEqualTo("workspaceId", WORKSPACE_DEFAULT)
      .snapshots()
      .map { snap ->
        snap.toObjects(EditorialTask::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.dueDate }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeComments(taskId: String): Flow<List<TaskComment>> {
    val uid = requireUserId()
    val path = "tasks/$taskId/comments"
    return db.collection("tasks").document(taskId).collection("comments")
      .whereEqualTo("taskId", taskId)
      .snapshots()
      .map { snap ->
        snap.toObjects(TaskComment::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  fun observeAdminEmails(): Flow<List<AdminEmailEntry>> {
    val uid = requireUserId()
    val path = "admin_emails"
    return db.collection("admin_emails")
      .snapshots()
      .map { snap ->
        snap.toObjects(AdminEmailEntry::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedWith(compareByDescending<AdminEmailEntry> { it.isOwner }.thenBy { it.email })
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, path, uid)
        throw error
      }
  }

  suspend fun addAdminEmail(
    rawEmail: String,
    existingUserIds: List<String>,
  ): Result<Unit> {
    val uid = requireUserId()
    val normalized = rawEmail.trim().lowercase()
    if (!isValidAllowlistEmail(normalized)) {
      return Result.failure(IllegalArgumentException("Enter a valid Google email address."))
    }
    val docRef = db.collection("admin_emails").document(normalized)
    return try {
      if (!docRef.get().await().exists()) {
        docRef.set(
          mapOf(
            "email" to normalized,
            "addedBy" to uid,
            "isOwner" to (normalized == OWNER_ADMIN_EMAIL),
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      syncRoleForUsersWithEmail(normalized, UserRole.ADMIN, existingUserIds)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun removeAdminEmail(
    rawEmail: String,
    existingUserIds: List<String>,
  ): Result<Unit> {
    requireUserId()
    val normalized = rawEmail.trim().lowercase()
    if (normalized == OWNER_ADMIN_EMAIL) {
      return Result.failure(IllegalArgumentException("Primary owner admin email cannot be removed."))
    }
    val docRef = db.collection("admin_emails").document(normalized)
    return try {
      docRef.delete().await()
      syncRoleForUsersWithEmail(normalized, UserRole.MEMBER, existingUserIds)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  private suspend fun syncRoleForUsersWithEmail(
    normalizedEmail: String,
    targetRole: UserRole,
    existingUserIds: List<String>,
  ) {
    existingUserIds.distinct().forEach { memberUid ->
      try {
        val infoSnap = db.collection("users").document(memberUid)
          .collection("private").document("info").get().await()
        val memberEmail = infoSnap.getString("email")?.trim()?.lowercase().orEmpty()
        if (memberEmail == normalizedEmail) {
          db.collection("users").document(memberUid).update(
            mapOf(
              "role" to targetRole.value,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Best-effort sync for already-registered users; login bootstrap also enforces role
      }
    }
  }

  suspend fun updateUserRole(targetUid: String, role: UserRole): Result<Unit> {
    val actorUid = requireUserId()
    val ref = db.collection("users").document(targetUid)
    return try {
      val targetEmail = try {
        ref.collection("private").document("info").get().await()
          .getString("email")?.trim()?.lowercase().orEmpty()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        ""
      }

      if (role == UserRole.MEMBER && targetEmail == OWNER_ADMIN_EMAIL) {
        return Result.failure(IllegalArgumentException("Primary owner admin cannot be demoted."))
      }

      ref.update(
        mapOf(
          "role" to role.value,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()

      if (isValidAllowlistEmail(targetEmail) && !targetEmail.endsWith("@society.local")) {
        val allowRef = db.collection("admin_emails").document(targetEmail)
        if (role == UserRole.ADMIN) {
          if (!allowRef.get().await().exists()) {
            allowRef.set(
              mapOf(
                "email" to targetEmail,
                "addedBy" to actorUid,
                "isOwner" to (targetEmail == OWNER_ADMIN_EMAIL),
                "createdAt" to FieldValue.serverTimestamp(),
              ),
            ).await()
          }
        } else if (targetEmail != OWNER_ADMIN_EMAIL) {
          allowRef.delete().await()
        }
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun saveTeam(
    teamId: String?,
    name: String,
    description: String,
    leadId: String,
    memberIds: List<String>,
  ): Result<String> {
    val uid = requireUserId()
    val isCreate = teamId.isNullOrBlank()
    val ref = if (isCreate) db.collection("teams").document() else db.collection("teams").document(teamId!!)
    val cleanMembers = (memberIds + leadId).distinct().take(50)

    return try {
      if (isCreate) {
        ref.set(
          mapOf(
            "workspaceId" to WORKSPACE_DEFAULT,
            "name" to name.trim().take(80),
            "description" to description.trim().take(300),
            "leadId" to leadId,
            "memberIds" to cleanMembers,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        ref.update(
          mapOf(
            "name" to name.trim().take(80),
            "description" to description.trim().take(300),
            "leadId" to leadId,
            "memberIds" to cleanMembers,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteTeam(teamId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection("teams").document(teamId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun saveTargetWithWorkflow(
    targetId: String?,
    name: String,
    description: String,
    deadline: String,
    stages: List<WorkflowStage>,
    createStageTasks: Boolean,
    defaultAssigneeType: AssigneeType,
    defaultAssigneeId: String,
  ): Result<String> {
    val uid = requireUserId()
    val isCreate = targetId.isNullOrBlank()
    val targetRef = if (isCreate) {
      db.collection("targets").document()
    } else {
      db.collection("targets").document(targetId!!)
    }
    val resolvedStages = WorkflowScheduleGenerator.applyDeadlineToStages(stages, deadline)
      .take(30)
      .map { it.toMap() }

    return try {
      val batch = db.batch()
      if (isCreate) {
        batch.set(
          targetRef,
          mapOf(
            "workspaceId" to WORKSPACE_DEFAULT,
            "name" to name.trim().take(120),
            "description" to description.trim().take(500),
            "deadline" to deadline,
            "stages" to resolvedStages,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.update(
          targetRef,
          mapOf(
            "name" to name.trim().take(120),
            "description" to description.trim().take(500),
            "deadline" to deadline,
            "stages" to resolvedStages,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (createStageTasks) {
        val assigneeIdFinal = defaultAssigneeId.ifBlank { uid }
        val assigneeTypeFinal = if (defaultAssigneeId.isBlank()) AssigneeType.USER.value else defaultAssigneeType.value
        WorkflowScheduleGenerator.applyDeadlineToStages(stages, deadline).take(30).forEach { stage ->
          val taskRef = db.collection("tasks").document()
          batch.set(
            taskRef,
            mapOf(
              "workspaceId" to WORKSPACE_DEFAULT,
              "title" to "${stage.name}: ${name.trim().take(60)}".take(140),
              "description" to "Scheduled editorial milestone for ${name.trim()} (${stage.offsetDays}d from deadline).",
              "targetId" to targetRef.id,
              "stageName" to stage.name.take(80),
              "dueDate" to stage.dueDate,
              "priority" to if (stage.offsetDays >= -14L) TaskPriority.HIGH.value else TaskPriority.MEDIUM.value,
              "status" to TaskStatus.TODO.value,
              "assigneeType" to assigneeTypeFinal,
              "assigneeId" to assigneeIdFinal,
              "createdBy" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      batch.commit().await()
      Result.success(targetRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, targetRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteTarget(targetId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection("targets").document(targetId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun saveTemplate(
    templateId: String?,
    name: String,
    stages: List<WorkflowStage>,
  ): Result<String> {
    val uid = requireUserId()
    val isCreate = templateId.isNullOrBlank()
    val ref = if (isCreate) {
      db.collection("templates").document()
    } else {
      db.collection("templates").document(templateId!!)
    }
    val stageMaps = stages.take(30).map { stage ->
      mapOf(
        "id" to stage.id.ifBlank { java.util.UUID.randomUUID().toString().take(8) },
        "name" to stage.name.trim().take(80),
        "offsetDays" to stage.offsetDays,
        "dueDate" to "",
      )
    }

    return try {
      if (isCreate) {
        ref.set(
          mapOf(
            "workspaceId" to WORKSPACE_DEFAULT,
            "name" to name.trim().take(100),
            "stages" to stageMaps,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        ref.update(
          mapOf(
            "name" to name.trim().take(100),
            "stages" to stageMaps,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteTemplate(templateId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection("templates").document(templateId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun saveTask(
    taskId: String?,
    title: String,
    description: String,
    targetId: String,
    stageName: String,
    dueDate: String,
    priority: TaskPriority,
    status: TaskStatus,
    assigneeType: AssigneeType,
    assigneeId: String,
  ): Result<String> {
    val uid = requireUserId()
    val isCreate = taskId.isNullOrBlank()
    val ref = if (isCreate) db.collection("tasks").document() else db.collection("tasks").document(taskId!!)

    return try {
      if (isCreate) {
        ref.set(
          mapOf(
            "workspaceId" to WORKSPACE_DEFAULT,
            "title" to title.trim().take(140),
            "description" to description.trim().take(1000),
            "targetId" to targetId,
            "stageName" to stageName.trim().ifBlank { "General" }.take(80),
            "dueDate" to dueDate,
            "priority" to priority.value,
            "status" to status.value,
            "assigneeType" to assigneeType.value,
            "assigneeId" to assigneeId,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        ref.update(
          mapOf(
            "title" to title.trim().take(140),
            "description" to description.trim().take(1000),
            "targetId" to targetId,
            "stageName" to stageName.trim().ifBlank { "General" }.take(80),
            "dueDate" to dueDate,
            "priority" to priority.value,
            "status" to status.value,
            "assigneeType" to assigneeType.value,
            "assigneeId" to assigneeId,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun updateTaskStatus(taskId: String, newStatus: TaskStatus): Result<Unit> {
    requireUserId()
    val ref = db.collection("tasks").document(taskId)
    return try {
      ref.update(
        mapOf(
          "status" to newStatus.value,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteTask(taskId: String): Result<Unit> {
    requireUserId()
    val ref = db.collection("tasks").document(taskId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun addComment(taskId: String, authorName: String, body: String): Result<String> {
    val uid = requireUserId()
    val ref = db.collection("tasks").document(taskId).collection("comments").document()
    return try {
      ref.set(
        mapOf(
          "taskId" to taskId,
          "authorId" to uid,
          "authorName" to authorName.trim().ifBlank { "Member" }.take(80),
          "body" to body.trim().take(1000),
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  private fun logSnapshotError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    uid: String,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
  }

  companion object {
    const val OWNER_ADMIN_EMAIL = "mayeetreemilans@gmail.com"
    private val EMAIL_REGEX = Regex("""^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$""")

    fun isValidAllowlistEmail(email: String): Boolean =
      email.length in 5..160 && EMAIL_REGEX.matches(email)
  }
}
