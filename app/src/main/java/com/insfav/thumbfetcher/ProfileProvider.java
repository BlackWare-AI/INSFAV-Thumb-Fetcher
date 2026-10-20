package com.insfav.thumbfetcher;

interface ProfileProvider {
    byte[] fetch(String username) throws Failure;
    void cancel();
    final class Failure extends Exception {
        final String code;
        final int http;
        final long retryAt;
        final boolean pause;
        Failure(String code, int http, long retryAt, boolean pause) {
            super(code); this.code = code; this.http = http; this.retryAt = retryAt; this.pause = pause;
        }
        Failure(String code) { this(code, 0, 0, false); }
    }
}
