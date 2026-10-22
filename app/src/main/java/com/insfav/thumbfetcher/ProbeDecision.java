package com.insfav.thumbfetcher;

import org.json.JSONObject;

/** Converts explicit page text into a confirmed, account-local unavailable decision. */
final class ProbeDecision {
    static ProfileProvider.Failure failure(String user, JSONObject data) {
        if (!"PROFILE_PAGE_UNAVAILABLE".equals(data.optString("wall"))) return null;
        if (user == null || user.isEmpty() || !data.optBoolean("unavailableText") || data.optBoolean("candidateFound")) return null;
        return new ProfileProvider.Failure("PROFILE_PAGE_UNAVAILABLE", 200, 0, false, true);
    }

    private ProbeDecision() { }
}
