package com.insfav.thumbfetcher;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ProbeDecisionTest {
    @Test public void onlyExplicitClosedPageTextConfirmsUnavailable() throws Exception {
        for (String text : new String[]{"bu sayfa kullanılamıyor", "sorry, this page isn't available"}) {
            JSONObject data = new JSONObject().put("wall", "PROFILE_PAGE_UNAVAILABLE")
                .put("unavailableText", true).put("pageReady", true).put("searchCompleted", true);
            ProfileProvider.Failure failure = ProbeDecision.failure("example", data);
            assertNotNull(failure);
            assertEquals("PROFILE_PAGE_UNAVAILABLE", failure.code);
            assertTrue(failure.confirmedUnavailable);
            assertTrue(FailurePolicy.confirmedUnavailable(failure));
            assertFalse(FailurePolicy.pause(failure));
        }
    }

    @Test public void candidateOrUnverifiedTextCannotConfirmUnavailable() throws Exception {
        JSONObject candidate = new JSONObject().put("wall", "PROFILE_PAGE_UNAVAILABLE")
            .put("unavailableText", true).put("candidateFound", true);
        assertNull(ProbeDecision.failure("example", candidate));
        JSONObject noText = new JSONObject().put("wall", "PROFILE_PAGE_UNAVAILABLE");
        assertNull(ProbeDecision.failure("example", noText));
        ProfileProvider.Failure unconfirmed = new ProfileProvider.Failure("PROFILE_PAGE_UNAVAILABLE", 200, 0, false);
        assertFalse(FailurePolicy.confirmedUnavailable(unconfirmed));
        assertFalse(FailurePolicy.skip(unconfirmed));
        assertFalse(FailurePolicy.pause(unconfirmed));
        JSONObject emptyUser = new JSONObject().put("wall", "PROFILE_PAGE_UNAVAILABLE").put("unavailableText", true);
        assertNull(ProbeDecision.failure("", emptyUser));
    }
}
