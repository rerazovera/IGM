package com.ejemplo.directorypicker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.provider.OpenableColumns;
import androidx.documentfile.provider.DocumentFile;

public class DirectoryPicker extends CordovaPlugin {
    private static final int REQUEST_DIRECTORY = 1001;
    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("select".equals(action)) {
            this.callbackContext = callbackContext;
            selectDirectory();
            return true;
        } else if ("check".equals(action)) {
            String uriString = args.optString(0, null);
            if (uriString == null || uriString.isEmpty()) {
                callbackContext.error("URI vacío");
                return true;
            }
            JSONObject result = checkDirectory(Uri.parse(uriString));
            if (result.optBoolean("ok", false)) callbackContext.success(result);
            else callbackContext.error(result.toString());
            return true;
        } else if ("list".equals(action)) {
            String uriString = args.optString(0, null);
            if (uriString == null || uriString.isEmpty()) {
                callbackContext.error("URI vacío");
                return true;
            }
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(cordova.getContext(), Uri.parse(uriString));
                if (dir == null || !dir.isDirectory() || !dir.canRead()) {
                    callbackContext.error("No se puede leer el directorio");
                    return true;
                }
                JSONArray out = new JSONArray();
                for (DocumentFile f : dir.listFiles()) {
                    JSONObject item = new JSONObject();
                    item.put("name", f.getName());
                    item.put("uri", f.getUri().toString());
                    item.put("directory", f.isDirectory());
                    item.put("size", f.length());
                    item.put("lastModified", f.lastModified());
                    out.put(item);
                }
                callbackContext.success(out);
            } catch (Exception e) {
                callbackContext.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    private void selectDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        }

        cordova.startActivityForResult(this, intent, REQUEST_DIRECTORY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_DIRECTORY) return;

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            try {
                JSONObject result = new JSONObject();
                result.put("cancelled", true);
                callbackContext.success(result);
            } catch (JSONException e) {
                callbackContext.error(e.getMessage());
            }
            return;
        }

        Uri treeUri = data.getData();

        int takeFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            takeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        try {
            cordova.getContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (Exception e) {
            // Some document providers do not expose persistable write access.
            // Read access is sufficient for the background uploader.
            try {
                cordova.getContext().getContentResolver()
                        .takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                e.printStackTrace();
            }
        }

        String name = getDirectoryName(treeUri);

        SharedPreferences prefs = cordova.getContext()
                .getSharedPreferences("directory_picker", Context.MODE_PRIVATE);

        prefs.edit().putString("last_directory", treeUri.toString()).apply();

        try {
            JSONObject result = new JSONObject();
            result.put("cancelled", false);
            result.put("uri", treeUri.toString());
            result.put("name", name);
            callbackContext.success(result);
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private JSONObject checkDirectory(Uri uri) {
        JSONObject r = new JSONObject();
        try {
            int p = cordova.getContext().checkUriPermission(
                    uri,
                    android.os.Process.myPid(),
                    android.os.Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            DocumentFile dir = DocumentFile.fromTreeUri(cordova.getContext(), uri);
            boolean ok = p == android.content.pm.PackageManager.PERMISSION_GRANTED
                    && dir != null && dir.isDirectory() && dir.canRead();
            r.put("ok", ok);
            r.put("uri", uri.toString());
            r.put("readPermission", p == android.content.pm.PackageManager.PERMISSION_GRANTED);
            r.put("isDirectory", dir != null && dir.isDirectory());
            r.put("canRead", dir != null && dir.canRead());
            return r;
        } catch (Exception e) {
            try { r.put("ok", false); r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); } catch (Exception ignored) {}
            return r;
        }
    }

    private String getDirectoryName(Uri treeUri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(treeUri);

            if (documentId != null) {
                String[] parts = documentId.split(":");
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    String path = parts[1];
                    int slash = path.lastIndexOf("/");
                    if (slash >= 0 && slash < path.length() - 1) {
                        return path.substring(slash + 1);
                    }
                    return path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Directorio seleccionado";
    }
}
