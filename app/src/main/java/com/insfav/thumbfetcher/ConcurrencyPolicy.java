package com.insfav.thumbfetcher;

import android.app.ActivityManager;
import android.content.Context;

/** Requested ceiling is independent from launch pacing and the device's memory budget. */
final class ConcurrencyPolicy {
    static int validate(int value) {
        if (value < 1 || value > 30) throw new IllegalArgumentException("CONCURRENCY_RANGE");
        return value;
    }
    static int initial(Context c, int requested) {
        ActivityManager manager = c.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo(); manager.getMemoryInfo(info);
        // Reserve 256 MiB for the OS, then budget 64 MiB per page. No claim of process isolation.
        int available = (int)Math.max(1, (info.availMem - 256L * 1024 * 1024) / (64L * 1024 * 1024));
        int heap = Math.max(1, manager.getMemoryClass() / 32);
        return Math.max(1, Math.min(validate(requested), manager.isLowRamDevice() ? 1 : Math.min(available, heap)));
    }
    static int reduced(int current) { return Math.max(1, current / 2); }
    private ConcurrencyPolicy() { }
}
