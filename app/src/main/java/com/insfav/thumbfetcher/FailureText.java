package com.insfav.thumbfetcher;

final class FailureText {
    static String label(String code) {
        if (code.equals("COOKIE_PREFLIGHT_INCOMPLETE")) return "Profile Verification Incomplete";
        if (code.equals("COOKIE_PREFLIGHT_REQUIRED") || code.equals("COOKIE_CHANGED")) return "Cookie Test Required";
        if (code.equals("COOKIE_REQUIRED")) return "Cookies Not Configured";
        if (code.equals("COOKIE_DECRYPT_FAILED")) return "Cookie Storage Unavailable";
        if (code.startsWith("COOKIE_")) return code.equals("COOKIE_AUTH_UNVERIFIED") ? "Cookie Session Unverified" : "Cookie Invalid / Expired";
        if (code.startsWith("LOGIN_")) return "Login Required";
        if (code.equals("PROFILE_NOT_FOUND_OR_UNAVAILABLE")) return "Profile Not Found / Unavailable";
        if (code.equals("PRIVATE_RESTRICTED") || code.equals("CHALLENGE_REQUIRED") || code.equals("ACCESS_BLOCKED")) return "Private / Restricted Profile";
        if (code.equals("RATE_LIMITED")) return "Rate Limited";
        if (code.startsWith("PROFILE_") || code.equals("INVALID_IMAGE_URL")) return "Profile Image URL Missing";
        if (code.startsWith("IMAGE_") || code.startsWith("WEBP_")) return "Image Decode Failed";
        if (code.startsWith("OUTPUT_") || code.startsWith("STORAGE_") || code.contains("RENAME") || code.contains("PARTIAL_")) return "Storage Error";
        if (code.equals("SYSTEM_RESTRICTED")) return "Android Background Restriction";
        return "Download Failed";
    }
}
