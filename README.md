# Journal Workflow

A native Android (Kotlin + Jetpack Compose + Material 3) editorial workflow application for academic journal societies, backed by **Firebase Authentication (Google Sign-In)** and **Cloud Firestore**.

## Core Capabilities

- **Google Sign-In & Admin Email Allowlist**: Uses Jetpack Credential Manager with Google Sign-In (`GetSignInWithGoogleOption`). The primary owner email (`mayeetreemilans@gmail.com`) and any email addresses added to `/admin_emails` in Firestore automatically sign in as **Admin** (and existing accounts are synced on sign-in); all other verified Google accounts sign in as **Member**.
- **Targets & Backward-Scheduled Workflow Generator**: Admins create publication targets (issues/volumes) with a deadline and auto-generate the 7 editorial stages (`Call for papers -60d`, `Peer review -45d`, `Author revisions -32d`, `Copyediting -22d`, `Layout & typesetting -14d`, `Proofreading -7d`, `Publish 0d`). Stages can be edited, added, removed, reordered, and saved as reusable templates.
- **Tasks, Teams & Threaded Comments**: Tasks can be assigned to an individual or a team (surfacing in every team member's **My Work** feed). One-tap status cycling (`To do` -> `In progress` -> `Done`) with colour-coded badges (`grey`, `amber`, `green`, `red` for overdue) and live task comments.
- **Dashboards & Native Compose Charts**:
  - **Segmented Progress Bar** per target
  - **Gantt-Style Timeline** per target with today marker
  - **Workload Bar Chart** per member (including team assignments)
- **Contextual Notifications & Multi-Faceted Search**: Notification bell badge appears when tasks assigned to you/your team are newly assigned or due within 3 days. Filter across target, assignee, team, status, and due date.

## Firebase Configuration

1. `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` configure the Firebase project and named Firestore database (`R.string.firestore_database_id`).
2. Every Firestore client uses `FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))`.
3. `firestore.rules` enforces:
   - Verified Google email allowlist (`/admin_emails/{email}`) + protected owner email (`mayeetreemilans@gmail.com`) for Admin privileges
   - Authenticated reads across society collections (`users`, `teams`, `targets`, `tasks`, `templates`, and `tasks/{taskId}/comments`)
   - PII isolation (`email` stored in `users/{uid}/private/info`, readable only by owner or Admin; `/admin_emails` listable only by Admins)
   - Admin-only writes on `admin_emails`, `targets`, `teams`, `templates`, task creation/deletion, and user role updates
   - Assignee/team-member updates restricted exclusively to `status` (+ `updatedAt`) and adding comments on assigned tasks.

## Local Testing

- **JavaScript rules tests**:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
- **Kotlin Robolectric rule & repository tests**:
  ```bash
  gradle :app:testDebugUnitTest --tests '*Rule*'
  ```

## Assumptions Made

1. **Native Android Stack**: Built in Kotlin, Jetpack Compose, Material 3, API-24-compatible ISO date scheduling, and custom Compose `Canvas` charts instead of React/Tailwind/Recharts to run natively on Android.
2. **Single Society Workspace**: All signed-in society members share a single workspace (`workspaceId = "default"`), matching "All signed-in users can read".
3. **One-Tap Status Cycling**: Tapping a task's status badge cycles `To do -> In progress -> Done -> To do`, allowing members to reopen a task if tapped accidentally.
4. **Notification Window**: "New assignments" highlights `To do` tasks created within the past 48 hours alongside open tasks due within 3 days or already overdue for the signed-in user or their teams.
