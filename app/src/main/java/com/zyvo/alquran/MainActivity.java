package com.zyvo.alquran;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

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

        @JavascriptInterface
        public void speak(String text, String tag) {
            if (tts == null || !ttsReady || text == null) return;
            try { tts.setLanguage(Locale.forLanguageTag(tag)); } catch (Exception ignored) {}
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aq");
        }

        @JavascriptInterface
        public void stopSpeak() {
            if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} }
        }
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
