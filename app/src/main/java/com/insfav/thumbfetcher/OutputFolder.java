package com.insfav.thumbfetcher;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

/** SAF only. No broad storage permission and no queue files in the output folder. */
final class OutputFolder {
    private final ContentResolver resolver;
    private final Uri tree, folder;
    private final Map<String, Uri> files = new HashMap<>();
    OutputFolder(Context context, String treeValue) throws IOException {
        resolver = context.getContentResolver(); tree = Uri.parse(treeValue);
        boolean permission = false;
        for (UriPermission p : resolver.getPersistedUriPermissions()) if (p.getUri().equals(tree) && p.isReadPermission() && p.isWritePermission()) permission = true;
        if (!permission) throw new SecurityException("OUTPUT_PERMISSION_LOST");
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String parentName;
        try (Cursor c = resolver.query(parent, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null)) {
            if (c == null || !c.moveToFirst() || !DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(1))) throw new IOException("OUTPUT_FOLDER_INVALID"); parentName = c.getString(0);
        }
        if (".thumnails".equals(parentName)) throw new IOException("OUTPUT_SELECT_PARENT");
        if (".thumbnails".equals(parentName)) folder = parent;
        else { Map<String, Uri> children = children(parent); Uri existing = children.get(".thumbnails"); folder = existing != null ? existing : DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, ".thumbnails"); }
        if (folder == null) throw new IOException("OUTPUT_FOLDER_CREATE_FAILED");
        try (Cursor c = resolver.query(folder, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null)) {
            if (c == null || !c.moveToFirst() || !".thumbnails".equals(c.getString(0)) || !DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(1))) throw new IOException("OUTPUT_FOLDER_INVALID");
        }
        files.putAll(children(folder));
    }
    private Map<String, Uri> children(Uri directory) throws IOException {
        Uri child = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(directory));
        Map<String, Uri> result = new HashMap<>();
        try (Cursor cursor = resolver.query(child, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null)) {
            if (cursor == null) throw new IOException("OUTPUT_UNAVAILABLE");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (result.containsKey(name)) throw new IOException("OUTPUT_DUPLICATE_NAMES");
                result.put(name, DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)));
            }
        }
        return result;
    }
    static byte[] read(InputStream source) throws IOException {
        if (source == null) throw new IOException("OUTPUT_READ_FAILED");
        try (InputStream input = source; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) { if (bytes.size() + n > ThumbnailCodec.MAX_OUTPUT) throw new IOException("OUTPUT_SIZE_REJECTED"); bytes.write(buffer, 0, n); }
            return bytes.toByteArray();
        }
    }
    boolean valid(String filename) throws IOException {
        Uri file = files.get(filename); if (file == null) return false;
        try { return ThumbnailCodec.valid(read(resolver.openInputStream(file))); }
        catch (FileNotFoundException error) { throw new IOException("OUTPUT_UNAVAILABLE", error); }
        catch (IOException error) { if ("OUTPUT_SIZE_REJECTED".equals(error.getMessage())) return false; throw error; }
    }
    void recover(String filename) throws IOException {
        Uri partial = files.get(filename + ".part");
        if (partial != null) {
            if (!DocumentsContract.deleteDocument(resolver, partial)) throw new IOException("PARTIAL_CLEANUP_FAILED");
            files.remove(filename + ".part");
        }
    }
    private void exactName(Uri file, String name) throws IOException {
        try (Cursor c = resolver.query(file, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null)) {
            if (c == null || !c.moveToFirst() || !name.equals(c.getString(0))) throw new IOException("OUTPUT_FILENAME_CHANGED");
        }
    }
    boolean write(String filename, byte[] bytes) throws IOException {
        if (!ThumbnailCodec.valid(bytes)) throw new IOException("OUTPUT_INVALID_WEBP");
        if (valid(filename)) return false;
        recover(filename);
        Uri pending = DocumentsContract.createDocument(resolver, folder, "application/octet-stream", filename + ".part");
        if (pending == null) throw new IOException("OUTPUT_CREATE_FAILED");
        files.put(filename + ".part", pending);
        exactName(pending, filename + ".part");
        try (OutputStream output = resolver.openOutputStream(pending, "wt")) {
            if (output == null) throw new IOException("OUTPUT_WRITE_FAILED"); output.write(bytes); output.flush();
        }
        if (!ThumbnailCodec.valid(read(resolver.openInputStream(pending)))) throw new IOException("OUTPUT_READBACK_FAILED");
        Uri old = files.get(filename);
        if (old != null) { if (!DocumentsContract.deleteDocument(resolver, old)) throw new IOException("OUTPUT_REPLACE_FAILED"); files.remove(filename); }
        Uri committed;
        try { committed = DocumentsContract.renameDocument(resolver, pending, filename); }
        catch (UnsupportedOperationException error) { throw new IOException("ATOMIC_RENAME_UNSUPPORTED", error); }
        if (committed == null) throw new IOException("ATOMIC_RENAME_UNSUPPORTED");
        exactName(committed, filename);
        files.remove(filename + ".part"); files.put(filename, committed);
        if (!valid(filename)) throw new IOException("OUTPUT_FINAL_VERIFY_FAILED");
        return true;
    }
}
