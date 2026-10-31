package com.insfav.thumbfetcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// English-only UI; commits complete private state changes before reporting success.
@android.annotation.SuppressLint({"SetTextI18n", "ApplySharedPref"})
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private QueueStore queue;
    private SettingsBackupUi settingsBackup;
    private Diagnostics log;
    private SharedPreferences settings;
    private TextView stats, status, jsonLabel, folderLabel;
    private ProgressBar progress;
    private TextView cookieStatus;
    private EditText cookieInput, minimum, maximum, startTime, endTime, concurrentViews;
    private LinearLayout cookieEditor;
    private Switch background;
    private ImageView preview;
    private TextView previewLabel;
    private String shownPreview = "";
    private PhotoViewer viewer;
    private final java.util.List<View> configurationViews = new ArrayList<>();
    private Button selectJson, selectFolder, start, pause, stop;
    private static volatile boolean busy, launchPending;
    private final Runnable refresh = new Runnable() { @Override public void run() { update(); handler.postDelayed(this, 750); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); queue = new QueueStore(this); log = new Diagnostics(this); settings = getSharedPreferences("settings", MODE_PRIVATE);
        settingsBackup=new SettingsBackupUi(this,io,new SettingsBackupUi.Host(){
            public String export(char[] pass)throws Exception{return SettingsArchive.export(MainActivity.this,pass,new org.json.JSONObject().put("tree",settings.getString("tree","")));}
            public org.json.JSONObject inspect(String raw,char[] pass)throws Exception{org.json.JSONObject data=SettingsArchive.inspect(MainActivity.this,raw,pass),extra=data.getJSONObject("extra");if(extra.length()!=1||!(extra.get("tree") instanceof String))throw new IOException("Invalid settings");return data;}
            public void apply(org.json.JSONObject data)throws Exception{
                if(busy||launchPending||QueueControl.lock.get())throw new IOException("Stop the current operation first.");
                SettingsArchive.apply(MainActivity.this,data);String tree=data.getJSONObject("extra").getString("tree");
                for(UriPermission grant:getContentResolver().getPersistedUriPermissions())if(grant.getUri().toString().equals(tree)&&grant.isReadPermission()&&grant.isWritePermission())settings.edit().putString("tree",tree).commit();
                OutputIndexObserver.attach(MainActivity.this,tree);
                handler.post(()->{minimum.setText(String.valueOf(settings.getInt("minimum",15)));maximum.setText(String.valueOf(settings.getInt("maximum",30)));startTime.setText(RunPolicy.time(settings.getInt("startMinute",120)));endTime.setText(RunPolicy.time(settings.getInt("endMinute",420)));concurrentViews.setText(String.valueOf(settings.getInt("concurrentWebViews",1)));background.setChecked(settings.getBoolean("background",false));update();});
            }
            public void message(String text){handler.post(()->MainActivity.this.message(text));}
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20), dp(16), dp(20), dp(20)); scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> { view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
        TextView title = text("INSFAV\nThumb Fetcher", 26, Color.WHITE); layout.addView(title);
        TextView subtitle = text("256 × 256 WebP · Local JSON · Private cookies", 13, 0xff999999); layout.addView(subtitle);
        selectJson = button(layout, "Select JSON", view -> picker(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 10));
        jsonLabel = text("No JSON selected", 13, 0xff999999); layout.addView(jsonLabel);
        selectFolder = button(layout, "Select Output Folder", view -> picker(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 11));
        folderLabel = text("No output folder selected", 13, 0xff999999); layout.addView(folderLabel);
        addCookieControls(layout);
        addScheduleControls(layout);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); layout.addView(progress, new LinearLayout.LayoutParams(-1, dp(18)));
        stats = text("", 16, Color.WHITE); stats.setPadding(0, dp(14), 0, dp(14)); layout.addView(stats);
        status = text("Idle", 14, 0xffaaaaaa); layout.addView(status);
        LinearLayout photo = row(layout);
        preview = new ImageView(this); preview.setContentDescription("Open profile photo"); photo.addView(preview, new LinearLayout.LayoutParams(dp(40), dp(40)));
        previewLabel = text("Tap a thumbnail to view its source photo", 13, 0xffaaaaaa); photo.addView(previewLabel);
        preview.setOnClickListener(v -> openPhoto(settings.getString("previewUser", "")));
        start = button(layout, "Start Missing", view -> begin("missing"));
        pause = button(layout, "Pause / Resume", view -> {
            if (QueueControl.lock.get() || (settings.getBoolean("armed", false) && !settings.getBoolean("userPaused", true))) { QueueControl.pause(this, false); status.setText("Pausing after the current request…"); }
            else begin("resume");
        });
        stop = button(layout, "Stop", view -> QueueControl.pause(this, true));
        button(layout, "More", view -> {
            PopupMenu menu = new PopupMenu(this, view);
            menu.getMenu().add("Open Profile Photo").setOnMenuItemClickListener(item -> {
                EditText user = new EditText(this); user.setSingleLine(true); user.setHint("Imported username");
                new AlertDialog.Builder(this).setTitle("Open Profile Photo").setView(user).setPositiveButton("Open", (d, which) -> openPhoto(Identity.normalize(user.getText().toString()))).setNegativeButton("Cancel", null).show(); return true;
            });
            menu.getMenu().add("Retry Failed").setOnMenuItemClickListener(item -> { begin("failed"); return true; });
            menu.getMenu().add("Retry Timeouts").setOnMenuItemClickListener(item -> { begin("retry_timeouts"); return true; });
            menu.getMenu().add("Timeout Retry Results").setOnMenuItemClickListener(item -> { showTimeoutRetryResults(); return true; });
            menu.getMenu().add("Export Timeout-Failed.txt").setOnMenuItemClickListener(item -> { picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "Timeout-Failed.txt"), 13); return true; });
            menu.getMenu().add("Retry Missing Images").setOnMenuItemClickListener(item -> { begin("retry_missing_images"); return true; });
            menu.getMenu().add("Missing Images Retry Results").setOnMenuItemClickListener(item -> { showMissingImageRetryResults(); return true; });
            menu.getMenu().add("Export Missing-Images-Failed.txt").setOnMenuItemClickListener(item -> { picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "Missing-Images-Failed.txt"), 14); return true; });
            menu.getMenu().add("Verify Thumbnail Index").setOnMenuItemClickListener(item -> { begin("verify_index"); return true; });
            menu.getMenu().add("Export Diagnostics").setOnMenuItemClickListener(item -> { picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "INSFAV-Thumb-Fetcher-Diagnostics.txt"), 12); return true; });
            menu.getMenu().add("Export All Settings").setOnMenuItemClickListener(item->{settingsBackup.start(true);return true;});
            menu.getMenu().add("Import All Settings").setOnMenuItemClickListener(item->{settingsBackup.start(false);return true;});
            menu.show();
        });
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
    }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button button(LinearLayout parent, String title, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextColor(Color.WHITE); button.setTextSize(15);
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.BLACK); background.setStroke(dp(1), 0xff333333); background.setCornerRadius(dp(5)); button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.topMargin = dp(12); parent.addView(button, params); button.setOnClickListener(listener); return button;
    }
    private void picker(Intent intent, int code) { try { startActivityForResult(intent, code); } catch (ActivityNotFoundException error) { message("No document picker is available"); } }
    private void begin(String action) {
        if (busy || QueueControl.lock.get() || launchPending) return;
        if (!"verify_index".equals(action)) try { InstagramSession.requireReady(this); } catch (ProfileProvider.Failure e) { message("Verify the Instagram session first"); return; }
        if (!"verify_index".equals(action) && !InstagramSession.configured(this)) { message("Sign in through Instagram Login or select Manual Cookies first"); return; }
        if (!saveSchedule()) return;
        if ((settings.getString("tree", "").isEmpty() || queue.users(false).isEmpty())) { message("Select a valid JSON backup and output folder first"); return; }
        if (!"verify_index".equals(action) && System.currentTimeMillis() < settings.getLong("retryAt",0)) { message("Instagram cooldown is active until Retry-After expires"); return; }
        WorkScheduler.cancel(this);
        { settings.edit().putBoolean("armed", true).putBoolean("userPaused", false).putString("blocked", "").commit(); WorkScheduler.recovery(this); }
        launchPending = true; update();
        try { FetchService.request(this, action); log.log("QUEUE", action.equals("resume") ? "RESUME" : action.equals("failed") ? "RETRY_FAILED" : action.equals("retry_timeouts") ? "RETRY_TIMEOUTS" : action.equals("retry_missing_images") ? "RETRY_MISSING_IMAGES" : "START_MISSING", 0); }
        catch (RuntimeException error) { settings.edit().putString("blocked", "SYSTEM_RESTRICTED").commit(); message("Foreground service could not start"); }
        handler.postDelayed(() -> { launchPending = false; update(); }, 1000);
    }
    private LinearLayout row(LinearLayout parent) { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); parent.addView(row); return row; }
    private Button smallButton(LinearLayout row, String name, View.OnClickListener click) {
        Button b = new Button(this); b.setText(name); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(Color.WHITE);
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.BLACK); d.setStroke(dp(1), 0xff333333); b.setBackground(d);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.topMargin = dp(8); params.setMargins(dp(2), dp(8), dp(2), 0); row.addView(b, params); b.setOnClickListener(click); configurationViews.add(b); return b;
    }
    private void addCookieControls(LinearLayout layout) {
        LinearLayout methods = row(layout);
        smallButton(methods, "WebView Session", v -> { InstagramSession.select(this, false); update(); });
        smallButton(methods, "Manual Cookies", v -> { InstagramSession.select(this, true); update(); });
        LinearLayout engines = row(layout);
        smallButton(engines, "Legacy · WebView", v -> { InstagramSession.selectEngine(this, "legacy"); update(); });
        smallButton(engines, "YTDLnis · Netscape", v -> { InstagramSession.selectEngine(this, "netscape"); update(); });
        LinearLayout languages = row(layout);
        smallButton(languages, "Session · English", v -> settings.edit().putString("language", "en").apply());
        smallButton(languages, "Oturum · Türkçe", v -> settings.edit().putString("language", "tr").apply());
        LinearLayout sessionActions = row(layout);
        smallButton(sessionActions, "Instagram Login / Status", v -> InstagramSession.show(this));
        smallButton(sessionActions, "Test Session", v -> InstagramSession.test(this));
        cookieStatus = text("Cookies Not Configured", 13, 0xffaaaaaa); cookieStatus.setPadding(0, dp(14), 0, 0); layout.addView(cookieStatus);
        LinearLayout controls = row(layout);
        smallButton(controls, "Select Cookies", v -> { cookieInput.setText(""); cookieEditor.setVisibility(cookieEditor.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); if (cookieEditor.getVisibility() == View.VISIBLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); });
        smallButton(controls, "Remove Cookies", v -> {
            new CookieVault(this).remove(); InstagramSession.removeManual(); SessionState.changed(this); cookieInput.setText(""); cookieEditor.setVisibility(View.GONE); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); settings.edit().putString("state", "Cookies removed").apply(); log.log("COOKIE", "REMOVED", 0); update();
        });
        cookieEditor = new LinearLayout(this); cookieEditor.setOrientation(LinearLayout.VERTICAL); cookieEditor.setVisibility(View.GONE); layout.addView(cookieEditor);
        cookieInput = new EditText(this); cookieInput.setHint("Paste Netscape cookies or an HTTP Cookie header"); cookieInput.setHintTextColor(0xff777777); cookieInput.setTextColor(Color.WHITE); cookieInput.setMinLines(3); cookieInput.setMaxLines(6); cookieInput.setTextSize(13);
        cookieInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cookieInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING); cookieInput.setSaveEnabled(false); cookieInput.setFreezesText(false); cookieInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); cookieInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(CookieJar.MAX_INPUT)}); cookieEditor.addView(cookieInput);
        LinearLayout paste = row(cookieEditor);
        smallButton(paste, "Paste Cookies", v -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class); ClipData clip = clipboard.getPrimaryClip();
            CharSequence value = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
            if (value == null || value.length() > CookieJar.MAX_INPUT) { message("Clipboard must contain cookie text of at most 64 KiB"); return; } cookieInput.setText(value);
        });
        smallButton(paste, "Save Cookies", v -> saveCookies());
        smallButton(paste, "Cancel", v -> { cookieInput.setText(""); cookieEditor.setVisibility(View.GONE); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); });
    }
    private void saveCookies() {
        if (busy || QueueControl.lock.get()) return;
        String raw = cookieInput.getText().toString(); busy = true; update();
        io.execute(() -> {
            String error = null;
            try { CookieJar jar = CookieJar.parse(raw, System.currentTimeMillis()); new CookieVault(this).save(jar); SessionState.changed(this); log.log("COOKIE", "SAVED", 0); }
            catch (ProfileProvider.Failure e) { error = "Cookie Invalid / Expired (" + e.code + ")"; log.log("COOKIE", e.code, 0); }
            catch (Exception e) { error = "Cookie encryption/storage failed; previous cookies preserved"; log.log("COOKIE", "ENCRYPTION_FAILED", 0); }
            String result = error;
            handler.post(() -> { busy = false; cookieInput.setText(""); if (result == null) { cookieEditor.setVisibility(View.GONE); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); message("Cookies Configured. Ready for Start Missing."); } else message(result); update(); });
        });
    }
    private EditText field(LinearLayout parent, String label, String initial, boolean clock) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); parent.addView(cell, new LinearLayout.LayoutParams(0, -2, 1)); cell.addView(text(label, 12, 0xff999999));
        EditText input = new EditText(this); input.setText(initial); input.setTextColor(Color.WHITE); input.setTextSize(15); input.setSingleLine(true); input.setInputType(clock ? android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME : android.text.InputType.TYPE_CLASS_NUMBER); cell.addView(input); configurationViews.add(input); return input;
    }
    private void addScheduleControls(LinearLayout layout) {
        LinearLayout delay = row(layout); minimum = field(delay, "Minimum Delay (sec)", String.valueOf(settings.getInt("minimum", 15)), false); maximum = field(delay, "Maximum Delay (sec)", String.valueOf(settings.getInt("maximum", 30)), false);
        concurrentViews = field(row(layout), "Concurrent WebViews (1–30)", String.valueOf(settings.getInt("concurrentWebViews", 1)), false);
        LinearLayout hours = row(layout); startTime = field(hours, "Start Time (HH:mm)", RunPolicy.time(settings.getInt("startMinute", 120)), true); endTime = field(hours, "End Time (HH:mm)", RunPolicy.time(settings.getInt("endMinute", 420)), true);
        background = new Switch(this); background.setText("Background Processing"); background.setTextColor(Color.WHITE); background.setChecked(settings.getBoolean("background", false)); layout.addView(background); configurationViews.add(background);
        Button save = button(layout, "Save Settings", v -> { if (saveSchedule()) { WorkScheduler.cancel(this); WorkScheduler.recovery(this); WorkScheduler.plan(this, false); message("Settings saved. Android may defer scheduled work."); } }); configurationViews.add(save);
    }
    private boolean saveSchedule() {
        try {
            RunPolicy p = new RunPolicy(Integer.parseInt(minimum.getText().toString()), Integer.parseInt(maximum.getText().toString()), RunPolicy.minute(startTime.getText().toString()), RunPolicy.minute(endTime.getText().toString()));
            int concurrency = ConcurrencyPolicy.validate(Integer.parseInt(concurrentViews.getText().toString()));
            settings.edit().putInt("concurrentWebViews", concurrency).putInt("minimum", p.minimum).putInt("maximum", p.maximum).putInt("startMinute", p.start).putInt("endMinute", p.end).putBoolean("background", background.isChecked()).commit(); return true;
        } catch (IllegalArgumentException e) { message("Use 1–30 WebViews, 1–86400 seconds, maximum ≥ minimum, and different HH:mm start/end times"); return false; }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if(settingsBackup.result(request,result,data))return; if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request != 12 && (QueueControl.lock.get() || busy || launchPending)) { message("Stop the current operation first"); return; }
        if (request == 10) {
            busy = true; status.setText("Importing JSON…"); update();
            io.execute(() -> {
                String error = null;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("JSON_OPEN_FAILED");
                    BackupParser.Result parsed = BackupParser.parse(input); queue.replace(parsed);
                    settings.edit().putBoolean("armed", false).putBoolean("userPaused", true).commit(); WorkScheduler.cancel(this);
                    settings.edit().putString("json", "Imported " + parsed.users.size() + " unique accounts").putString("state", "Ready").commit();
                    log.log("IMPORT", "UNIQUE", parsed.users.size()); log.log("IMPORT", "DUPLICATES", parsed.duplicates); log.log("IMPORT", "INVALID", parsed.invalid);
                } catch (Exception | OutOfMemoryError errorValue) { error = "Invalid or unreadable JSON; previous accounts preserved"; log.log("IMPORT", Diagnostics.importCode(errorValue), 0); }
                String value = error; handler.post(() -> { busy = false; if (value != null) message(value); update(); });
            });
        } else if (request == 11) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) { message("Read and write permission are required"); return; }
            busy = true; update();
            io.execute(() -> {
                String error = null;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); new OutputFolder(this, uri.toString(), queue, false);
                    String old = settings.getString("tree", ""); settings.edit().putString("tree", uri.toString()).putString("state", "Output folder ready").commit();
                    OutputIndexObserver.attach(this,uri.toString());
                    if (!old.isEmpty() && !old.equals(uri.toString())) try { getContentResolver().releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
                    log.log("STORAGE", "SELECTED", 0);
                } catch (Exception errorValue) { error = "Output folder unavailable; select a writable parent folder"; log.log("STORAGE", "SELECT_FAILED", 0); }
                String value = error; handler.post(() -> { busy = false; if (value != null) message(value); update(); });
            });
        } else if (request == 12) io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) { if (output == null) throw new IOException(); log.export(output); handler.post(() -> message("Diagnostics exported")); }
            catch (Exception error) { handler.post(() -> message("Diagnostics export failed")); }
        }); else if (request == 13) io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) { if (output == null) throw new IOException(); output.write(timeoutRetryText().getBytes(java.nio.charset.StandardCharsets.UTF_8)); handler.post(() -> message("Timeout-Failed.txt exported")); }
            catch (Exception error) { handler.post(() -> message("Timeout report export failed")); }
        }); else if (request == 14) io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) { if (output == null) throw new IOException(); output.write(missingImageRetryText().getBytes(java.nio.charset.StandardCharsets.UTF_8)); handler.post(() -> message("Missing-Images-Failed.txt exported")); }
            catch (Exception error) { handler.post(() -> message("Missing image report export failed")); }
        });
    }
    private String timeoutRetryText() {
        String runId=settings.getString("timeoutRetryRun","");
        return RetryReportFormatter.format("Retry Timeouts",queue.timeoutRetryReport(runId));
    }
    private String missingImageRetryText() {
        String runId=settings.getString("missingImageRetryRun","");
        return RetryReportFormatter.format("Retry Missing Images",queue.missingImageRetryReport(runId));
    }
    private void showTimeoutRetryResults() {
        String runId=settings.getString("timeoutRetryRun","");
        if(runId.isEmpty()){message("No timeout retry results yet");return;}
        String content=timeoutRetryText();
        TextView body=text(content,14,Color.WHITE);body.setTextIsSelectable(true);android.text.util.Linkify.addLinks(body,android.text.util.Linkify.WEB_URLS);
        ScrollView view=new ScrollView(this);view.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Retry Timeouts — "+(settings.getBoolean("timeoutRetryActive",false)?"In Progress":"Completed"))
            .setView(view).setPositiveButton("Export Timeout-Failed.txt",(d,w)->picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Timeout-Failed.txt"),13))
            .setNegativeButton("Close",null).create();
        dialog.show();
    }
    private void showMissingImageRetryResults() {
        String runId=settings.getString("missingImageRetryRun","");
        if(runId.isEmpty()){message("No missing image retry results yet");return;}
        TextView body=text(missingImageRetryText(),14,Color.WHITE);body.setTextIsSelectable(true);android.text.util.Linkify.addLinks(body,android.text.util.Linkify.WEB_URLS);
        ScrollView view=new ScrollView(this);view.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Retry Missing Images — "+(settings.getBoolean("missingImageRetryActive",false)?"In Progress":"Completed"))
            .setView(view).setPositiveButton("Export Missing-Images-Failed.txt",(d,w)->picker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Missing-Images-Failed.txt"),14))
            .setNegativeButton("Close",null).create();dialog.show();
    }
    private void openPhoto(String user) {
        if (!Identity.valid(user) || !queue.users(false).contains(user)) { message("Select an imported account with a local thumbnail"); return; }
        if (viewer != null) viewer.close(); viewer = new PhotoViewer(this, user); viewer.show();
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void update() {
        if (stats == null) return;
        Map<String, Integer> counts = queue.counts(); int total = 0; for (int value : counts.values()) total += value;
        int existing = counts.getOrDefault("EXISTING", 0), success = counts.getOrDefault("SUCCESS", 0), failed = counts.getOrDefault("FAILED", 0), skipped = counts.getOrDefault("SKIPPED", 0)+counts.getOrDefault("SKIPPED_UNAVAILABLE",0), remaining = counts.getOrDefault("PENDING", 0)+counts.getOrDefault("RETRY_LATER",0);
        int percent = total == 0 ? 0 : (total - remaining) * 100 / total;
        stats.setText(String.format(Locale.ROOT, "Total unique accounts: %,d\nExisting valid thumbnails: %,d\nSuccessfully downloaded: %,d\nFailed: %,d\nSkipped (unavailable): %,d\nRemaining: %,d\nProgress: %d%%", total, existing, success, failed, skipped, remaining, percent));
        stats.append("\nCompleted: " + (existing + success) + "\nActive WebViews: " + QueueControl.activeViews + " / " + QueueControl.effectiveViews + "\nConcurrent WebViews: " + settings.getInt("concurrentWebViews", 1));
        int indexed=queue.indexedImages(),wrongResolution=queue.outOfTargetResolution();
        stats.append("\nIndexed valid thumbnails: "+indexed+"\nResolution outside 128×128 / 256×256: "+wrongResolution);
        if(wrongResolution>0)stats.setTextColor(0xffff5555);else stats.setTextColor(Color.WHITE);
        progress.setProgress(percent);
        String previewUser = settings.getString("previewUser", "");
        if (!previewUser.equals(shownPreview)) {
            shownPreview = previewUser;
            io.execute(() -> {
                try {
                    byte[] bytes = new OutputFolder(this, settings.getString("tree", ""), queue, false).thumbnail(Identity.outputFilename(previewUser));
                    android.graphics.Bitmap bitmap = bytes != null && ThumbnailCodec.valid(bytes) ? android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length) : null;
                    handler.post(() -> { if (!isDestroyed()) { preview.setImageBitmap(bitmap); previewLabel.setText(previewUser); } else if (bitmap != null) bitmap.recycle(); });
                } catch (Exception ignored) { }
            });
        }
        String detail = busy ? "Preparing…" : settings.getString("state", "Idle");
        if (QueueControl.lock.get()) { detail += "\nCurrent: " + QueueControl.current; if (QueueControl.etaSeconds >= 0) detail += "\nEstimated remaining: " + QueueControl.etaSeconds / 60 + " min"; }
        else if (detail.equals("Downloading") || detail.equals("Waiting") || detail.equals("Writing thumbnail") || detail.equals("Checking existing thumbnails")) detail = "Interrupted; tap Resume to continue";
        StringBuilder failureSummary = new StringBuilder();
        for (Map.Entry<String, Integer> failure : queue.failures().entrySet()) failureSummary.append("\n").append(FailureText.label(failure.getKey())).append(": ").append(failure.getValue()).append(" (").append(failure.getKey()).append(")");
        status.setText(ScheduleStatus.text(settings,QueueControl.lock.get(),QueueControl.activeViews)+"\n"+detail + failureSummary);
        String completedState=settings.getString("state","");String retryRun=settings.getString("timeoutRetryRun","");
        if(completedState.equals("Retry Timeouts — Completed")&&!retryRun.isEmpty()&&!retryRun.equals(settings.getString("timeoutRetryShownRun",""))){
            settings.edit().putString("timeoutRetryShownRun",retryRun).commit();handler.post(()->{if(!isFinishing()&&!isDestroyed())showTimeoutRetryResults();});
        }
        String missingRun=settings.getString("missingImageRetryRun","");
        if(completedState.equals("Retry Missing Images — Completed")&&!missingRun.isEmpty()&&!missingRun.equals(settings.getString("missingImageRetryShownRun",""))){
            settings.edit().putString("missingImageRetryShownRun",missingRun).commit();handler.post(()->{if(!isFinishing()&&!isDestroyed())showMissingImageRetryResults();});
        }
        cookieStatus.setText(InstagramSession.status(this) + " · Manual cookies " + (new CookieVault(this).configured() ? "saved" : "not saved")); jsonLabel.setText(settings.getString("json", "No JSON selected"));
        folderLabel.setText(settings.getString("tree", "").isEmpty() ? "No output folder selected" : "Selected folder → .thumbnails / username.webp");
        boolean active = busy || QueueControl.lock.get() || launchPending;
        selectJson.setEnabled(!active); selectFolder.setEnabled(!active); start.setEnabled(!active && total > 0); pause.setEnabled(!busy && !launchPending && total > 0); stop.setEnabled(QueueControl.lock.get() || settings.getBoolean("armed", false));
        pause.setText(QueueControl.lock.get() || (settings.getBoolean("armed", false) && !settings.getBoolean("userPaused", true)) ? "Pause" : "Resume");
        for (View view : configurationViews) view.setEnabled(!active);
        cookieInput.setEnabled(!active);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { InstagramSession.flush(); handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onStop() { InstagramSession.flush();
        if (cookieInput != null) { cookieInput.setText(""); cookieEditor.setVisibility(View.GONE); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); }
        if (!settings.getBoolean("background", false) && QueueControl.lock.get()) QueueControl.pause(this, false);
        super.onStop();
    }
    @Override protected void onDestroy() { if (viewer != null) viewer.close(); io.shutdown(); super.onDestroy(); }
}
