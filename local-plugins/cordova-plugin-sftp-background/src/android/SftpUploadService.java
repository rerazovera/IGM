package com.igm.sftp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.documentfile.provider.DocumentFile;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;

public class SftpUploadService extends Service {
    private static final String CHANNEL_ID = "igm_sftp";
    private static final int NOTIFICATION_ID = 7001;
    private static final String PREFS = "igm_sftp";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 10;
    private static volatile boolean running = false;
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IGM:SftpUpload");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("Servicio SFTP activo"));
        if (intent != null && intent.hasExtra("config")) {
            String config = intent.getStringExtra("config");
            if (config != null) saveConfig(config);
        }
        if (!isEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running && executor != null && !executor.isShutdown()) {
            final String config = getSavedConfig();
            executor.submit(() -> runForever(config));
        }
        return START_STICKY;
    }

    private void runForever(String raw) {
        running = true;
        try {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            JSONObject config = new JSONObject(raw == null ? "{}" : raw);
            while (!Thread.currentThread().isInterrupted() && isEnabled()) {
                long started = System.currentTimeMillis();
                int uploaded = 0;
                int skipped = 0;
                String result = "OK";
                String detail = "";
                try {
                    if (hasInternet()) {
                        updateNotification("Conectando al SFTP...");
                        int[] counts = sendDirectories(config);
                        uploaded = counts[0];
                        skipped = counts[1];
                        detail = "Enviados: " + uploaded + ", omitidos: " + skipped;
                        updateNotification("Sincronización OK · " + detail);
                    } else {
                        result = "SIN INTERNET";
                        detail = "Sin conexión a Internet";
                        updateNotification("Sin Internet; reintentando en 30 s");
                    }
                } catch (Exception e) {
                    result = "ERROR";
                    detail = safeMessage(e);
                    updateNotification("Error SFTP: " + detail);
                }
                addLog(result, detail, uploaded, skipped, System.currentTimeMillis() - started);
                try {
                    Thread.sleep(30000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            addLog("ERROR SERVICIO", safeMessage(e), 0, 0, 0);
            updateNotification("Error del servicio: " + safeMessage(e));
        } finally {
            running = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network n = cm.getActiveNetwork();
            return n != null && cm.getNetworkCapabilities(n) != null;
        }
        android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private int[] sendDirectories(JSONObject c) throws Exception {
        String host = c.getString("host");
        int port = c.optInt("port", 22);
        String user = c.getString("user");
        String password = c.getString("password");
        String remoteImages = c.optString("remoteImages", "/home/rerazo/IGM/imagenes");
        String remoteFiles = c.optString("remoteFiles", "/home/rerazo/IGM/archivos");
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(15000);
        ChannelSftp sftp = (ChannelSftp)session.openChannel("sftp");
        sftp.connect(15000);
        Counter counter = new Counter();
        try {
            ensureRemoteDirectory(sftp, remoteImages);
            ensureRemoteDirectory(sftp, remoteFiles);
            uploadTree(sftp, c.optString("directory_origen", null), remoteImages, counter);
            uploadTree(sftp, c.optString("directory_destino", null), remoteFiles, counter);
        } finally {
            if (sftp.isConnected()) sftp.disconnect();
            if (session.isConnected()) session.disconnect();
        }
        return new int[]{counter.uploaded, counter.skipped};
    }

    private static class Counter { int uploaded = 0; int skipped = 0; }

    private void uploadTree(ChannelSftp sftp, String uriString, String remoteRoot, Counter counter) throws Exception {
        if (uriString == null || uriString.isEmpty()) return;
        DocumentFile root = DocumentFile.fromTreeUri(this, android.net.Uri.parse(uriString));
        if (root == null || !root.isDirectory() || !root.canRead()) {
            throw new SecurityException("No se puede leer el directorio SAF: " + uriString);
        }
        uploadChildren(sftp, root, remoteRoot, counter);
    }

    private void uploadChildren(ChannelSftp sftp, DocumentFile dir, String remoteDir, Counter counter) throws Exception {
        ensureRemoteDirectory(sftp, remoteDir);
        for (DocumentFile item : dir.listFiles()) {
            if (item.isDirectory()) {
                uploadChildren(sftp, item, remoteDir + "/" + safeName(item.getName()), counter);
            } else if (item.isFile()) {
                String name = safeName(item.getName());
                uploadFile(sftp, item, remoteDir + "/" + name, counter);
            }
        }
    }

    private void uploadFile(ChannelSftp sftp, DocumentFile item, String remote, Counter counter) throws Exception {
        long localSize = item.length();
        long remoteSize = -1;
        try { remoteSize = sftp.stat(remote).getSize(); } catch (Exception ignored) {}
        if (remoteSize >= 0 && remoteSize == localSize) { counter.skipped++; return; }
        try (InputStream in = getContentResolver().openInputStream(item.getUri())) {
            if (in == null) throw new java.io.IOException("No se pudo abrir: " + item.getUri());
            sftp.put(in, remote, ChannelSftp.OVERWRITE);
            counter.uploaded++;
        }
    }

    private void ensureRemoteDirectory(ChannelSftp sftp, String path) throws Exception {
        if (path == null || path.isEmpty() || ".".equals(path)) return;
        String normalized = path.replace("\\", "/");
        String[] parts = normalized.split("/");
        String current = normalized.startsWith("/") ? "/" : "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!current.endsWith("/")) current += "/";
            current += part;
            try { sftp.cd(current); }
            catch (Exception e) { try { sftp.mkdir(current); } catch (Exception ignored) {} }
        }
        sftp.cd("/");
    }

    private String safeName(String name) { return name == null || name.isEmpty() ? "archivo" : name; }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }
    private boolean isEnabled() { return prefs().getBoolean(KEY_ENABLED, false); }
    private void saveConfig(String config) { prefs().edit().putString(KEY_CONFIG, config).apply(); }
    private String getSavedConfig() { return prefs().getString(KEY_CONFIG, "{}"); }

    private synchronized void addLog(String status, String detail, int uploaded, int skipped, long durationMs) {
        try {
            JSONArray old = new JSONArray(prefs().getString(KEY_LOGS, "[]"));
            JSONArray out = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            entry.put("status", status);
            entry.put("detail", detail == null ? "" : detail);
            entry.put("uploaded", uploaded);
            entry.put("skipped", skipped);
            entry.put("durationMs", durationMs);
            out.put(entry);
            for (int i = 0; i < old.length() && out.length() < MAX_LOGS; i++) out.put(old.getJSONObject(i));
            prefs().edit().putString(KEY_LOGS, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void enable(Context context, String config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true).putString(KEY_CONFIG, config == null ? "{}" : config).apply();
    }
    public static void disable(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
    }
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }
    public static String getLogs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOGS, "[]");
    }
    public static void clearLogs(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOGS, "[]").apply();
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL_ID).setContentTitle("IGM process")
                .setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).build();
    }
    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification(text));
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "IGM SFTP", NotificationManager.IMPORTANCE_LOW));
        }
    }
    @Override public void onTimeout(int startId) {
        addLog("TIMEOUT", "Android detuvo el servicio por el límite de dataSync", 0, 0, 0);
        stopSelf();
    }
    @Override public void onDestroy() {
        running = false;
        if (executor != null) executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
