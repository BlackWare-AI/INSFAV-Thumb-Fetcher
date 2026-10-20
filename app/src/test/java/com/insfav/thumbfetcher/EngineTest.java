package com.insfav.thumbfetcher;

import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.net.*;
import java.io.*;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EngineTest {
    StorageTest storage; SharedPreferences prefs; QueueStore queue;
    @Before public void setup() throws Exception {
        storage = new StorageTest(); storage.setup(); Context c = storage.context; c.deleteDatabase("queue.db");
        prefs = SessionState.prefs(c); int minute=LocalTime.now().getHour()*60+LocalTime.now().getMinute();
        prefs.edit().clear().putString("tree",storage.tree.toString()).putString("cookieGeneration","synthetic").putString("verifiedGeneration","synthetic").putInt("verifiedProfiles",3).putInt("minimum",1).putInt("maximum",1).putInt("startMinute",(minute+1439)%1440).putInt("endMinute",(minute+60)%1440).commit();
        queue=new QueueStore(c); queue.replace(ImportTest.parse("[{\"items\":[{\"pageId\":\"one\"},{\"pageId\":\"two\"},{\"pageId\":\"three\"}]}]")); QueueControl.requested=false; QueueControl.lock.set(false);
    }
    @After public void close() { queue.close(); QueueControl.requested=false; QueueControl.lock.set(false); }
    PublicProfileProvider provider(int[] calls, int status) throws Exception {
        byte[] pixels=CodecTest.png(256,300); CookieJar jar=CookieJar.parse("sessionid=TEST_SESSION; ds_user_id=123",System.currentTimeMillis());
        return new PublicProfileProvider(url -> {
            calls[0]++;
            if (url.getHost().endsWith("cdninstagram.com")) return new ProviderTest.Stub(url,200,"") { @Override public String getContentType(){return "image/png";} @Override public InputStream getInputStream(){return new ByteArrayInputStream(pixels);} };
            if (url.getPath().equals("/")) return new ProviderTest.Stub(url,200,"<script>{\"config\":{\"viewerId\":\"123\"}}</script>");
            String user=url.getPath().replace("/",""); return new ProviderTest.Stub(url,status,ProviderTest.page(user,"false"));
        },jar);
    }
    QueueEngine engine(PublicProfileProvider provider) { return new QueueEngine(storage.context, s -> {}, () -> false, () -> provider); }
    @Test public void preflightReallyFetchesThreeProfilesBeforeUnlockingBatch() throws Exception {
        prefs.edit().putInt("verifiedProfiles",0).putString("verifiedGeneration","").commit(); int[] calls={0}; engine(provider(calls,200)).run("test",false);
        assertTrue(SessionState.verified(prefs)); assertEquals("VERIFIED",SessionState.evidence(prefs)); assertEquals(3,prefs.getInt("verifiedProfiles",0)); assertEquals(7,calls[0]);
        assertTrue(new OutputFolder(storage.context,storage.tree.toString()).valid("one.webp")); assertFalse(prefs.getBoolean("armed",false));
    }
    @Test public void unverifiedSessionCannotStartBatch() throws Exception {
        prefs.edit().putString("verifiedGeneration","").commit(); int[] calls={0}; engine(provider(calls,200)).run("missing",false); assertEquals(0,calls[0]); assertEquals("COOKIE_PREFLIGHT_REQUIRED",prefs.getString("blocked",""));
    }
    @Test public void validExistingIsSkippedAndSingleWorkerProcessesOnlyOneAccount() throws Exception {
        OutputFolder output=new OutputFolder(storage.context,storage.tree.toString()); output.write("one.webp",ThumbnailCodec.convert(CodecTest.png(150,150))); int[] calls={0}; engine(provider(calls,200)).run("resume",true);
        assertEquals(2,calls[0]); assertEquals(Integer.valueOf(1),queue.counts().get("EXISTING")); assertEquals(Integer.valueOf(1),queue.counts().get("SUCCESS")); assertEquals(1,queue.pending().size());
    }
    @Test public void rateLimitPausesWithoutAutomaticRetryAndPersistsDeadline() throws Exception {
        int[] calls={0}; engine(provider(calls,429)).run("resume",false); assertEquals(1,calls[0]); assertEquals("RATE_LIMITED",prefs.getString("blocked","")); assertTrue(prefs.getLong("retryAt",0)>System.currentTimeMillis()); assertEquals(Integer.valueOf(1),queue.counts().get("FAILED"));
    }
    @Test public void cookieRetestCannotBypassServerRetryAfter() throws Exception {
        prefs.edit().putLong("retryAt",System.currentTimeMillis()+60000).commit(); int[] calls={0}; engine(provider(calls,200)).run("test",false); assertEquals(0,calls[0]); assertEquals("RATE_LIMITED",prefs.getString("blocked",""));
    }
    @Test public void outsideWindowDoesNotSendAnyRequest() throws Exception {
        int minute=LocalTime.now().getHour()*60+LocalTime.now().getMinute(); prefs.edit().putInt("startMinute",(minute+60)%1440).putInt("endMinute",(minute+120)%1440).commit(); int[] calls={0}; engine(provider(calls,200)).run("resume",false); assertEquals(0,calls[0]); assertEquals("Waiting for working hours",prefs.getString("state",""));
    }
    @Test public void pauseFinishesCurrentWriteButNeverStartsAnotherProfile() throws Exception {
        int[] calls={0}; PublicProfileProvider base=provider(calls,200); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        CookieJar jar=CookieJar.parse("sessionid=TEST_SESSION",System.currentTimeMillis()); byte[] pixels=CodecTest.png(160,160);
        PublicProfileProvider delayed=new PublicProfileProvider(url -> {
            if (url.getHost().endsWith("cdninstagram.com")) return new ProviderTest.Stub(url,200,""){ @Override public String getContentType(){return "image/png";} @Override public InputStream getInputStream(){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new ByteArrayInputStream(pixels);} };
            return new ProviderTest.Stub(url,200,ProviderTest.page("one","false"));
        },jar);
        Thread worker=new Thread(() -> engine(delayed).run("resume",false)); worker.start(); assertTrue(entered.await(3,TimeUnit.SECONDS)); QueueControl.requestedState="Paused";QueueControl.requested=true;release.countDown();worker.join(5000);assertFalse(worker.isAlive()); assertEquals("Paused",prefs.getString("state","")); assertEquals(Integer.valueOf(1),queue.counts().get("SUCCESS"));
    }
    @Test public void cookieGenerationChangeInvalidatesProofAndBlocksAutoResume() throws Exception {
        assertTrue(SessionState.verified(prefs)); SessionState.changed(storage.context); assertFalse(SessionState.verified(prefs)); assertFalse(WorkScheduler.enabled(prefs));
    }

    PublicProfileProvider alternatePages(int[] calls, String home, int homeStatus, String profileMode) throws Exception {
        byte[] pixels=CodecTest.png(180,220); CookieJar jar=CookieJar.parse("sessionid=TEST_SESSION; ds_user_id=123",System.currentTimeMillis());
        return new PublicProfileProvider(url -> {
            calls[0]++;
            if(url.getHost().endsWith("cdninstagram.com"))return new ProviderTest.Stub(url,200,"") { @Override public String getContentType(){return "image/png";} @Override public InputStream getInputStream(){return new ByteArrayInputStream(pixels);} };
            if(url.getPath().equals("/"))return new ProviderTest.Stub(url,homeStatus,home);
            String user=url.getPath().replace("/","");
            String body=profileMode.equals("login") ? "<form id='loginForm'></form>" : profileMode.equals("missing") ? "<script>{\"unrelated\":true}</script>" : ProviderTest.page(user,"false");
            return new ProviderTest.Stub(url,200,body);
        },jar);
    }
    @Test public void unsupportedHomepageCanVerifyRealPhotoAccessWithoutClaimingSessionIdentity() throws Exception {
        int[] calls={0}; engine(alternatePages(calls,"<script>{\"new_schema\":true}</script>",200,"photo")).run("test",false);
        assertEquals(7,calls[0]); assertTrue(SessionState.verified(prefs)); assertEquals("UNVERIFIED",SessionState.evidence(prefs));
        assertTrue(prefs.getString("state","").contains("Session identity unverified")); assertFalse(prefs.getString("state","").contains("Cookies Valid"));
        assertTrue(new OutputFolder(storage.context,storage.tree.toString()).valid("one.webp")); assertFalse(prefs.getBoolean("armed",false));
    }
    @Test public void unknownHomepageAndMissingPhotosNeverUnlockBatch() throws Exception {
        int[] calls={0}; engine(alternatePages(calls,"<script>{\"new_schema\":true}</script>",200,"missing")).run("test",false);
        assertEquals(4,calls[0]); assertFalse(SessionState.verified(prefs)); assertEquals("UNVERIFIED",SessionState.evidence(prefs)); assertEquals(Integer.valueOf(3),queue.counts().get("FAILED"));
        assertEquals("PROFILE_IMAGE_URL_MISSING",prefs.getString("blocked",""));
    }
    @Test public void explicitHomepageRestrictionsNeverFallThroughToProfileRequests() throws Exception {
        String[] pages={"<form id='loginForm'></form>","<script>{\"checkpoint_required\":true}</script>","","",""};
        int[] statuses={200,200,401,403,429};
        for(int i=0;i<statuses.length;i++){
            prefs.edit().putLong("retryAt",0).commit(); int[] calls={0};
            engine(alternatePages(calls,pages[i],statuses[i],"photo")).run("test",false);
            assertEquals(1,calls[0]); assertFalse(SessionState.verified(prefs)); assertEquals(3,queue.pending().size());
        }
    }
    @Test public void missingOutputFolderStopsBeforeNetworkAndExplainsPrerequisite() throws Exception {
        prefs.edit().putString("tree","").commit(); int[] calls={0};
        engine(alternatePages(calls,"<script>{\"new_schema\":true}</script>",200,"photo")).run("test",false);
        assertEquals(0,calls[0]); assertFalse(SessionState.verified(prefs)); assertTrue(prefs.getString("state","").contains("Select Output Folder"));
    }
    @Test public void profileLoginStillInvalidatesCookieAfterUnrecognizedHomepage() throws Exception {
        int[] calls={0}; engine(alternatePages(calls,"<script>{\"new_schema\":true}</script>",200,"login")).run("test",false);
        assertEquals(2,calls[0]); assertFalse(SessionState.verified(prefs)); assertEquals("INVALID",SessionState.evidence(prefs));
        assertEquals("COOKIE_INVALID_EXPIRED",prefs.getString("blocked","")); assertEquals(Integer.valueOf(1),queue.counts().get("FAILED"));
    }
    @Test public void unavailableAccountsAreSkippedWithoutStoppingLaterDownloads() throws Exception {
        queue.replace(ImportTest.parse("[{\"items\":[{\"pageId\":\"gone1\"},{\"pageId\":\"gone2\"},{\"pageId\":\"gone3\"},{\"pageId\":\"one\"}]}]"));
        byte[] pixels=CodecTest.png(180,180); int[] profiles={0};
        PublicProfileProvider provider=new PublicProfileProvider(url -> {
            if(url.getHost().endsWith("cdninstagram.com")) return new ProviderTest.Stub(url,200,"") {
                @Override public String getContentType(){return "image/png";}
                @Override public InputStream getInputStream(){return new ByteArrayInputStream(pixels);}
            };
            profiles[0]++; String user=url.getPath().replace("/","");
            return new ProviderTest.Stub(url,user.startsWith("gone")?404:200,ProviderTest.page(user,"false"));
        });
        engine(provider).run("resume",false);
        assertEquals(4,profiles[0]); assertEquals(Integer.valueOf(3),queue.counts().get("SKIPPED"));
        assertEquals(Integer.valueOf(1),queue.counts().get("SUCCESS")); assertTrue(queue.failures().isEmpty());
        assertEquals("Completed",prefs.getString("state",""));
        assertTrue(new OutputFolder(storage.context,storage.tree.toString()).valid("one.webp"));
    }
    @Test public void serverErrorPageIsFailedAndPausedNeverSkippedAsDeleted() throws Exception {
        int[] calls={0};
        PublicProfileProvider provider=new PublicProfileProvider(url -> {calls[0]++;return new ProviderTest.Stub(url,200,ProviderTest.ERROR_PAGE);});
        engine(provider).run("resume",false);
        assertEquals(1,calls[0]); assertEquals(Integer.valueOf(1),queue.counts().get("FAILED"));
        assertFalse(queue.counts().containsKey("SKIPPED")); assertEquals(2,queue.pending().size());
        assertEquals("PROFILE_PAGE_UNAVAILABLE",prefs.getString("blocked",""));
    }

    @Test public void singleProfileDiagnosticWritesOnePhotoButDoesNotUnlockBatch() throws Exception {
        queue.replace(ImportTest.parse("[{\"items\":[{\"pageId\":\"one\"}]}]"));
        int[] calls={0}; engine(provider(calls,200)).run("test_one",false);
        assertEquals(3,calls[0]); assertFalse(SessionState.verified(prefs)); assertEquals(1,prefs.getInt("verifiedProfiles",0));
        assertTrue(new OutputFolder(storage.context,storage.tree.toString()).valid("one.webp"));
        assertTrue(prefs.getString("state","").contains("One profile photo verified"));
    }
    @Test public void fiveProfileDiagnosticDoesNotStopAfterThirdPhoto() throws Exception {
        queue.replace(ImportTest.parse("[{\"items\":[{\"pageId\":\"one\"},{\"pageId\":\"two\"},{\"pageId\":\"three\"},{\"pageId\":\"four\"},{\"pageId\":\"five\"}]}]"));
        int[] calls={0}; engine(provider(calls,200)).run("test",false);
        assertEquals(11,calls[0]); assertEquals(5,prefs.getInt("verifiedProfiles",0));
        assertEquals(Integer.valueOf(5),queue.counts().get("SUCCESS")); assertTrue(SessionState.verified(prefs));
    }
    @Test public void providerIsClosedEvenWhenAuthenticationBlocksQueue() {
        boolean[] closed={false};
        ProfileProvider fake=new ProfileProvider() {
            @Override public void authenticate() throws Failure {throw new Failure("COOKIE_INVALID_EXPIRED",401,0,true);}
            @Override public byte[] fetch(String user){throw new AssertionError("No profile access after login wall");}
            @Override public void cancel(){}
            @Override public void close(){closed[0]=true;}
        };
        new QueueEngine(storage.context,s->{},()->false,()->fake).run("test",false);
        assertTrue(closed[0]); assertEquals("COOKIE_INVALID_EXPIRED",prefs.getString("blocked",""));
    }

}
