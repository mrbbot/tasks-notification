package com.mrbbot.taskification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class StartReceiver extends BroadcastReceiver {
    private static final String TAG = "TaskificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // start service on boot
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Starting service...");
            Intent serviceIntent = new Intent(context, ForegroundService.class);
            serviceIntent.setAction(Actions.START.name());
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
