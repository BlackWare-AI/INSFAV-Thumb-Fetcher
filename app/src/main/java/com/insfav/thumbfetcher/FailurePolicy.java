package com.insfav.thumbfetcher;

/** Account-local failures must never stop the batch or be presented as proof of deletion. */
final class FailurePolicy {
    static boolean skip(String code) {
        return code.equals("PROFILE_NOT_FOUND_OR_UNAVAILABLE")
            || code.equals("PROFILE_DEFAULT_AVATAR") || code.equals("PROFILE_IMAGE_URL_MISSING")
            || code.equals("PROFILE_IDENTITY_UNVERIFIED") || code.equals("PROFILE_DATA_AMBIGUOUS")
            || code.equals("WEBVIEW_TIMEOUT") || code.equals("WEBVIEW_NETWORK_ERROR")
            || code.equals("WEBVIEW_NAVIGATION_BLOCKED") || code.equals("HTTP_ERROR");
    }
    static boolean confirmedUnavailable(ProfileProvider.Failure error) {
        return error.confirmedUnavailable && error.code.equals("PROFILE_PAGE_UNAVAILABLE");
    }
    static boolean skip(ProfileProvider.Failure error) { return skip(error.code) || confirmedUnavailable(error); }
    static boolean pause(ProfileProvider.Failure error) { return !error.code.equals("PROFILE_PAGE_UNAVAILABLE") && !skip(error.code) && (error.pause || error.code.startsWith("COOKIE_") || error.code.startsWith("LOGIN_")); }
    private FailurePolicy() { }
}
