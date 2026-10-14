import android.annotation.SuppressLint;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile String preferredVoice = null;   // chosen voice name ("" / null = auto)
    private volatile String maleVoice = null;        // auto-detected male voice name (cached)

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // Native TTS — used by the reader's "With tarjuma" audio mode.
        tts = new TextToSpeech(this, status -> ttsReady = (status == TextToSpeech.SUCCESS));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { notifyTtsDone(); }
            @Override public void onError(String id) { notifyTtsDone(); }
        });

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFF06110E);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.getSettings().setLoadWithOverviewMode(false);
        web.getSettings().setUseWideViewPort(false);
        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");

        setContentView(web);
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void notifyTtsDone() {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.__ttsDone && window.__ttsDone()", null);
        });
    }

    /** Small native bridge — grows in later stages (audio service, alarms, downloads). */
    private class Bridge {
        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(MainActivity.this::finish);
        }

        /** Is a system voice installed for this language tag (e.g. "ur", "hi")? */
        @JavascriptInterface
        public boolean canSpeak(String tag) {
            if (tts == null || !ttsReady) return false;
            try {
                int r = tts.setLanguage(Locale.forLanguageTag(tag));
                return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            } catch (Exception e) {
                return false;
            }
        }

        /** Preferred TTS voice name ("" = auto-pick a male voice when available). */
        @JavascriptInterface
        public void setVoice(String name) {
            preferredVoice = (name == null || name.isEmpty()) ? null : name;
        }

        /** JSON list of installed voices for a language tag: [{name,network}, ...] */
        @JavascriptInterface
        public String listVoices(String tag) {
            if (tts == null || !ttsReady) return "[]";
            String lang;
            try { lang = Locale.forLanguageTag(tag).getLanguage(); } catch (Exception e) { lang = ""; }
            StringBuilder sb = new StringBuilder("[");
            try {
                Set<Voice> vs = tts.getVoices();
                boolean first = true;
                if (vs != null) for (Voice v : vs) {
                    if (v == null || v.getLocale() == null) continue;
                    if (!v.getLocale().getLanguage().equalsIgnoreCase(lang)) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append("{\"name\":\"").append(jesc(v.getName())).append("\",\"network\":")
                      .append(v.isNetworkConnectionRequired()).append('}');
                }
            } catch (Exception ignored) {}
            return sb.append(']').toString();
        }

        @JavascriptInterface
        public void speak(String text, String tag) {
            if (tts == null || !ttsReady || text == null) return;
            Locale loc;
            try { loc = Locale.forLanguageTag(tag); } catch (Exception e) { loc = Locale.US; }
            try { tts.setLanguage(loc); } catch (Exception ignored) {}
            applyVoice(loc);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aq");
        }

        @JavascriptInterface
        public void stopSpeak() {
            if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} }
        }
    }

    /** Pick the voice: explicit preference, else the first male-named voice for the locale. */
    private void applyVoice(Locale loc) {
        try {
            Set<Voice> vs = tts.getVoices();
            if (vs == null) return;
            Voice pick = null;
            if (preferredVoice != null) {
                for (Voice v : vs) if (preferredVoice.equals(v.getName())) { pick = v; break; }
            }
            if (pick == null) {
                String lang = loc.getLanguage();
                for (Voice v : vs) {
                    if (v == null || v.getLocale() == null) continue;
                    if (!v.getLocale().getLanguage().equalsIgnoreCase(lang)) continue;
                    String n = v.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("male") && !n.contains("female")) { pick = v; break; }
                }
            }
            if (pick != null) tts.setVoice(pick);
        } catch (Exception ignored) {}
    }

    private static String jesc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    /** Let the web app handle back first (in-app navigation); else leave the app. */
    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(window.__hwBack && window.__hwBack()) || false", value -> {
            if (!"true".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
