# Community Health Center Queue Management System — Action Selector Edition

This is the **recoded Java Swing desktop system**, built from the HealthCenterSystem_Improved project you uploaded. The Patients screen replaces the four stacked action buttons with one **Select Action** dropdown and a single **APPLY ACTION** button. It is a fully compiled Java project, not an image-only mockup.

## Run on Windows

1. Extract the entire `HealthCenterSystem_ActionSelector.zip` archive.
2. Open the extracted `HealthCenterSystem_ActionSelector` folder.
3. Install a Java Runtime/JDK version 8 or newer if needed (JDK 17 or 21 recommended).
4. Double-click **RUN.bat**, or from Command Prompt in the folder run `java -jar HealthCenterSystem.jar`.
5. Sign in to the demonstration staff account:
   - Administrator: `admin` / `admin123`
   - Staff: `staff` / `staff123`
   - Public queue: from the sign-in screen, select **VIEW LIVE QUEUE**, without signing in.

If `RUN.bat` cannot find Java, run `java -version` in Command Prompt to check whether Java is installed and available on your PATH.

## Patients — new dropdown actions

Select an action, then click **APPLY ACTION**:

| Dropdown action | What it actually does |
|---|---|
| **Enqueue** | Type a new patient's name (plus optional contact, birthday, address); assign a permanent queue number, record arrival time, save the visit, and place the patient in line. The first patient automatically begins serving. |
| **Edit** | Select a row in the Patients table, then choose Edit and Apply Action to unlock its fields. The dropdown automatically switches to Update so you can save changes. |
| **Update** | After Edit, save the modified patient details; no new queue number is assigned. |
| **Done** | Select the **currently serving** patient and confirm. Mark consultation Completed, preserve its history, and automatically call the next waiting patient. |
| **Delete** | Select a mistakenly registered active visit; confirm removal. Status becomes **Removed** in Records, not Completed. |
| **Serve** | Call the selected **first waiting** patient, but only when no other patient is currently serving. Queue order cannot be skipped. |
| **Dequeue** | Select an active patient who leaves without completing a consultation. Confirm; remove them from the active queue and mark the record **Dequeued**. The completed count is **not** increased. If they were serving, the next patient is automatically called. |

**New / Clear** (the small control beside Select Action) clears the selection and form to begin another registration. It does not delete a record. A selected patient's form is read-only until **Edit** is applied, helping prevent accidental modifications.

## Preserved features

- Dashboard: live waiting, serving, completed today, checked in today, and current/next queue information.
- Patients: searchable CRUD table, name/contact validation, duplicate warning, automatic arrival time, birthday calendar with month/year selection.
- Queue Simulation: ordered patient line; Complete & Call Next, Place on Hold, Return to Line, Call Next controls. Queue number order and data are shared with the Patients action selector.
- Records: history including **Dequeued** status, date filters (day/week/month/year/custom dates), status filter, search, sorting, CSV export, print, and backup.
- Public view: no login, no identifying patient details, automatic refresh. Staff can launch the separate waiting-room display while managing visits.
- Automatic local saved data, backup and recovery.

## Existing saved records and backups

The original data model and saved-data file location are preserved, so existing local data **can be loaded** from earlier Improved Edition installations. The app stores its file at:

`C:\Users\YOUR_WINDOWS_USERNAME\queue_simulation_oop_data.ser`

with the adjacent `.ser.bak` backup. **Copy both to a separate safe folder before running this edition**, especially if you used an earlier version.

## Edit the Java source

The included `src/` folder is the full Java OOP project. Open in IntelliJ IDEA, NetBeans, VS Code with Java extensions, or JCreator. On Windows with a JDK installed, double-click **REBUILD.bat** to compile new changes and create a new JAR. Then double-click **RUN.bat**. On Linux/macOS you can use `sh build.sh && java -jar HealthCenterSystem.jar`.

## Testing

Passed under JDK 21 (compiled with Java 8 target):

- `QueueManagerTest`: validation, FIFO ordering, CRUD, holding, persistence.
- `BackupRecoveryTest`: backup and corrupted-file protections.
- `DequeueTest`: canceled active visits, automatic promotion, history, log and save.
- `ActionSelectorTest`: Swing Enqueue → Edit → Update flow and selected-row states.
- `GuiSmokeTest`: basic app startup, login, birthday picker, and public-only fields.
- `RecordsFilterTest`: historical day/month/year and status filters.

`ActionSelectorTest`, `GuiSmokeTest`, and `RecordsFilterTest` require a graphical environment to execute. `build.sh` runs command-line tests and rebuilds the JAR.

## Important: Demonstration use only

This program is a school/coursework prototype. Demo usernames/passwords are embedded, saved files and exported CSVs are **not encrypted**, and it is not designed for real clinical medical records or simultaneous multi-computer staff access. **Use fictional patient information only**. Production use would require proper authentication/authorization, privacy controls, data protection, and professional review.
