package com.lucas.phototriage;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Photo Triage: a WebView UI (assets/index.html) backed by native access to the
 * phone's photo library (MediaStore). The page is served from a virtual https
 * origin so thumbnails can be analysed in JavaScript without CORS problems.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 1;
    private static final int REQ_TRASH = 2;
    private static final String HOST = "appassets.androidplatform.net";
    private static final Uri IMAGES = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                return serve(u);
            }
        });
        web.loadUrl("https://" + HOST + "/index.html");
    }

    // ---------- local "web server" ----------

    private WebResourceResponse serve(Uri u) {
        String path = u.getPath();
        if (path == null || path.equals("/")) path = "/index.html";
        try {
            if (path.startsWith("/thumb/")) return image(Long.parseLong(path.substring(7)), 640, 80);
            if (path.startsWith("/full/")) return image(Long.parseLong(path.substring(6)), 2400, 90);
            String name = path.substring(1);
            InputStream in = getAssets().open(name);
            return new WebResourceResponse(mime(name), name.endsWith(".html") ? "utf-8" : null, in);
        } catch (Exception e) {
            WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
            r.setStatusCodeAndReasonPhrase(404, "Not found");
            return r;
        }
    }

    private WebResourceResponse image(long id, final int max, int quality) throws IOException {
        Uri uri = ContentUris.withAppendedId(IMAGES, id);
        ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
        // ImageDecoder applies the EXIF rotation automatically.
        Bitmap bmp = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float sc = Math.min(1f, (float) max / Math.max(w, h));
            decoder.setTargetSize(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
        bmp.recycle();
        return new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(out.toByteArray()));
    }

    private static String mime(String n) {
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ---------- helpers ----------

    private void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private static String permName() {
        return Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private static ArrayList<Uri> toUris(String idsJson) {
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(idsJson);
            for (int i = 0; i < a.length(); i++) {
                uris.add(ContentUris.withAppendedId(IMAGES, Long.parseLong(a.getString(i))));
            }
        } catch (JSONException | NumberFormatException e) {
            // ignore malformed entries
        }
        return uris;
    }

    private static long parseLong(String s) {
        try {
            return (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------- results coming back from Android ----------

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            js("onPerm(" + ok + ")");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TRASH) {
            js("onTrash(" + (resultCode == RESULT_OK) + ")");
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.onBack && onBack()) ? 1 : 0", v -> {
            if (!"1".equals(v)) finish();
        });
    }

    // ---------- JavaScript bridge: window.Android ----------

    public class Bridge {

        @JavascriptInterface
        public boolean hasPermission() {
            return checkSelfPermission(permName()) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{permName()}, REQ_PERM));
        }

        /** Albums (folders) with photo counts, most recently used first. */
        @JavascriptInterface
        public String albums() {
            String[] proj = {MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
            LinkedHashMap<String, Integer> count = new LinkedHashMap<>();
            HashMap<String, String> names = new HashMap<>();
            try (Cursor c = getContentResolver().query(IMAGES, proj, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        String id = c.getString(0);
                        if (id == null) continue;
                        Integer k = count.get(id);
                        count.put(id, k == null ? 1 : k + 1);
                        if (!names.containsKey(id)) {
                            String nm = c.getString(1);
                            names.put(id, nm == null ? "(no name)" : nm);
                        }
                    }
                }
            } catch (Exception e) {
                return "[]";
            }
            JSONArray a = new JSONArray();
            for (Map.Entry<String, Integer> e : count.entrySet()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("id", e.getKey());
                    o.put("name", names.get(e.getKey()));
                    o.put("count", e.getValue());
                    a.put(o);
                } catch (JSONException ignored) {
                }
            }
            return a.toString();
        }

        /** Photos of one album ("" = all) between two dates in ms (0 = no limit), oldest first. */
        @JavascriptInterface
        public String photos(String bucket, String fromMs, String toMs) {
            StringBuilder sel = new StringBuilder();
            ArrayList<String> args = new ArrayList<>();
            if (bucket != null && !bucket.isEmpty()) {
                sel.append(MediaStore.Images.Media.BUCKET_ID).append("=?");
                args.add(bucket);
            }
            long from = parseLong(fromMs), to = parseLong(toMs);
            if (from > 0) {
                if (sel.length() > 0) sel.append(" AND ");
                sel.append(MediaStore.Images.Media.DATE_TAKEN).append(">=?");
                args.add(String.valueOf(from));
            }
            if (to > 0) {
                if (sel.length() > 0) sel.append(" AND ");
                sel.append(MediaStore.Images.Media.DATE_TAKEN).append("<=?");
                args.add(String.valueOf(to));
            }
            String[] proj = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE
            };
            JSONArray a = new JSONArray();
            try (Cursor c = getContentResolver().query(IMAGES, proj,
                    sel.length() == 0 ? null : sel.toString(),
                    args.isEmpty() ? null : args.toArray(new String[0]),
                    MediaStore.Images.Media.DATE_TAKEN + " ASC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        long taken = c.isNull(2) ? 0 : c.getLong(2);
                        long modified = c.isNull(3) ? 0 : c.getLong(3) * 1000L;
                        JSONObject o = new JSONObject();
                        o.put("id", String.valueOf(c.getLong(0)));
                        o.put("name", c.isNull(1) ? "" : c.getString(1));
                        o.put("t", taken > 0 ? taken : modified);
                        o.put("size", c.isNull(4) ? 0 : c.getLong(4));
                        a.put(o);
                    }
                }
            } catch (Exception e) {
                return "[]";
            }
            return a.toString();
        }

        /** Asks Android to move the photos to the system trash (user confirms in a dialog). */
        @JavascriptInterface
        public void trash(String idsJson) {
            final ArrayList<Uri> uris = toUris(idsJson);
            runOnUiThread(() -> {
                try {
                    PendingIntent pi = MediaStore.createTrashRequest(getContentResolver(), uris, true);
                    startIntentSenderForResult(pi.getIntentSender(), REQ_TRASH, null, 0, 0, 0);
                } catch (Exception e) {
                    js("onTrash(false)");
                }
            });
        }

        /** Opens the Android share sheet with the given photos. */
        @JavascriptInterface
        public void share(String idsJson) {
            final ArrayList<Uri> uris = toUris(idsJson);
            final Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("image/*");
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "Share photos")));
        }
    }
}
