package com.igm.sftp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(intent.getAction())) {
            if (!SftpUploadService.isEnabled(context)) return;
            // Android 15 does not allow BOOT_COMPLETED to directly start a dataSync FGS.
            // The persisted configuration remains available and the app will resume it when appropriate.
        }
    }
}
