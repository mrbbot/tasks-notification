package com.mrbbot.taskification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ForegroundService extends Service implements Runnable {
    private static final String TAG = "TaskificationService";
    private static final String NOTIFICATION_CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String SP_LIST_ID_KEY = "list_id";
    public static final String SP_LIST_TITLE_KEY = "list_title";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received start command with action " + action);
            if (Actions.STOP.name().equals(action)) {
                stopService();
                return START_STICKY;
            }
        } else {
            Log.d(TAG, "Received start command without action");
        }
        startService();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating...");
        startForeground(NOTIFICATION_ID, createNotification(null,null));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying...");
        stopService();
    }

    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> scheduledFuture;

    private void startService() {
        Log.d(TAG, "Starting...");
        if (executorService == null && scheduledFuture == null) {
            executorService = Executors.newSingleThreadScheduledExecutor();
            scheduledFuture = executorService.scheduleWithFixedDelay(this, 120, 120, TimeUnit.SECONDS);
        }
        if(executorService != null) {
            executorService.schedule(this, 0, TimeUnit.SECONDS);
        }
    }

    private void stopService() {
        Log.d(TAG, "Stopping...");
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        stopSelf();
    }

    @Override
    public void run() {
        try {
            SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE);
            String listId = prefs.getString(SP_LIST_ID_KEY, null);
            String listTitle = prefs.getString(SP_LIST_TITLE_KEY, null);
            if(listId == null) return;
            List<TasksAPI.Task> tasks = TasksAPI.getTasks(this, listId);
            NotificationManager manager = ContextCompat.getSystemService(this, NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification(listTitle, tasks));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting tasks: " + e.getMessage());
        }
    }

    private Notification createNotification(@Nullable String listTitle, @Nullable List<TasksAPI.Task> tasks) {
        Log.d(TAG, "Displaying notification...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Tasks", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Tasks");
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            Objects.requireNonNull(manager).createNotificationChannel(channel);
        }

        Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.tasks");
        if(intent == null) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.tasks"));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        StringBuilder contentText = new StringBuilder();
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        if (tasks != null) {
            for (TasksAPI.Task task : tasks) {
                if(contentText.length() > 0) {
                    contentText.append(", ");
                }
                contentText.append(task.title);

                inboxStyle.addLine(task.spannable);
                for (TasksAPI.Task subTask : task.subTasks) {
                    inboxStyle.addLine(subTask.spannable);
                }
            }
        }
        if(contentText.length() == 0) {
            contentText.append("No Tasks");
            inboxStyle.addLine("No Tasks");
        }

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSubText(listTitle)
                .setContentText(contentText.toString())
                .setSmallIcon(R.drawable.ic_stat_name)
                .setStyle(inboxStyle)
                .setContentIntent(pendingIntent)
                .build();
    }
}
