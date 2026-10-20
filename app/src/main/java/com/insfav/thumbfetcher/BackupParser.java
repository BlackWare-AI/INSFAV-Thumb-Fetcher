package com.insfav.thumbfetcher;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** Strict streaming import. No reference to the source URI survives parsing. */
final class BackupParser {
    static final int LIMIT = 32 * 1024 * 1024;
    static final class Result {
        final LinkedHashSet<String> users = new LinkedHashSet<>();
        int duplicates, invalid;
    }
    static Result parse(InputStream input) throws IOException {
        InputStream bounded = new FilterInputStream(input) {
            long count;
            @Override public int read() throws IOException { int n = in.read(); if (n >= 0 && ++count > LIMIT) throw new IOException("JSON_TOO_LARGE"); return n; }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int n = in.read(b, off, (int)Math.min(len, LIMIT + 1L - count));
                if (n > 0 && (count += n) > LIMIT) throw new IOException("JSON_TOO_LARGE"); return n;
            }
        };
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (JsonReader reader = new JsonReader(new InputStreamReader(bounded, decoder))) {
            reader.setLenient(false); Result result = new Result();
            if (reader.peek() == JsonToken.BEGIN_ARRAY) collections(reader, result);
            else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                boolean found = false; reader.beginObject();
                while (reader.hasNext()) {
                    if (reader.nextName().equals("favorite_page_pref")) {
                        if (found) throw new IOException("DUPLICATE_BACKUP_FIELD"); found = true;
                        if (reader.peek() == JsonToken.STRING) {
                            try (JsonReader nested = new JsonReader(new StringReader(reader.nextString()))) {
                                nested.setLenient(false); collections(nested, result);
                                if (nested.peek() != JsonToken.END_DOCUMENT) throw new IOException("TRAILING_JSON");
                            }
                        } else collections(reader, result);
                    } else skip(reader, 0);
                }
                reader.endObject(); if (!found) throw new IOException("BACKUP_FIELD_MISSING");
            } else throw new IOException("INVALID_BACKUP_ROOT");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("TRAILING_JSON");
            return result;
        } catch (IllegalStateException | NumberFormatException error) { throw new IOException("INVALID_JSON", error); }
    }
    private static void collections(JsonReader reader, Result result) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject(); boolean items = false;
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("items")) {
                    if (items) throw new IOException("DUPLICATE_ITEMS"); items = true;
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (reader.peek() != JsonToken.BEGIN_OBJECT) { skip(reader, 0); result.invalid++; continue; }
                        reader.beginObject(); String pageId = null; boolean seen = false, ambiguous = false;
                        while (reader.hasNext()) {
                            if (reader.nextName().equals("pageId")) {
                                if (seen) ambiguous = true; seen = true;
                                if (reader.peek() == JsonToken.STRING) pageId = reader.nextString(); else skip(reader, 0);
                            } else skip(reader, 0);
                        }
                        reader.endObject(); String user = Identity.normalize(pageId);
                        if (ambiguous || !Identity.valid(user)) result.invalid++;
                        else if (!result.users.add(user)) result.duplicates++;
                    }
                    reader.endArray();
                } else skip(reader, 0); // Includes the optional display name; never used for identity.
            }
            reader.endObject(); if (!items) throw new IOException("COLLECTION_ITEMS_MISSING");
        }
        reader.endArray();
    }
    private static void skip(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IOException("JSON_TOO_DEEP");
        if (reader.peek() == JsonToken.BEGIN_ARRAY) { reader.beginArray(); while (reader.hasNext()) skip(reader, depth + 1); reader.endArray(); }
        else if (reader.peek() == JsonToken.BEGIN_OBJECT) { reader.beginObject(); while (reader.hasNext()) {reader.nextName(); skip(reader, depth + 1);} reader.endObject(); }
        else reader.skipValue();
    }
}
