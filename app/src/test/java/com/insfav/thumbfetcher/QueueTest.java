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
    @Test public void retryTimeoutsSelectsOnlyCompletedTimeoutFailuresAndOnlyOnce() {
        store.mark("example","SKIPPED","WEBVIEW_TIMEOUT");
        store.mark("valid_2","SKIPPED_UNAVAILABLE","PROFILE_PAGE_UNAVAILABLE");
        assertEquals(1,store.prepareTimeoutRetries("run-1"));
        assertEquals(Collections.singletonList("example"),store.timeoutRetryPending());
        store.close();store=new QueueStore(context);store.recoverInterrupted();
        assertEquals("Timeout retry must survive process restart",Collections.singletonList("example"),store.timeoutRetryPending());
        store.finishTimeoutRetry("example","SKIPPED","WEBVIEW_TIMEOUT");
        assertEquals(0,store.prepareTimeoutRetries("run-2"));
        QueueStore.RetryReport report=store.timeoutRetryReport("run-1");
        assertEquals(1,report.retried);assertEquals(0,report.recovered);assertEquals("WEBVIEW_TIMEOUT",report.failed.get(0)[1]);
        assertTrue("Unavailable pages are never timeout-retried",store.timeoutRetryPending().isEmpty());
    }
    @Test public void retryTimeoutReportPreservesDifferentSecondErrorAndSuccessIsRecovered() {
        store.mark("example","FAILED","WEBVIEW_TIMEOUT");
        store.mark("valid_2","SKIPPED","WEBVIEW_TIMEOUT");
        assertEquals(2,store.prepareTimeoutRetries("run"));
        store.finishTimeoutRetry("example","SUCCESS","VALID_WEBP");
        store.finishTimeoutRetry("valid_2","FAILED","PROFILE_IMAGE_URL_MISSING");
        QueueStore.RetryReport report=store.timeoutRetryReport("run");
        assertEquals(2,report.retried);assertEquals(1,report.recovered);assertEquals(1,report.failed.size());
        assertEquals("valid_2",report.failed.get(0)[0]);assertEquals("PROFILE_IMAGE_URL_MISSING",report.failed.get(0)[1]);
        assertEquals(0,store.prepareTimeoutRetries("run-again"));
    }
    @Test public void retryMissingImagesSelectsOnlyEligibleErrorsAndSurvivesRestartOnce() {
        store.mark("example","FAILED","PROFILE_IMAGE_URL_MISSING");
        store.mark("valid_2","SKIPPED","PROFILE_DATA_AMBIGUOUS");
        BackupParser.Result more=new BackupParser.Result();more.users.add("example");more.users.add("valid_2");more.users.add("unavailable");more.users.add("pending_user");store.replace(more);
        store.mark("unavailable","SKIPPED_UNAVAILABLE","PROFILE_PAGE_UNAVAILABLE");
        store.mark("pending_user","PENDING","PENDING");
        assertEquals(2,store.prepareMissingImageRetries("missing-run-1"));
        assertEquals(Arrays.asList("example","valid_2"),store.missingImageRetryPending());
        store.close();store=new QueueStore(context);store.recoverInterrupted();
        assertEquals(Arrays.asList("example","valid_2"),store.missingImageRetryPending());
        store.finishMissingImageRetry("example","SUCCESS","VALID_WEBP");
        store.finishMissingImageRetry("valid_2","FAILED","PROFILE_PAGE_UNAVAILABLE");
        assertEquals(0,store.prepareMissingImageRetries("missing-run-2"));
        QueueStore.RetryReport report=store.missingImageRetryReport("missing-run-1");
        assertEquals(2,report.retried);assertEquals(1,report.recovered);assertEquals(1,report.failed.size());
        assertEquals("valid_2",report.failed.get(0)[0]);assertEquals("PROFILE_PAGE_UNAVAILABLE",report.failed.get(0)[1]);
        assertEquals("Retry Missing Images — Completed\n\nRetried: 2\nRecovered: 1\nStill Failed: 1\n\nvalid_2 — PROFILE_PAGE_UNAVAILABLE\nhttps://www.instagram.com/valid_2/\n",RetryReportFormatter.format("Retry Missing Images",report));
        assertEquals("Unavailable pages remain unavailable",Integer.valueOf(1),store.counts().get("SKIPPED_UNAVAILABLE"));
        assertEquals(Collections.singletonList("pending_user"),store.pending());
    }
    @Test public void validIndexedImageIsNotSelectedForMissingImageRetry() {
        store.mark("example","FAILED","PROFILE_IMAGE_URL_MISSING");
        store.image(new QueueStore.Image("example","content://selected/thumbs","example.webp",100,256,256,1,true));
        assertEquals(0,store.prepareMissingImageRetries("missing-run"));
        assertTrue(store.missingImageRetryPending().isEmpty());
    }
    @Test public void invalidImportPreservesOldQueue() {
        try { store.replace(ImportTest.parse("broken")); fail(); } catch (Exception expected) { }
        assertEquals(Arrays.asList("example", "valid_2"), store.users(false));
    }
    @Test public void unfinishedRowsRemainPendingAcrossProcessRestart() { store.mark("example", "EXISTING", "VALID_WEBP"); store.close(); store = new QueueStore(context); assertEquals(Collections.singletonList("valid_2"), store.pending()); }
    @Test public void startMissingKeepsCompletedRowsClosedAndOnlyEnablesUnfinishedRows() {
        store.mark("example", "SUCCESS", "VALID_WEBP");
        store.mark("valid_2", "EXISTING", "VALID_WEBP");
        store.getWritableDatabase().execSQL("UPDATE accounts SET eligible=0"); // Simulate a previous retry action's eligibility filter.
        store.prepareMissing();
        assertEquals("Start Missing must not requeue completed files", Integer.valueOf(1), store.counts().get("SUCCESS"));
        assertEquals("Start Missing must not requeue completed files", Integer.valueOf(1), store.counts().get("EXISTING"));
        assertTrue("No completed account should be sent through per-file validation again", store.pending().isEmpty());
    }
    @Test public void unavailableIsPersistentSeparateAndExplicitlyRetryable() {
        store.mark("example", "SKIPPED", "PROFILE_NOT_FOUND_OR_UNAVAILABLE");
        store.close(); store = new QueueStore(context);
        assertEquals(Integer.valueOf(1), store.counts().get("SKIPPED"));
        assertTrue(store.users(true).isEmpty()); assertEquals(Integer.valueOf(1),store.failures().get("PROFILE_NOT_FOUND_OR_UNAVAILABLE"));
        assertEquals(Collections.singletonList("valid_2"), store.preflightCandidates());
        store.reset(store.users(false));
        assertEquals(Arrays.asList("example", "valid_2"), store.pending());
    }
    @Test public void preflightPrefersUntriedAccountsOverEarlierFailures() {
        store.mark("example", "FAILED", "PROFILE_IMAGE_URL_MISSING");
        assertEquals(Arrays.asList("valid_2", "example"), store.preflightCandidates());
    }

    @Test public void reimportingSameBackupPreservesDurableResultsAndOrder() throws Exception {
        store.mark("example", "SUCCESS", "VALID_WEBP");
        store.replace(ImportTest.parse(ImportTest.ARRAY));
        assertEquals(Collections.singletonList("valid_2"), store.pending());
        assertEquals(Arrays.asList("example", "valid_2"), store.users(false));
    }

    @Test public void changedBackupRetainsMatchingRowsAndAddsOnlyNewAccounts() throws Exception {
        store.mark("example", "SUCCESS", "VALID_WEBP");
        store.replace(ImportTest.parse("[{\"items\":[{\"pageId\":\"@example\"},{\"pageId\":\"new_user\"}]}]"));
        assertEquals(Arrays.asList("example", "new_user"), store.users(false));
        assertEquals(Collections.singletonList("new_user"), store.pending());
        assertEquals(Integer.valueOf(1), store.counts().get("SUCCESS"));
    }

    @Test public void interruptedWebViewClaimIsReturnedToPendingOnRestart() {
        store.mark("valid_2", "SUCCESS", "VALID_WEBP");
        assertTrue(store.claim("example"));
        store.close(); store = new QueueStore(context);
        store.recoverInterrupted();
        assertEquals(Collections.singletonList("example"), store.pending());
    }

    @Test public void outputChangesInvalidateOnlyKnownRowsAndUnknownChangesRequestVerification() {
        store.mark("valid_2", "SUCCESS", "VALID_WEBP");
        store.image(new QueueStore.Image("example","content://provider/item-1","example.webp",512,256,256,1,true));
        store.mark("example","SUCCESS","VALID_WEBP");
        store.recordOutputChange("content://provider/item-1");
        assertTrue(store.image("example").needsCheck);
        String state;try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT status||':'||eligible||':'||code FROM accounts WHERE username='example'",null)){state=c.moveToFirst()?c.getString(0):"MISSING";}
        assertEquals("counts="+store.counts()+"; example="+state,Collections.singletonList("example"),store.pending());
        store.recordOutputChange("content://provider/unindexed");
        assertEquals("1",store.meta("verificationRecommended"));
    }

    @Test public void notificationProgressIncludesRetryLaterAndReportsRealCompletion() {
        Map<String,Integer> counts=new HashMap<>();counts.put("SUCCESS",2);counts.put("EXISTING",1);counts.put("FAILED",1);counts.put("SKIPPED",1);counts.put("RETRY_LATER",2);
        DownloadNotification.Progress progress=new DownloadNotification.Progress(counts,"Downloading","");
        assertEquals(7,progress.total);assertEquals(5,progress.done);assertEquals(71,progress.percent);assertEquals("Downloading",progress.status);
    }

}
