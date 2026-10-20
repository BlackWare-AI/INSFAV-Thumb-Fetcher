package com.insfav.thumbfetcher;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ImportTest {
    static final String ARRAY = "[{\"items\":[{\"pageId\":\" @Example \"},{\"pageId\":\"EXAMPLE\"},{\"pageId\":\"valid_2\"},{\"pageId\":\"bad user\"},{\"pageId\":12},{}]}]";
    static BackupParser.Result parse(String raw) throws IOException { return BackupParser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))); }
    @Test public void threeFormatsHaveSameResult() throws Exception {
        String[] forms = {ARRAY, "{\"favorite_page_pref\":" + ARRAY + "}", new JSONObject().put("favorite_page_pref", ARRAY).toString()};
        for (String form : forms) { BackupParser.Result r = parse(form); assertEquals(Arrays.asList("example", "valid_2"), new ArrayList<>(r.users)); assertEquals(1, r.duplicates); assertEquals(3, r.invalid); }
    }
    @Test public void normalizationAndGrammar() {
        Locale old = Locale.getDefault(); try { Locale.setDefault(new Locale("tr", "TR")); assertEquals("insfav", Identity.normalize(" @INSFAV ")); } finally { Locale.setDefault(old); }
        for (String invalid : new String[]{"@@abc", "..", "a..b", ".abc", "abc.", "a/b", "a b", "é", "a".repeat(31), ""}) assertFalse(invalid, Identity.valid(Identity.normalize(invalid)));
    }
    @Test public void filenameKnownVector() {
        assertEquals("50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c.webp", Identity.filename(" @EXAMPLE "));
        assertEquals(Identity.filename("example"), Identity.filename("EXAMPLE"));
    }
    @Test public void rejectsTrailingAndDuplicateIdentity() throws Exception {
        for (String bad : new String[]{"[] garbage", "{}", "null", "[{\"items\":null}]", "{\"favorite_page_pref\":[],\"favorite_page_pref\":[]}", "[1]", "[] []"}) {
            try { parse(bad); fail(bad); } catch (IOException expected) { }
        }
        assertEquals(1, parse("[{\"items\":[{\"pageId\":\"a\",\"pageId\":\"b\"}]}]").invalid);
    }
    @Test public void rejectsOversizedStream() throws Exception {
        InputStream huge = new InputStream() { int count; @Override public int read() { return count++ < BackupParser.LIMIT + 1 ? ' ' : -1; } };
        try { BackupParser.parse(huge); fail(); } catch (IOException expected) { }
    }
    @Test public void largeImportsPreserveOrder() throws Exception {
        for (int size : new int[]{1000, 5000, 10000}) {
            StringBuilder json = new StringBuilder("[{\"items\":[");
            for (int i = 0; i < size; i++) { if (i > 0) json.append(','); json.append("{\"pageId\":\"account_").append(i).append("\"}"); }
            json.append("]}]"); long started = System.nanoTime(); BackupParser.Result r = parse(json.toString()); long ms = (System.nanoTime() - started) / 1000000;
            assertEquals(size, r.users.size()); assertEquals("account_0", r.users.iterator().next());
            System.out.println("IMPORT_BENCHMARK accounts=" + size + " elapsed_ms=" + ms + " bytes=" + json.length());
        }
    }
}
