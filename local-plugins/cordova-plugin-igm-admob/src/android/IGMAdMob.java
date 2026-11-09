package cl.igm.admob;

import android.app.Activity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

/** Banner nativo anclado en la zona inferior, sin interferir con WebView ni el servicio SFTP. */
public class IGMAdMob extends CordovaPlugin {
    private AdView banner;
    private boolean initialized = false;

    @Override public boolean execute(String action, JSONArray args, CallbackContext callback) throws JSONException {
        if ("showBanner".equals(action)) {
            String unit = args.optString(0, "");
            if (unit.trim().isEmpty()) { callback.error("Falta el ID de unidad AdMob"); return true; }
            cordova.getActivity().runOnUiThread(() -> show(unit, callback));
            return true;
        }
        if ("hideBanner".equals(action)) {
            cordova.getActivity().runOnUiThread(() -> { clearBanner(); callback.success(); });
            return true;
        }
        return false;
    }

    private void show(String unit, CallbackContext callback) {
        try {
            Activity activity = cordova.getActivity();
            clearBanner();
            if (!initialized) { MobileAds.initialize(activity, status -> {}); initialized = true; }
            AdView view = new AdView(activity);
            banner = view;
            view.setAdUnitId(unit);
            // Banner adaptable, dimensionado al ancho de pantalla del teléfono.
            int widthDp = (int)(activity.getResources().getDisplayMetrics().widthPixels /
                    activity.getResources().getDisplayMetrics().density);
            view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            activity.addContentView(view, params);
            view.setAdListener(new AdListener() {
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    android.util.Log.w("IGMAdMob", "No se pudo cargar el anuncio: " + error.getMessage());
                }
            });
            view.loadAd(new AdRequest.Builder().build());
            callback.success();
        } catch (Exception ex) { clearBanner(); callback.error(ex.getMessage()); }
    }

    private void clearBanner() {
        if (banner != null) {
            if (banner.getParent() instanceof ViewGroup) ((ViewGroup) banner.getParent()).removeView(banner);
            banner.destroy(); banner = null;
        }
    }

    @Override public void onDestroy() { cordova.getActivity().runOnUiThread(this::clearBanner); super.onDestroy(); }
}
