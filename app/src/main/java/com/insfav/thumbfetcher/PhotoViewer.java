package com.insfav.thumbfetcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Transient, uncropped source viewer. No HD file or disk cache is created. */
final class PhotoViewer {
    private final Activity activity;
    private final String user;
    private final Dialog dialog;
    private final ZoomImage image;
    private final TextView label;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile WebViewProfileProvider provider;
    private volatile boolean closed;
    private boolean ownsQueueLock;
    PhotoViewer(Activity activity, String user) {
        this.activity = activity; this.user = user;
        dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        label = new TextView(activity); label.setTextColor(Color.WHITE); label.setPadding(24, 24, 24, 16); label.setText(user + " · Loading local thumbnail…"); root.addView(label);
        image = new ZoomImage(activity); root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        Button close = new Button(activity); close.setText("Close"); close.setTextColor(Color.WHITE); close.setBackgroundColor(Color.BLACK); close.setOnClickListener(v -> dialog.dismiss()); root.addView(close);
        dialog.setContentView(root); dialog.setOnDismissListener(d -> close());
    }
    void show() {
        dialog.show();
        worker.execute(() -> {
            try {
                byte[] bytes = new OutputFolder(activity, SessionState.prefs(activity).getString("tree", "")).thumbnail(Identity.outputFilename(user));
                if (bytes != null && ThumbnailCodec.valid(bytes)) showBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length), false, 0, 0);
            } catch (IOException | SecurityException error) { main.post(() -> { if (!closed) label.setText(user + " · Local thumbnail unavailable"); }); }
            if (closed) return;
            // A viewer shares the same exclusive session owner as the queue, preventing cookie purges.
            ownsQueueLock = QueueControl.lock.compareAndSet(false, true);
            if (!ownsQueueLock) { main.post(() -> { if (!closed) label.append(" · Pause downloads and reopen for full resolution"); }); return; }
            try {
                if (System.currentTimeMillis() < SessionState.prefs(activity).getLong("retryAt", 0)) throw new ProfileProvider.Failure("RATE_LIMITED");
                provider = new WebViewProfileProvider(activity, new CookieVault(activity).load());
                if (closed) return;
                byte[] bytes = provider.fetch(user, true);
                if (!ThumbnailCodec.completeContainer(bytes)) throw new IOException("IMAGE_CONTAINER_INVALID");
                BitmapFactory.Options b = new BitmapFactory.Options(); b.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, b);
                if (b.outWidth < 1 || b.outHeight < 1 || b.outWidth > 16384 || b.outHeight > 16384 || (long)b.outWidth * b.outHeight > 40000000) throw new IOException("IMAGE_REJECTED");
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                while (Math.max(b.outWidth, b.outHeight) / options.inSampleSize > 2048) options.inSampleSize = options.inSampleSize == 0 ? 2 : options.inSampleSize * 2;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) throw new IOException("IMAGE_DECODE_FAILED");
                showBitmap(bitmap, true, b.outWidth, b.outHeight);
            } catch (ProfileProvider.Failure error) {
                if (error.retryAt > 0) SessionState.prefs(activity).edit().putLong("retryAt", error.retryAt).commit();
                if (FailurePolicy.pause(error)) SessionState.prefs(activity).edit().putString("blocked", error.code).commit();
                main.post(() -> { if (!closed) label.append(" · " + FailureText.label(error.code)); });
            } catch (IOException | RuntimeException | OutOfMemoryError error) { main.post(() -> { if (!closed) label.append(" · Full resolution unavailable"); }); }
            finally {
                WebViewProfileProvider p = provider; if (p != null) p.close(); provider = null;
                QueueControl.lock.set(false); ownsQueueLock = false;
            }
        });
    }
    private void showBitmap(Bitmap bitmap, boolean remote, int width, int height) {
        if (bitmap == null) return;
        main.post(() -> {
            if (closed) { bitmap.recycle(); return; }
            // Keep the thumbnail if the source offers fewer pixels.
            if (remote && image.bitmap != null && (long)bitmap.getWidth() * bitmap.getHeight() < (long)image.bitmap.getWidth() * image.bitmap.getHeight()) { bitmap.recycle(); label.append(" · " + width + " × " + height + " source offers no larger image"); return; }
            image.replace(bitmap);
            label.setText(user + " · " + (remote ? width : bitmap.getWidth()) + " × " + (remote ? height : bitmap.getHeight())
                + (remote ? " source" : " local thumbnail") + (remote && (width != bitmap.getWidth() || height != bitmap.getHeight()) ? " · Display sampled to " + bitmap.getWidth() + " × " + bitmap.getHeight() : ""));
        });
    }
    void close() {
        closed = true; WebViewProfileProvider p = provider; if (p != null) p.cancel();
        image.replace(null); worker.shutdown();
        if (dialog.isShowing()) dialog.dismiss();
    }
    static final class ZoomImage extends View {
        Bitmap bitmap;
        private float scale = 1, offsetX, offsetY;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector pinch;
        private final GestureDetector gestures;
        ZoomImage(Context context) {
            super(context); setContentDescription("Profile photo. Pinch to zoom, double tap to reset or enlarge, drag to pan.");
            pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) { scale = Math.max(1, Math.min(8, scale * d.getScaleFactor())); invalidate(); return true; }
            });
            gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(android.view.MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(android.view.MotionEvent e) { scale = scale > 1 ? 1 : 3; offsetX = offsetY = 0; invalidate(); return true; }
                @Override public boolean onScroll(android.view.MotionEvent a, android.view.MotionEvent b, float x, float y) { if (!pinch.isInProgress()) { offsetX -= x; offsetY -= y; invalidate(); } return true; }
            });
        }
        void replace(Bitmap next) { Bitmap old = bitmap; bitmap = next; scale = 1; offsetX = offsetY = 0; invalidate(); if (old != null && old != next) old.recycle(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if (bitmap == null) return;
            float fit = Math.min((float)getWidth() / bitmap.getWidth(), (float)getHeight() / bitmap.getHeight());
            float w = bitmap.getWidth() * fit * scale, h = bitmap.getHeight() * fit * scale;
            offsetX = Math.max(-Math.max(0, (w - getWidth()) / 2), Math.min(Math.max(0, (w - getWidth()) / 2), offsetX));
            offsetY = Math.max(-Math.max(0, (h - getHeight()) / 2), Math.min(Math.max(0, (h - getHeight()) / 2), offsetY));
            float x = (getWidth() - w) / 2 + offsetX, y = (getHeight() - h) / 2 + offsetY;
            canvas.drawBitmap(bitmap, null, new RectF(x, y, x + w, y + h), paint);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) { pinch.onTouchEvent(e); gestures.onTouchEvent(e); if (e.getAction() == android.view.MotionEvent.ACTION_UP) performClick(); return true; }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
