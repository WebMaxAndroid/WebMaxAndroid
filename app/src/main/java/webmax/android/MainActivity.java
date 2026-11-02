package webmax.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final String VPN_MESSAGE =
            "Сайт не загружен и данные не отправлены, так как вы открыли приложение через VPN\n\n"
            + "В Max нет анонимности, так что нет смысла использовать анонимайзеры, ведь он может связать их с вашей личностью\n\n"
            + "Отключите VPN или исключите приложение из него\n\n";

    private WebView webView;
    private ProgressBar spinner;
    private boolean reloading = false;

    private ConnectivityManager.NetworkCallback netCallback;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private static boolean hasVpn(NetworkCapabilities c) {
        return c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
    }

    private boolean isVpnActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        return hasVpn(cm.getNetworkCapabilities(n));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);

        if (isVpnActive()) {            
            TextView tv = new TextView(this);
            tv.setText(VPN_MESSAGE);
            tv.setTextSize(18);
            tv.setTextColor(Color.WHITE);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(48, 48, 48, 48);

            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            sv.setBackgroundColor(Color.BLACK);
            sv.addView(tv);
            tv.setBackgroundColor(Color.BLACK);
            setContentView(sv);     
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (!hasVpn(caps)) runOnUiThread(() -> finish());                
            }};
            cm.registerDefaultNetworkCallback(netCallback);
            return;
        } else {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (hasVpn(caps)) runOnUiThread(() -> finish());                
            }};
            cm.registerDefaultNetworkCallback(netCallback);            
        }

        webView = new WebView(this);
        float density = getResources().getDisplayMetrics().density;
        final int size = (int) (40 * density);
        final float maxTravel = 72 * density;

        spinner = new ProgressBar(this);
        int pad = (int) (8 * density);
        spinner.setPadding(pad, pad, pad, pad);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        spinner.setBackground(circle);
        spinner.setElevation(8 * density);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.BLUE));
        spinner.setVisibility(View.INVISIBLE);

        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(spinner, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(root);

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !"https".equals(r.getUrl().getScheme());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                reloading = false;
                spinner.setVisibility(View.INVISIBLE);
            }
        });

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (webView.canGoBack()) webView.goBack();
                    else finish();
                });

        final float[] t = {0, -1};
        webView.setOnTouchListener((v, e) -> {
            float h = v.getHeight();
            float showAt = h * 0.05f;
            float fireAt = h * 0.2f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    t[0] = e.getX();
                    t[1] = (!reloading && e.getY() < h / 2f) ? e.getY() : -1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    t[1] = -1;
                    if (!reloading) spinner.setVisibility(View.INVISIBLE);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (t[1] < 0) break;
                    float dy = e.getY() - t[1];
                    if (dy > showAt && Math.abs(e.getX() - t[0]) < dy / 2) {
                        float p = Math.min(1f, (dy - showAt) / (fireAt - showAt));
                        spinner.setVisibility(View.VISIBLE);
                        spinner.setAlpha(p);
                        spinner.setTranslationY(-size + p * (maxTravel + size));
                    } else {
                        spinner.setVisibility(View.INVISIBLE);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    float dy = e.getY() - t[1];
                    if (t[1] >= 0 && dy >= fireAt && Math.abs(e.getX() - t[0]) < dy / 2) {
                        reloading = true;
                        spinner.setVisibility(View.VISIBLE);
                        spinner.setAlpha(1f);
                        spinner.setTranslationY(maxTravel);
                        webView.reload();
                    } else if (!reloading) {
                        spinner.setVisibility(View.INVISIBLE);
                    }
                    t[1] = -1;
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    t[1] = -1;
                    if (!reloading) spinner.setVisibility(View.INVISIBLE);
                    break;
            }
            return false;
        });
        
        webView.loadUrl("https://web.max.ru/");
    }

    @Override
    protected void onPause() {
        if (webView != null) CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(screenOff);
        if (netCallback != null) {
            try {
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) { }
        }
        if (webView != null) {
            CookieManager.getInstance().flush();
            webView.destroy();
        }
        super.onDestroy();
        System.exit(0);
        Process.killProcess(Process.myPid());        
    }
}
