package com.deerflow.mobile.run

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deerflow.mobile.MainActivity
import com.deerflow.mobile.R
import com.deerflow.mobile.data.GatewayRunStatus
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.SettingsStore
import com.deerflow.mobile.data.drawableResId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ManagedRunNotification(
    val key: RunKey,
    val title: String,
    val progress: RunProgressUpdate,
    val startedAtEpochMs: Long,
    val error: String?,
    val revision: Long,
)

private data class ManagedRunSnapshot(
    val active: List<ManagedRunNotification> = emptyList(),
    val terminal: ManagedRunNotification? = null,
)

private fun ManagedRunNotification.toBundle(): Bundle = Bundle().apply {
    putString(MANAGED_SERVER_URL, key.serverUrl)
    putString(MANAGED_THREAD_ID, key.threadId)
    putString(MANAGED_TITLE, title)
    putString(MANAGED_PHASE, progress.phase.name)
    putInt(MANAGED_COMPLETED_TODOS, progress.completedTodos)
    putInt(MANAGED_TOTAL_TODOS, progress.totalTodos)
    putString(MANAGED_CURRENT_TODO, progress.currentTodo)
    putString(MANAGED_LATEST_TOOL, progress.latestToolName)
    putLong(MANAGED_STARTED_AT, startedAtEpochMs)
    putString(MANAGED_ERROR, error)
    putLong(MANAGED_REVISION, revision)
}

private fun Bundle.toManagedRunNotification(): ManagedRunNotification? {
    val serverUrl = getString(MANAGED_SERVER_URL).orEmpty()
    val threadId = getString(MANAGED_THREAD_ID).orEmpty()
    val phase = getString(MANAGED_PHASE)?.let { value ->
        runCatching { RunProgress.valueOf(value) }.getOrNull()
    } ?: return null
    if (serverUrl.isBlank() || threadId.isBlank()) return null
    return ManagedRunNotification(
        key = RunKey(serverUrl, threadId),
        title = getString(MANAGED_TITLE).orEmpty(),
        progress = RunProgressUpdate(
            phase = phase,
            completedTodos = getInt(MANAGED_COMPLETED_TODOS),
            totalTodos = getInt(MANAGED_TOTAL_TODOS),
            currentTodo = getString(MANAGED_CURRENT_TODO),
            latestToolName = getString(MANAGED_LATEST_TOOL),
        ),
        startedAtEpochMs = getLong(MANAGED_STARTED_AT),
        error = getString(MANAGED_ERROR),
        revision = getLong(MANAGED_REVISION),
    )
}

@Suppress("DEPRECATION")
private fun Intent.managedRunSnapshotOrNull(): ManagedRunSnapshot? {
    if (!getBooleanExtra(MANAGED_SNAPSHOT_PRESENT, false)) return null
    val activeBundles = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(MANAGED_ACTIVE_LIST, Bundle::class.java)
    } else {
        getParcelableArrayListExtra(MANAGED_ACTIVE_LIST)
    }.orEmpty()
    val terminalBundle = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(MANAGED_TERMINAL_ITEM, Bundle::class.java)
    } else {
        getParcelableExtra(MANAGED_TERMINAL_ITEM)
    }
    return ManagedRunSnapshot(
        active = activeBundles.mapNotNull(Bundle::toManagedRunNotification),
        terminal = terminalBundle?.toManagedRunNotification(),
    )
}

private fun CoordinatedRunState.toManagedNotification(): ManagedRunNotification = ManagedRunNotification(
    key = key,
    title = title,
    progress = runProgressUpdate(
        phase = when (run.status) {
            RunStatus.Connecting -> RunProgress.Connecting
            RunStatus.Streaming -> RunProgress.Working
            RunStatus.Reconnecting -> RunProgress.Reconnecting
            RunStatus.Stopping -> RunProgress.Finalizing
            RunStatus.Idle, RunStatus.AwaitingInput, RunStatus.Failed -> RunProgress.Completed
        },
        todos = todos,
        latestToolName = latestToolName,
    ),
    startedAtEpochMs = run.startedAtEpochMs ?: 0L,
    error = error ?: when (run.gatewayStatus) {
        GatewayRunStatus.Error -> "The run failed."
        GatewayRunStatus.Timeout -> "The run timed out."
        GatewayRunStatus.Interrupted -> "The run was interrupted."
        else -> null
    },
    revision = revision,
)

class RunService : Service() {
    private var title: String = ""
    private var progress = RunProgressUpdate(RunProgress.Preparing)
    private var serverUrl: String? = null
    private var threadId: String? = null
    private var notificationSurfaceColor: Int = 0xFFDCE8D8.toInt()
    private var notificationAccentColor: Int = 0xFF234D37.toInt()
    private var terminalSmallIconRes: Int = R.drawable.ic_notification_completed
    private var liveUpdateDismissed = false
    private var lastPublishedSignature: String? = null
    private var lastNotificationProjection: RunNotificationProjection? = null
    private var lastPublishedAtMs: Long = 0
    private var pendingPublish: Job? = null
    private var showStopAction = true
    private var managedDetail: String? = null
    private var managedActiveKeys: Set<RunKey> = emptySet()
    private var serviceManagedSnapshot = ManagedRunSnapshot()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.run_channel), NotificationManager.IMPORTANCE_LOW),
        )
        // Keep the notification palette in lockstep with the adaptive launcher icon.
        notificationSurfaceColor = getColor(R.color.ic_launcher_background)
        notificationAccentColor = getColor(R.color.ic_launcher_foreground)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNCHRONIZE -> {
                serviceManagedSnapshot = intent.managedRunSnapshotOrNull() ?: ManagedRunSnapshot()
                applyManagedSnapshot(startId, serviceManagedSnapshot)
                return if (serviceManagedSnapshot.active.isEmpty()) START_NOT_STICKY else START_STICKY
            }
            ACTION_STOP -> {
                val requestedKey = intent.runKeyOrNull() ?: serviceManagedSnapshot.active.singleOrNull()?.key
                serviceScope.launch {
                    if (requestedKey != null) {
                        RunCoordinator.get(applicationContext).cancel(requestedKey)
                    } else {
                        removeNotification()
                        stopSelf(startId)
                    }
                }
                return START_STICKY
            }
            ACTION_UPDATE -> {
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { title }.ifBlank { getString(R.string.run_in_progress) }
                val phase = intent.getStringExtra(EXTRA_PHASE)?.let { value ->
                    runCatching { RunProgress.valueOf(value) }.getOrNull()
                } ?: progress.phase
                progress = RunProgressUpdate(
                    phase = phase,
                    completedTodos = intent.getIntExtra(EXTRA_COMPLETED_TODOS, 0),
                    totalTodos = intent.getIntExtra(EXTRA_TOTAL_TODOS, 0),
                    currentTodo = intent.getStringExtra(EXTRA_CURRENT_TODO),
                    latestToolName = intent.getStringExtra(EXTRA_LATEST_TOOL_NAME),
                )
                publish(ongoing = true)
            }
            ACTION_COMPLETE -> {
                if (serviceManagedSnapshot.active.isNotEmpty()) {
                    applyManagedSnapshot(startId, serviceManagedSnapshot)
                    return START_STICKY
                }
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { title }
                progress = progress.copy(phase = RunProgress.Completed, currentTodo = null)
                terminalSmallIconRes = R.drawable.ic_notification_completed
                finish(getString(R.string.run_completed))
                return START_NOT_STICKY
            }
            ACTION_FAILED -> {
                if (serviceManagedSnapshot.active.isNotEmpty()) {
                    applyManagedSnapshot(startId, serviceManagedSnapshot)
                    return START_STICKY
                }
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { title }
                terminalSmallIconRes = android.R.drawable.ic_dialog_alert
                finish(intent.getStringExtra(EXTRA_DETAIL).orEmpty().ifBlank { getString(R.string.run_failed) })
                return START_NOT_STICKY
            }
            ACTION_DISMISSED -> {
                // The foreground service still needs a notification, but no longer requests Live Update promotion.
                liveUpdateDismissed = true
                publish(ongoing = true, force = true)
                return START_STICKY
            }
            else -> {
                if (intent == null) {
                    serviceScope.launch {
                        val recovered = runCatching { recover(applicationContext) }.getOrDefault(false)
                        if (!recovered) {
                            removeNotification()
                            stopSelf(startId)
                        }
                    }
                    return START_NOT_STICKY
                }
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.run_in_progress) }
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty().ifBlank { serverUrl }
                threadId = intent.getStringExtra(EXTRA_THREAD_ID).orEmpty().ifBlank { threadId }
                progress = RunProgressUpdate(RunProgress.Preparing)
                managedDetail = null
                showStopAction = true
                liveUpdateDismissed = false
                publish(ongoing = true)
            }
        }
        return START_STICKY
    }

    private fun applyManagedSnapshot(startId: Int, snapshot: ManagedRunSnapshot) {
        val active = snapshot.active
        if (active.isNotEmpty()) {
            val activeKeys = active.mapTo(mutableSetOf(), ManagedRunNotification::key)
            val topologyChanged = activeKeys != managedActiveKeys
            managedActiveKeys = activeKeys
            val focused = active.maxByOrNull(ManagedRunNotification::startedAtEpochMs) ?: return
            title = if (active.size == 1) focused.title else getString(R.string.run_in_progress)
            progress = focused.progress
            serverUrl = focused.key.serverUrl
            threadId = focused.key.threadId
            managedDetail = if (active.size == 1) null else getString(R.string.run_count_in_progress, active.size)
            showStopAction = active.size == 1
            terminalSmallIconRes = R.drawable.ic_notification_completed
            publish(ongoing = true, detail = managedDetail, force = topologyChanged)
            return
        }

        managedActiveKeys = emptySet()
        val terminal = snapshot.terminal
        if (terminal != null) {
            title = terminal.title
            progress = terminal.progress.copy(phase = RunProgress.Completed, currentTodo = null)
            serverUrl = terminal.key.serverUrl
            threadId = terminal.key.threadId
            managedDetail = null
            showStopAction = false
            terminalSmallIconRes = if (terminal.error == null) {
                R.drawable.ic_notification_completed
            } else {
                android.R.drawable.ic_dialog_alert
            }
            finish(terminal.error ?: getString(R.string.run_completed))
        } else {
            removeNotification()
            stopSelf(startId)
        }
    }

    private fun Intent.runKeyOrNull(): RunKey? {
        val server = getStringExtra(EXTRA_SERVER_URL).orEmpty()
        val thread = getStringExtra(EXTRA_THREAD_ID).orEmpty()
        return if (server.isBlank() || thread.isBlank()) null else RunKey(server, thread)
    }

    private fun publish(ongoing: Boolean, detail: String? = null, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val projection = progress.notificationProjection()
        val signature = listOf(ongoing, detail.orEmpty(), projection, liveUpdateDismissed).joinToString("|")
        val remainingInterval = NOTIFICATION_UPDATE_INTERVAL_MS - (now - lastPublishedAtMs)
        if (
            ongoing &&
            !force &&
            lastNotificationProjection != null &&
            lastNotificationProjection != projection &&
            remainingInterval > 0
        ) {
            pendingPublish?.cancel()
            pendingPublish = serviceScope.launch {
                delay(remainingInterval)
                pendingPublish = null
                publish(ongoing = true, detail = managedDetail)
            }
            return
        }
        if (ongoing && !shouldPublishOngoingNotification(lastNotificationProjection, projection, lastPublishedAtMs, now, force)) return
        val openIntent = PendingIntent.getActivity(
            this,
            threadId?.hashCode() ?: 0,
            MainActivity.runDestinationIntent(this, serverUrl, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = if (showStopAction) {
            PendingIntent.getService(
                this,
                (threadId?.hashCode() ?: 0) xor STOP_REQUEST_CODE_SALT,
                Intent(this, RunService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_SERVER_URL, serverUrl)
                    .putExtra(EXTRA_THREAD_ID, threadId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        val dismissIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RunService::class.java).setAction(ACTION_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = if (ongoing && Build.VERSION.SDK_INT >= 36 && shouldUseLiveUpdate()) {
            buildLiveUpdate(openIntent, stopIntent, dismissIntent, ongoing, detail)
        } else if (ongoing) {
            buildCompatNotification(openIntent, stopIntent, ongoing, detail)
        } else {
            buildTerminalNotification(openIntent, detail)
        }
        if (ongoing) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
        lastPublishedSignature = signature
        lastNotificationProjection = projection
        lastPublishedAtMs = now
        pendingPublish?.cancel()
        pendingPublish = null
    }

    private fun shouldUseLiveUpdate(): Boolean {
        if (Build.VERSION.SDK_INT < 36 || liveUpdateDismissed) return false
        return getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    }

    @RequiresApi(36)
    private fun buildLiveUpdate(
        openIntent: PendingIntent,
        stopIntent: PendingIntent?,
        dismissIntent: PendingIntent,
        ongoing: Boolean,
        detail: String?,
    ): Notification = buildProgressNotification(
        openIntent = openIntent,
        stopIntent = stopIntent,
        dismissIntent = dismissIntent,
        ongoing = ongoing,
        detail = detail,
        requestPromotion = true,
        iconRes = statusChipSmallIconRes(),
    )

    @RequiresApi(36)
    private fun buildProgressNotification(
        openIntent: PendingIntent,
        stopIntent: PendingIntent?,
        dismissIntent: PendingIntent?,
        ongoing: Boolean,
        detail: String?,
        requestPromotion: Boolean,
        iconRes: Int,
    ): Notification {
        val indeterminate = progress.usesIndeterminateNotificationProgress(ongoing)
        val style = Notification.ProgressStyle()
            .setProgressIndeterminate(indeterminate)
            .addProgressSegment(Notification.ProgressStyle.Segment(100).setColor(notificationAccentColor))
            .apply {
                if (!indeterminate) {
                    setProgress(this@RunService.progress.percent)
                    setStyledByProgress(true)
                    // Oplus inserts a default tracker when none is supplied. An explicit
                    // transparent tracker keeps the progress bar free of a right-side glyph.
                    setProgressTrackerIcon(
                        Icon.createWithResource(
                            this@RunService,
                            R.drawable.ic_notification_transparent,
                        ),
                    )
                }
                if (!ongoing && iconRes == R.drawable.ic_notification_completed) {
                    setProgressEndIcon(Icon.createWithResource(this@RunService, iconRes))
                }
            }
        return Notification.Builder(this, CHANNEL_ID)
            // The small icon is what Android renders at the left of a Live Update status chip.
            .setSmallIcon(iconRes)
            .setContentTitle(title.ifBlank { getString(R.string.run_in_progress) })
            .setContentText(detail ?: progressLabel())
            .setContentIntent(openIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(
                if (ongoing) Notification.FOREGROUND_SERVICE_IMMEDIATE else Notification.FOREGROUND_SERVICE_DEFAULT,
            )
            // A Live Update may use an accent color, but must not be colorized.
            .setColor(notificationSurfaceColor)
            .setShortCriticalText(statusChip())
            .setStyle(style)
            .apply {
                if (requestPromotion) {
                    addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                }
                dismissIntent?.let(::setDeleteIntent)
                stopIntent?.let {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@RunService, android.R.drawable.ic_media_pause),
                            getString(R.string.stop_run),
                            it,
                        ).build(),
                    )
                }
            }
            .build()
    }

    private fun buildCompatNotification(
        openIntent: PendingIntent,
        stopIntent: PendingIntent?,
        ongoing: Boolean,
        detail: String?,
    ): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(statusChipSmallIconRes())
            .setContentTitle(title.ifBlank { getString(R.string.run_in_progress) })
            .setContentText(detail ?: progressLabel())
            .setContentIntent(openIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(
                if (ongoing) {
                    NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                } else {
                    NotificationCompat.FOREGROUND_SERVICE_DEFAULT
                },
            )
            .setColor(notificationSurfaceColor)
            .setProgress(
                if (progress.indeterminate) 0 else 100,
                if (progress.indeterminate) 0 else progress.percent,
                progress.indeterminate,
            )
            .apply { if (ongoing && stopIntent != null) addAction(0, getString(R.string.stop_run), stopIntent) }
            .build()

    private fun buildTerminalNotification(openIntent: PendingIntent, detail: String?): Notification {
        if (Build.VERSION.SDK_INT >= 36) {
            return buildProgressNotification(
                openIntent = openIntent,
                stopIntent = null,
                dismissIntent = null,
                ongoing = false,
                detail = detail,
                requestPromotion = false,
                iconRes = terminalSmallIconRes,
            )
        }
        val terminalTitle = title.ifBlank { getString(R.string.run_in_progress) }
        val terminalDetail = detail ?: progressLabel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(terminalSmallIconRes)
            .setContentTitle(terminalTitle)
            .setContentText(terminalDetail)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(notificationSurfaceColor)
            .setProgress(100, progress.percent, false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(terminalDetail))
            .build()
    }

    private fun phaseLabel(progress: RunProgress): Int = when (progress) {
        RunProgress.Preparing -> R.string.run_preparing
        RunProgress.Uploading -> R.string.run_uploading
        RunProgress.Connecting -> R.string.run_connecting
        RunProgress.Working, RunProgress.Responding -> R.string.run_running
        RunProgress.Reconnecting -> R.string.run_reconnecting
        RunProgress.Finalizing -> R.string.run_finalizing
        RunProgress.Completed -> R.string.run_completed
    }

    private fun progressLabel(): String = if (progress.indeterminate) {
        progress.currentTodo?.let { getString(R.string.run_current_step, it) }
            ?: getString(phaseLabel(progress.phase))
    } else {
        progress.currentTodo?.let { getString(R.string.run_current_step, it) }
            ?: getString(R.string.run_plan_progress, progress.completedTodos, progress.totalTodos)
    }

    private fun statusChip(): String = progress.todoChip ?: getString(
        when (progress.phase) {
            RunProgress.Preparing -> R.string.run_chip_preparing
            RunProgress.Uploading -> R.string.run_chip_uploading
            RunProgress.Connecting -> R.string.run_chip_connecting
            RunProgress.Working -> R.string.run_chip_working
            RunProgress.Responding -> R.string.run_chip_responding
            RunProgress.Reconnecting -> R.string.run_chip_reconnecting
            RunProgress.Finalizing -> R.string.run_chip_finalizing
            RunProgress.Completed -> R.string.run_chip_completed
        },
    )

    private fun statusChipSmallIconRes(): Int = when (val icon = progress.notificationIcon()) {
        RunNotificationIcon.Thinking -> R.drawable.ic_notification_thinking
        is RunNotificationIcon.Tool -> icon.icon.drawableResId()
        RunNotificationIcon.Upload -> android.R.drawable.stat_sys_upload
        RunNotificationIcon.Reconnect -> R.drawable.ic_notification_thinking
        RunNotificationIcon.Completed -> R.drawable.ic_notification_completed
    }

    private fun finish(detail: String) {
        serviceScope.launch {
            pendingPublish?.cancel()
            pendingPublish = null
            if (SettingsStore(this@RunService).read().notifyOnRunCompletion) {
                // A completed activity can no longer remain a promoted Live Update, but keeping
                // this notification attached through the foreground teardown prevents the visual
                // jump to a separate generic completion card.
                stopForeground(STOP_FOREGROUND_DETACH)
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    buildTerminalNotification(
                        PendingIntent.getActivity(
                            this@RunService,
                            threadId?.hashCode() ?: 0,
                            MainActivity.runDestinationIntent(this@RunService, serverUrl, threadId),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                        detail,
                    ),
                )
            } else {
                removeNotification()
            }
            lastPublishedSignature = null
            lastNotificationProjection = null
            lastPublishedAtMs = 0
            stopSelf()
        }
    }

    private fun removeNotification() {
        pendingPublish?.cancel()
        pendingPublish = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        lastPublishedSignature = null
        lastNotificationProjection = null
        lastPublishedAtMs = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "deerflow-runs"
        private const val NOTIFICATION_ID = 2026
        private const val STOP_REQUEST_CODE_SALT = 0x51A7
        private const val ACTION_STOP = "com.deerflow.mobile.action.STOP_RUN"
        private const val ACTION_SYNCHRONIZE = "com.deerflow.mobile.action.SYNCHRONIZE_RUNS"
        private const val ACTION_UPDATE = "com.deerflow.mobile.action.UPDATE_RUN"
        private const val ACTION_COMPLETE = "com.deerflow.mobile.action.COMPLETE_RUN"
        private const val ACTION_FAILED = "com.deerflow.mobile.action.FAILED_RUN"
        internal const val ACTION_DISMISSED = "com.deerflow.mobile.action.DISMISS_RUN_LIVE_UPDATE"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_SERVER_URL = "server_url"
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val EXTRA_COMPLETED_TODOS = "completed_todos"
        private const val EXTRA_TOTAL_TODOS = "total_todos"
        private const val EXTRA_CURRENT_TODO = "current_todo"
        private const val EXTRA_LATEST_TOOL_NAME = "latest_tool_name"
        internal const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        fun synchronize(context: Context, states: Map<RunKey, CoordinatedRunState>) {
            val notifications = states.values.map(CoordinatedRunState::toManagedNotification)
            val snapshot = ManagedRunSnapshot(
                active = notifications.filter { states[it.key]?.run?.active == true },
                terminal = notifications
                    .filter {
                        states[it.key]?.run?.let { run ->
                            !run.active && !run.awaitingInput
                        } == true
                    }
                    .maxWithOrNull(compareBy<ManagedRunNotification> { it.revision }.thenBy { it.startedAtEpochMs }),
            )
            val intent = Intent(context, RunService::class.java)
                .setAction(ACTION_SYNCHRONIZE)
                .putExtra(MANAGED_SNAPSHOT_PRESENT, true)
                .putParcelableArrayListExtra(
                    MANAGED_ACTIVE_LIST,
                    ArrayList(snapshot.active.map(ManagedRunNotification::toBundle)),
                )
                .putExtra(MANAGED_TERMINAL_ITEM, snapshot.terminal?.toBundle())
            if (snapshot.active.isNotEmpty()) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context, title: String, serverUrl: String? = null, threadId: String? = null) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RunService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_SERVER_URL, serverUrl)
                    .putExtra(EXTRA_THREAD_ID, threadId),
            )
        }

        fun update(context: Context, update: RunProgressUpdate, title: String? = null) {
            context.startService(
                Intent(context, RunService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_PHASE, update.phase.name)
                    .putExtra(EXTRA_COMPLETED_TODOS, update.completedTodos)
                    .putExtra(EXTRA_TOTAL_TODOS, update.totalTodos)
                    .putExtra(EXTRA_CURRENT_TODO, update.currentTodo)
                    .putExtra(EXTRA_LATEST_TOOL_NAME, update.latestToolName)
                    .putExtra(EXTRA_TITLE, title),
            )
        }

        fun update(context: Context, phase: RunProgress, title: String? = null) =
            update(context, RunProgressUpdate(phase), title)

        fun complete(context: Context, title: String? = null) {
            context.startService(
                Intent(context, RunService::class.java)
                    .setAction(ACTION_COMPLETE)
                    .putExtra(EXTRA_TITLE, title),
            )
        }

        fun fail(context: Context, detail: String, title: String? = null) {
            context.startService(
                Intent(context, RunService::class.java)
                    .setAction(ACTION_FAILED)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_DETAIL, detail),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        suspend fun recover(context: Context, serverUrl: String? = null): Boolean =
            RunCoordinator.get(context).recoverAll(serverUrl)
    }
}

private const val MANAGED_SERVER_URL = "server_url"
private const val MANAGED_SNAPSHOT_PRESENT = "managed_snapshot"
private const val MANAGED_ACTIVE_LIST = "managed_active"
private const val MANAGED_TERMINAL_ITEM = "managed_terminal"
private const val MANAGED_THREAD_ID = "thread_id"
private const val MANAGED_TITLE = "title"
private const val MANAGED_PHASE = "phase"
private const val MANAGED_COMPLETED_TODOS = "completed_todos"
private const val MANAGED_TOTAL_TODOS = "total_todos"
private const val MANAGED_CURRENT_TODO = "current_todo"
private const val MANAGED_LATEST_TOOL = "latest_tool"
private const val MANAGED_STARTED_AT = "started_at"
private const val MANAGED_ERROR = "error"
private const val MANAGED_REVISION = "revision"
