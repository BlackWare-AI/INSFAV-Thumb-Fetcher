package com.insfav.thumbfetcher;

import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.annotation.*;
import org.robolectric.RobolectricTestRunner;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EngineTest {
    StorageTest storage; SharedPreferences prefs; QueueStore queue; byte[] pixels;
    @Before public void setup() throws Exception {
        storage = new StorageTest(); storage.setup(); storage.context.deleteDatabase("queue.db");
        prefs = SessionState.prefs(storage.context); int minute = LocalTime.now().getHour()*60+LocalTime.now().getMinute();
        prefs.edit().clear().putString("tree",storage.tree.toString()).putString("cookieGeneration","synthetic")
            .putInt("minimum",1).putInt("maximum",1).putInt("startMinute",(minute+1439)%1440).putInt("endMinute",(minute+60)%1440).commit();
        queue = new QueueStore(storage.context); accounts(4); pixels = CodecTest.png(320,400);
        QueueControl.requested = false; QueueControl.lock.set(false);
    }
    void accounts(int count) { BackupParser.Result r = new BackupParser.Result(); for (int i=0;i<count;i++) r.users.add("synthetic_"+i); queue.replace(r); }
    @After public void close() { queue.close(); QueueControl.requested=false; QueueControl.lock.set(false); }
    interface Fetch { byte[] get(String user) throws ProfileProvider.Failure; }
    ProfileProvider fake(Fetch fetch) {
        return new ProfileProvider() {
            @Override public void authenticate() { throw new AssertionError("No preflight may run"); }
            @Override public byte[] fetch(String user) throws Failure { return fetch.get(user); }
            @Override public void cancel() { }
        };
    }
    QueueEngine engine(ProfileProvider p) {
        // Tests remove elapsed launch delays via the reporter, while production pacing is tested separately.
        return new QueueEngine(storage.context, state -> prefs.edit().putLong("nextRequestAt",0).commit(), () -> false, () -> p, requested -> requested);
    }
    @Test public void startsDirectlyWithoutCookieProofAndWrites256Webp() throws Exception {
        engine(fake(user -> pixels)).run("missing",false);
        assertEquals(Integer.valueOf(4),queue.counts().get("SUCCESS")); assertEquals("Completed",prefs.getString("state",""));
        byte[] bytes = new OutputFolder(storage.context,storage.tree.toString()).thumbnail("synthetic_0.webp");
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        assertEquals(256,bitmap.getWidth()); bitmap.recycle(); assertFalse(SessionState.verified(prefs));
    }
    @Test public void allAccountLocalFailuresAreSkippedAndLaterAccountStillDownloads() {
        String[] codes={"PROFILE_NOT_FOUND_OR_UNAVAILABLE","PROFILE_PAGE_UNAVAILABLE","PROFILE_IMAGE_URL_MISSING","WEBVIEW_TIMEOUT","WEBVIEW_NETWORK_ERROR","WEBVIEW_NAVIGATION_BLOCKED","PROFILE_DEFAULT_AVATAR","HTTP_ERROR"};
        accounts(codes.length+1);
        engine(fake(user -> { int i=Integer.parseInt(user.substring(10)); if(i<codes.length)throw new ProfileProvider.Failure(codes[i],200,0,true); return pixels; })).run("missing",false);
        assertEquals(Integer.valueOf(codes.length),queue.counts().get("SKIPPED")); assertEquals(Integer.valueOf(1),queue.counts().get("SUCCESS"));
        assertEquals("",prefs.getString("blocked","")); assertTrue(queue.pending().isEmpty());
    }
    @Test public void imageFailuresDoNotTriggerThreeFailureStop() {
        engine(fake(user -> new byte[]{1,2,3})).run("missing",false);
        assertEquals(Integer.valueOf(4),queue.counts().get("FAILED")); assertEquals("Completed",prefs.getString("state",""));
    }
    @Test public void rateLimitAndLoginPauseWithoutStartingAnotherAccount() {
        for(String code:new String[]{"RATE_LIMITED","COOKIE_INVALID_EXPIRED","CHALLENGE_REQUIRED","PRIVATE_RESTRICTED"}) {
            accounts(4); prefs.edit().putLong("retryAt",0).putString("blocked","").commit(); AtomicInteger calls=new AtomicInteger();
            engine(fake(user -> {calls.incrementAndGet(); throw new ProfileProvider.Failure(code,429,System.currentTimeMillis()+60000,true);})).run("missing",false);
            assertEquals(1,calls.get()); assertEquals(3,queue.pending().size()); assertEquals(code,prefs.getString("blocked","")); assertTrue(prefs.getLong("retryAt",0)>System.currentTimeMillis());
        }
    }
    @Test public void retryAfterIsRespectedBeforeAnyProfileRequest() {
        prefs.edit().putLong("retryAt",System.currentTimeMillis()+60000).commit();
        engine(fake(user -> {throw new AssertionError("Server deadline must be honored");})).run("resume",false);
        assertEquals(4,queue.pending().size()); assertEquals("RATE_LIMITED",prefs.getString("blocked",""));
    }
    @Test public void concurrencyMatrixHasBoundedUniqueAssignmentsAndExactlyOnceResults() throws Exception {
        for(int limit:new int[]{1,2,5,10,30}) {
            BackupParser.Result matrix = new BackupParser.Result(); for (int i=0;i<limit*2;i++) matrix.users.add("matrix_"+limit+"_"+i); queue.replace(matrix); prefs.edit().putInt("concurrentWebViews",limit).putLong("nextRequestAt",0).commit();
            AtomicInteger active=new AtomicInteger(), peak=new AtomicInteger(); Set<String> users=ConcurrentHashMap.newKeySet();
            CountDownLatch firstWave=new CountDownLatch(limit);
            ProfileProvider provider=fake(user -> {
                assertTrue("Duplicate assignment",users.add(user)); int n=active.incrementAndGet(); peak.accumulateAndGet(n,Math::max); firstWave.countDown();
                try { if(!firstWave.await(8,TimeUnit.SECONDS))throw new AssertionError("Expected parallel dispatch"); Thread.sleep(10); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProfileProvider.Failure("REQUEST_CANCELLED");}
                finally {active.decrementAndGet();} return pixels;
            });
            engine(provider).run("missing",false);
            assertEquals("Requested parallel slots must be usable",limit,peak.get()); assertEquals(limit*2,users.size());
            assertEquals(Integer.valueOf(limit*2),queue.counts().get("SUCCESS")); assertEquals(0,QueueControl.activeViews);
        }
    }
    @Test public void productionLaunchPacingIsSharedAcrossWorkers() {
        prefs.edit().putInt("concurrentWebViews",5).commit(); accounts(3); List<Long> starts=Collections.synchronizedList(new ArrayList<>());
        new QueueEngine(storage.context,s->{},()->false,()->fake(user->{starts.add(System.nanoTime());return pixels;}),requested->requested).run("missing",false);
        assertEquals(3,starts.size()); assertTrue(starts.get(1)-starts.get(0)>=900_000_000L); assertTrue(starts.get(2)-starts.get(1)>=900_000_000L);
    }
    @Test public void pauseCancelsWholeBatchAndLeavesUncommittedAccountsPending() throws Exception {
        prefs.edit().putInt("concurrentWebViews",5).commit(); accounts(10); CountDownLatch entered=new CountDownLatch(5);
        ProfileProvider provider=fake(user->{entered.countDown();try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProfileProvider.Failure("REQUEST_CANCELLED");}return pixels;});
        Thread worker=new Thread(()->engine(provider).run("missing",false)); worker.start(); assertTrue(entered.await(10,TimeUnit.SECONDS));
        QueueControl.requestedState="Paused"; QueueControl.requested=true; worker.join(7000);
        assertFalse(worker.isAlive()); assertEquals(10,queue.pending().size()); assertEquals("Paused",prefs.getString("state",""));
        QueueControl.requested=false; engine(fake(user->pixels)).run("resume",false); assertEquals(Integer.valueOf(10),queue.counts().get("SUCCESS"));
    }
    @Test public void existingValidFilesArePreservedAndOnlyLegacyRefreshOverwrites() throws Exception {
        OutputFolder output=new OutputFolder(storage.context,storage.tree.toString()); byte[] old=CodecTest.legacy(); output.write("synthetic_0.webp",old);
        engine(fake(user->pixels)).run("missing",false); assertEquals(Integer.valueOf(1),queue.counts().get("EXISTING"));
        assertArrayEquals(old,new OutputFolder(storage.context,storage.tree.toString()).thumbnail("synthetic_0.webp"));
        AtomicInteger calls=new AtomicInteger(); engine(fake(user->{calls.incrementAndGet();return pixels;})).run("refresh_legacy",false);
        assertEquals(1,calls.get()); assertFalse(new OutputFolder(storage.context,storage.tree.toString()).legacy("synthetic_0.webp"));
    }
    @Test public void revokedStorageAndOutsideHoursStopBeforeFetch() {
        prefs.edit().putString("tree","").commit(); engine(fake(user->{throw new AssertionError();})).run("resume",false);
        assertEquals("STORAGE_PERMISSION_LOST",prefs.getString("blocked",""));
        int minute=LocalTime.now().getHour()*60+LocalTime.now().getMinute(); prefs.edit().putInt("startMinute",(minute+60)%1440).putInt("endMinute",(minute+120)%1440).commit();
        engine(fake(user->{throw new AssertionError();})).run("resume",false); assertEquals("Waiting for working hours",prefs.getString("state",""));
    }
    @Test public void defaultAndMemoryReductionNeverIncreaseParallelism() {
        assertEquals(1,prefs.getInt("concurrentWebViews",1));
        for(int n:new int[]{1,2,5,10,30}) {assertEquals(n,ConcurrencyPolicy.validate(n)); assertTrue(ConcurrencyPolicy.reduced(n)<=n); assertTrue(ConcurrencyPolicy.reduced(n)>=1);}
        for(int n:new int[]{0,31})try{ConcurrencyPolicy.validate(n);fail();}catch(IllegalArgumentException expected){}
    }
}
