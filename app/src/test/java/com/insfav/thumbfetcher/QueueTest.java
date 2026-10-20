package com.insfav.thumbfetcher;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class QueueTest {
    Context context;
    QueueStore store;
    @Before public void setup() throws Exception { context = RuntimeEnvironment.getApplication(); context.deleteDatabase("queue.db"); store = new QueueStore(context); store.replace(ImportTest.parse(ImportTest.ARRAY)); }
    @After public void close() { store.close(); }
    @Test public void completedAndFailedStatesSurviveReopen() {
        store.mark("example", "SUCCESS", "VALID_WEBP"); store.mark("valid_2", "FAILED", "PROFILE_DATA_MISSING"); store.close(); store = new QueueStore(context);
        assertEquals(Integer.valueOf(1), store.counts().get("SUCCESS")); assertEquals(Collections.singletonList("valid_2"), store.users(true)); assertTrue(store.pending().isEmpty());
    }
    @Test public void retryFailedSelectsOnlyFailed() {
        store.mark("example", "FAILED", "NETWORK_ERROR"); store.reset(store.users(true)); assertEquals(Collections.singletonList("example"), store.pending());
    }
    @Test public void invalidImportPreservesOldQueue() {
        try { store.replace(ImportTest.parse("broken")); fail(); } catch (Exception expected) { }
        assertEquals(Arrays.asList("example", "valid_2"), store.users(false));
    }
    @Test public void unfinishedRowsRemainPendingAcrossProcessRestart() { store.mark("example", "EXISTING", "VALID_WEBP"); store.close(); store = new QueueStore(context); assertEquals(Collections.singletonList("valid_2"), store.pending()); }
    @Test public void unavailableIsPersistentSeparateAndExplicitlyRetryable() {
        store.mark("example", "SKIPPED", "PROFILE_NOT_FOUND_OR_UNAVAILABLE");
        store.close(); store = new QueueStore(context);
        assertEquals(Integer.valueOf(1), store.counts().get("SKIPPED"));
        assertTrue(store.users(true).isEmpty()); assertTrue(store.failures().isEmpty());
        assertEquals(Collections.singletonList("valid_2"), store.preflightCandidates());
        store.reset(store.users(false));
        assertEquals(Arrays.asList("example", "valid_2"), store.pending());
    }
    @Test public void preflightPrefersUntriedAccountsOverEarlierFailures() {
        store.mark("example", "FAILED", "PROFILE_IMAGE_URL_MISSING");
        assertEquals(Arrays.asList("valid_2", "example"), store.preflightCandidates());
    }

}
