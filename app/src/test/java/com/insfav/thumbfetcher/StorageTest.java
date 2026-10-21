package com.insfav.thumbfetcher;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StorageTest {
    Context context;
    Fixture provider;
    Uri tree = Uri.parse("content://fetcher.test/tree/root");
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        ProviderInfo info = new ProviderInfo(); info.authority = "fetcher.test"; info.exported = true; info.grantUriPermissions = true; info.readPermission = "android.permission.MANAGE_DOCUMENTS"; info.writePermission = "android.permission.MANAGE_DOCUMENTS";
        provider = Robolectric.buildContentProvider(Fixture.class).create(info).get(); provider.root = Files.createTempDirectory("fetcher-saf").toFile();
        context.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
    @Test public void writeVerifySkipAndRecoverPartials() throws Exception {
        OutputFolder output = new OutputFolder(context, tree.toString()); String name = Identity.outputFilename("example"); byte[] bytes = ThumbnailCodec.convert(CodecTest.png(256, 300));
        assertTrue(new File(provider.root, ".thumbnails").isDirectory());
        assertTrue(output.write(name, bytes)); assertTrue(output.valid(name)); int writes = provider.writes;
        assertFalse(output.write(name, bytes)); assertEquals(writes, provider.writes);
        File part = new File(provider.root, ".thumbnails/" + name + ".part"); Files.write(part.toPath(), new byte[]{1});
        OutputFolder reopened = new OutputFolder(context, tree.toString()); reopened.recover(name); assertFalse(part.exists()); assertTrue(reopened.valid(name));
    }
    @Test public void brokenExistingCanBeReplacedWithoutDeletingOtherAccounts() throws Exception {
        OutputFolder output = new OutputFolder(context, tree.toString());
        String name = Identity.outputFilename("example"), other = Identity.outputFilename("other");
        Files.write(new File(provider.root, ".thumbnails/" + name).toPath(), new byte[]{1,2,3});
        byte[] untouched = ThumbnailCodec.convert(CodecTest.png(130, 140)); Files.write(new File(provider.root, ".thumbnails/" + other).toPath(), untouched);
        output = new OutputFolder(context, tree.toString()); assertFalse(output.valid(name)); assertTrue(output.write(name, untouched)); assertArrayEquals(untouched, Files.readAllBytes(new File(provider.root, ".thumbnails/" + other).toPath()));
    }
    @Test public void revokedPermissionStopsBeforeWriting() throws Exception {
        context.getContentResolver().releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { new OutputFolder(context, tree.toString()); fail(); } catch (SecurityException expected) { }
    }
    @Test public void failedRenameIsNotSuccessAndRemainsRecoverable() throws Exception {
        OutputFolder output = new OutputFolder(context, tree.toString()); provider.rejectRename = true; String name = Identity.outputFilename("example");
        try { output.write(name, ThumbnailCodec.convert(CodecTest.png(140, 140))); fail(); } catch (IOException expected) { }
        assertFalse(output.valid(name)); assertTrue(new File(provider.root, ".thumbnails/" + name + ".part").isFile());
    }
    @Test public void validLegacyImageIsNeverOverwritten() throws Exception {
        OutputFolder output = new OutputFolder(context, tree.toString()); byte[] original = CodecTest.legacy(); String name = "synthetic.webp";
        output.write(name, original); provider.rejectCommit = true;
        assertFalse(output.write(name, ThumbnailCodec.convert(CodecTest.png(320,320))));
        provider.rejectCommit = false; OutputFolder reopened = new OutputFolder(context,tree.toString()); reopened.recover(name);
        assertTrue(reopened.valid(name)); assertArrayEquals(original,reopened.thumbnail(name));
    }
    @Test public void interruptedRefreshRestoresBackupOnNextOpen() throws Exception {
        OutputFolder output = new OutputFolder(context,tree.toString()); byte[] original=CodecTest.legacy(); output.write("synthetic.webp",original);
        File folder=new File(provider.root,".thumbnails"); assertTrue(new File(folder,"synthetic.webp").renameTo(new File(folder,"synthetic.webp.bak")));
        Files.write(new File(folder,"synthetic.webp.part").toPath(),new byte[]{1,2});
        OutputFolder reopened=new OutputFolder(context,tree.toString()); reopened.recover("synthetic.webp"); assertArrayEquals(original,reopened.thumbnail("synthetic.webp"));
        assertFalse(new File(folder,"synthetic.webp.bak").exists()); assertFalse(new File(folder,"synthetic.webp.part").exists());
    }
    public static class Fixture extends DocumentsProvider {
        File root; int writes; boolean rejectRename, rejectCommit;
        static final String[] DEFAULT = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        File file(String id) { return id.equals("root") ? root : new File(root, id.substring(5)); }
        String id(File file) { String rel = root.toPath().relativize(file.toPath()).toString(); return rel.isEmpty() ? "root" : "root/" + rel; }
        @Override public boolean onCreate() { return true; }
        @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(projection == null ? new String[]{DocumentsContract.Root.COLUMN_ROOT_ID} : projection); }
        @Override public Cursor queryDocument(String id, String[] projection) { MatrixCursor c = new MatrixCursor(projection == null ? DEFAULT : projection); row(c, file(id)); return c; }
        @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) {
            MatrixCursor c = new MatrixCursor(projection == null ? DEFAULT : projection); File[] files = file(parent).listFiles(); if (files != null) for (File f : files) row(c, f); return c;
        }
        void row(MatrixCursor cursor, File f) {
            Object[] data = new Object[cursor.getColumnCount()]; String[] columns = cursor.getColumnNames();
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) data[i] = id(f);
                else if (columns[i].equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) data[i] = f.getName();
                else if (columns[i].equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) data[i] = f.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "image/webp";
            }
            cursor.addRow(data);
        }
        @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
            if (!mode.equals("r")) writes++;
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
        }
        @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
            File file = new File(file(parent), name);
            try { if (mime.equals(DocumentsContract.Document.MIME_TYPE_DIR)) { if (!file.mkdir()) throw new IOException(); } else if (!file.createNewFile()) throw new IOException(); }
            catch (IOException error) { throw new FileNotFoundException(); }
            return id(file);
        }
        @Override public String renameDocument(String old, String name) throws FileNotFoundException {
            if (rejectRename || rejectCommit && old.endsWith(".part")) throw new UnsupportedOperationException();
            File target = new File(file(old).getParentFile(), name); if (!file(old).renameTo(target)) throw new FileNotFoundException(); return id(target);
        }
        @Override public void deleteDocument(String id) throws FileNotFoundException { if (!file(id).delete()) throw new FileNotFoundException(); }
        @Override public boolean isChildDocument(String parent, String child) { return child.startsWith(parent + "/"); }
    }
}
