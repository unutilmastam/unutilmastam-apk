package uz.unutilmastam.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;

/**
 * "Unutilmas Ta'm" — Android WebView ilovasi.
 *
 * Ilova saytni ko'rsatadi, lekin quyidagilar alohida sozlangan:
 *   • Kamera       — QR kod skanerlash uchun (getUserMedia)
 *   • Joylashuv    — yetkazib berish manzili uchun
 *   • Fayl tanlash — mahsulot rasmini yuklash uchun
 *   • Yuklab olish — chek PDF/rasm (blob: manzillar uchun maxsus ko'prik)
 *   • Tashqi havolalar (Telegram, telefon) tashqi ilovada ochiladi
 */
public class MainActivity extends AppCompatActivity {

    /** Ilova manzili — o'zgartirsangiz, AndroidManifest'dagi host'ni ham o'zgartiring. */
    private static final String SITE_URL = "https://unutilmastam.uz/";
    private static final String SITE_HOST = "unutilmastam.uz";

    private static final int REQ_PERMS = 1001;
    private static final int REQ_FILE = 1002;

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingWebPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.webview);
        configureWebView();

        // Boshlanish paytida ruxsatlarni so'raymiz (kamera, joylashuv)
        requestAppPermissions();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(SITE_URL);
        }

        // "Orqaga" tugmasi — avval sayt ichida orqaga qaytadi
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // localStorage — ilova ma'lumotlari shu yerda
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setGeolocationEnabled(true);             // xarita va yetkazib berish uchun
        s.setMediaPlaybackRequiresUserGesture(false);  // kamera darhol ishga tushsin
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Internet bo'lmasa keshdan o'qiydi — service worker bilan birga offline ishlaydi
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);  // saytning o'z "tortib yangilash"iga xalaqit bermasin
        web.addJavascriptInterface(new FileBridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return handleUrl(req.getUrl());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // Saytga "men Android ilovasidaman" deb bildiramiz —
                // shunda chek yuklab olish ko'prik orqali ishlaydi.
                v.evaluateJavascript("window.__isAndroidApp = true;", null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /** Kamera/mikrofon so'rovi (QR skanerlash) */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingWebPermission = request;
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
                    }
                });
            }

            /** Joylashuv so'rovi */
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback cb) {
                boolean ok = ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                cb.invoke(origin, ok, false);
                if (!ok) requestAppPermissions();
            }

            /** Fayl tanlash (mahsulot rasmi, kuryer surati) */
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(i, "Fayl tanlang"), REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    toast("Fayl tanlash oynasi ochilmadi");
                    return false;
                }
            }
        });

        // Oddiy yuklab olish (blob: bo'lmagan havolalar)
        web.setDownloadListener((url, agent, disposition, mime, size) -> {
            if (url.startsWith("blob:")) return;   // blob: — ko'prik orqali hal qilinadi
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                toast("Yuklab olishda xato");
            }
        });
    }

    /** Telegram, telefon, pochta havolalari tashqi ilovada ochilsin */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();

        if (host.equals(SITE_HOST) || host.equals("www." + SITE_HOST)) return false;

        if (scheme.equals("http") || scheme.equals("https")
                || scheme.equals("tel") || scheme.equals("mailto")
                || scheme.equals("sms") || scheme.equals("intent")
                || scheme.equals("tg") || scheme.equals("geo")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (ActivityNotFoundException e) {
                toast("Bu havolani ochadigan ilova topilmadi");
                return true;
            }
        }
        return false;
    }

    /**
     * JavaScript ko'prigi — chekni (PDF/rasm) telefon xotirasiga saqlaydi.
     * Sayt tomonida blob base64 ga aylantirilib shu yerga uzatiladi.
     */
    private class FileBridge {
        @JavascriptInterface
        public void saveFile(String base64Data, String fileName, String mimeType) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    Uri saved = writeToDownloads(bytes, fileName, mimeType);
                    if (saved == null) { toast("Saqlab bo'lmadi"); return; }
                    toast("Saqlandi: " + fileName);

                    // Darhol ulashish taklifi (Telegram, WhatsApp...)
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(mimeType);
                    share.putExtra(Intent.EXTRA_STREAM, saved);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "Ulashish"));
                } catch (Exception e) {
                    toast("Xato: " + e.getMessage());
                }
            });
        }
    }

    private Uri writeToDownloads(byte[] bytes, String name, String mime) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            } else {
                collection = MediaStore.Files.getContentUri("external");
            }
            Uri item = getContentResolver().insert(collection, cv);
            if (item == null) return null;
            try (OutputStream out = getContentResolver().openOutputStream(item)) {
                if (out == null) return null;
                out.write(bytes);
            }
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private void requestAppPermissions() {
        String[] perms = {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        boolean need = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need = true; break;
            }
        }
        if (need) ActivityCompat.requestPermissions(this, perms, REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_PERMS && pendingWebPermission != null) {
            boolean granted = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int req, int result, @Nullable Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(uris);
            filePathCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override protected void onPause()  { web.onPause();  super.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
