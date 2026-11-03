package com.igm.sftp;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

public class SftpBackground extends CordovaPlugin {
    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";
    private static final String ACTION_STATUS = "status";
    private static final String ACTION_LOGS = "logs";
    private static final String ACTION_CLEAR_LOGS = "clearLogs";

    @Override public boolean execute(String action, JSONArray args, CallbackContext cb) {
        try {
            Context ctx = cordova.getContext().getApplicationContext();
            if (ACTION_START.equals(action)) {
                JSONObject config = args.length() > 0 && !args.isNull(0) ? args.getJSONObject(0) : new JSONObject();
                SftpUploadService.enable(ctx, config.toString());
                Intent i = new Intent(ctx, SftpUploadService.class);
                i.setAction(ACTION_START);
                i.putExtra("config", config.toString());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i); else ctx.startService(i);
                cb.success(); return true;
            }
            if (ACTION_STOP.equals(action)) {
                SftpUploadService.disable(ctx);
                ctx.stopService(new Intent(ctx, SftpUploadService.class));
                cb.success(); return true;
            }
            if (ACTION_STATUS.equals(action)) {
                JSONObject r = new JSONObject();
                r.put("running", SftpUploadService.isRunning());
                r.put("enabled", SftpUploadService.isEnabled(ctx));
                cb.success(r); return true;
            }
            if (ACTION_LOGS.equals(action)) { cb.success(new JSONArray(SftpUploadService.getLogs(ctx))); return true; }
            if (ACTION_CLEAR_LOGS.equals(action)) { SftpUploadService.clearLogs(ctx); cb.success(); return true; }
            cb.error("Acción no soportada: " + action); return true;
        } catch (Exception e) { cb.error(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())); return true; }
    }
}
