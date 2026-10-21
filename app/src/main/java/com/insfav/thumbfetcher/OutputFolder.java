package com.insfav.thumbfetcher;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

/** SAF only. No broad storage permission and no queue files in the output folder. */
final class OutputFolder {
    private final ContentResolver resolver;
    private final Uri tree, folder;
    private final Map<String, Uri> files = new HashMap<>();
    private final QueueStore catalog;
    private final String catalogScope;
    OutputFolder(Context context, String treeValue) throws IOException {
        this(context, treeValue, null, false);
    }
    OutputFolder(Context context, String treeValue, QueueStore catalog, boolean verify) throws IOException {
        resolver = context.getContentResolver(); tree = Uri.parse(treeValue);
        this.catalog = catalog;
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
        catalogScope = tree.toString() + "\n" + DocumentsContract.getDocumentId(folder);
        if (catalog == null) files.putAll(children(folder));
        else if (verify || !("1".equals(catalog.meta("ready")) && catalogScope.equals(catalog.meta("folder")))) indexCatalog(catalog.users(false));
        else for (String user : catalog.users(false)) if (catalog.image(user) == null) catalog.image(new QueueStore.Image(user, "", user + ".webp", 0, 0, 0, 0, false));
    }

    /** Full SAF enumeration is performed once per output-folder scope or explicit verification. */
    private void indexCatalog(List<String> users) throws IOException {
        Map<String, Uri> found = children(folder);
        files.putAll(found);
        Set<String> known = new HashSet<>();
        for (Map.Entry<String, Uri> item : found.entrySet()) {
            String filename=item.getKey();
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".webp")) continue;
            String user=Identity.normalize(filename.substring(0,filename.length()-5));
            if (!Identity.valid(user)) continue;
            Uri uri=item.getValue();
            QueueStore.Image previous=catalog.image(user);
            byte[] data=null;boolean readable;
            try { data=read(resolver.openInputStream(uri));readable=decodableWebp(data); }
            catch (FileNotFoundException missing) { readable=false; }
            long size=-1, modified=0;
            try (Cursor c=resolver.query(uri,new String[]{DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null)) {
                if(c!=null&&c.moveToFirst()){size=c.isNull(0)?-1:c.getLong(0);modified=c.isNull(1)?0:c.getLong(1);}
            }
            int width=0,height=0;
            if(readable){android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,bounds);width=bounds.outWidth;height=bounds.outHeight;}
            if(previous==null||!previous.uri.equals(uri.toString())||previous.size!=size||previous.modified!=modified||previous.readable!=readable||previous.width!=width||previous.height!=height)
                catalog.image(new QueueStore.Image(user,uri.toString(),filename,size,width,height,modified,readable));
            if(!readable)catalog.mark(user,"PENDING","INVALID_LOCAL_THUMBNAIL");
            known.add(user);
        }
        catalog.clearMissingImages(known);
        for(String user:users)if(!known.contains(user))catalog.image(new QueueStore.Image(user,"",user+".webp",0,0,0,0,false));
        catalog.meta("folder",catalogScope);catalog.meta("ready","1");catalog.settleImages();
    }

    private Uri lookup(String filename) throws IOException {
        if(catalog==null)return files.get(filename);
        String user=Identity.normalize(filename.substring(0,filename.length()-5));
        QueueStore.Image image=catalog.image(user);
        if(image==null)return null;
        if(image.needsCheck){catalog.image(new QueueStore.Image(user,image.uri,image.name,image.size,image.width,image.height,image.modified,false));return null;}
        if(image.uri.isEmpty())return null;
        Uri uri=Uri.parse(image.uri);files.put(filename,uri);return uri;
    }
    private static boolean decodableWebp(byte[] bytes) {
        if(bytes==null||bytes.length<20||bytes.length>ThumbnailCodec.MAX_OUTPUT||!"RIFF".equals(new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII))||!"WEBP".equals(new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII)))return false;
        long size=0;for(int i=0;i<4;i++)size|=(long)(bytes[4+i]&255)<<(8*i);if(size+8!=bytes.length)return false;
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(!"image/webp".equals(bounds.outMimeType)||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>16384||bounds.outHeight>16384||(long)bounds.outWidth*bounds.outHeight>40000000)return false;
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)return false;bitmap.recycle();return true;
    }
    private Uri findExact(String name) throws IOException {
        Uri child=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(folder));
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        Bundle args=new Bundle();args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,DocumentsContract.Document.COLUMN_DISPLAY_NAME+"=?");args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{name});
        try(Cursor c=resolver.query(child,columns,args,null)){
            if(c==null)throw new IOException("OUTPUT_UNAVAILABLE");
            while(c.moveToNext())if(name.equals(c.getString(1))&&!DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))){Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));files.put(name,uri);return uri;}
        }
        return null;
    }
    private Map<String, Uri> children(Uri directory) throws IOException {
        Uri child = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(directory));
        Map<String, Uri> result = new HashMap<>();
        try (Cursor cursor = resolver.query(child, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null)) {
            if (cursor == null) throw new IOException("OUTPUT_UNAVAILABLE");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if(name.toLowerCase(Locale.ROOT).endsWith(".webp")){String user=Identity.normalize(name.substring(0,name.length()-5));if(Identity.valid(user))name=user+".webp";}
                if (result.containsKey(name)) {
                    // Multiple case variants refer to one account. Keep an existing readable candidate.
                    Uri next=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));
                    boolean currentValid=ThumbnailCodec.valid(read(resolver.openInputStream(result.get(name))));
                    if(!currentValid&&ThumbnailCodec.valid(read(resolver.openInputStream(next))))result.put(name,next);
                    continue;
                }
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
        Uri file = lookup(filename); if (file == null) return false;
        try {
            byte[] bytes=read(resolver.openInputStream(file));boolean valid=catalog==null?ThumbnailCodec.valid(bytes):decodableWebp(bytes);
            if(catalog!=null){String user=Identity.normalize(filename.substring(0,filename.length()-5));android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);QueueStore.Image old=catalog.image(user);long modified=old==null?0:old.modified;catalog.image(new QueueStore.Image(user,file.toString(),filename,bytes.length,bounds.outWidth,bounds.outHeight,modified,valid));}
            return valid;
        }
        catch (FileNotFoundException error) {
            if(catalog!=null){String user=Identity.normalize(filename.substring(0,filename.length()-5));files.remove(filename);catalog.image(new QueueStore.Image(user,"",filename,0,0,0,0,false));return false;}
            throw new IOException("OUTPUT_UNAVAILABLE", error);
        }
        catch (IOException error) { if ("OUTPUT_SIZE_REJECTED".equals(error.getMessage())) return false; throw error; }
    }
    void recover(String filename) throws IOException {
        Uri backup = files.get(filename + ".bak"); if(backup==null&&catalog!=null)backup=findExact(filename+".bak");
        if (backup != null) {
            if (valid(filename)) {
                if (!DocumentsContract.deleteDocument(resolver, backup)) throw new IOException("OUTPUT_BACKUP_CLEANUP_FAILED");
            } else {
                Uri bad = files.remove(filename);
                if (bad != null && !DocumentsContract.deleteDocument(resolver, bad)) throw new IOException("OUTPUT_RESTORE_FAILED");
                Uri restored = DocumentsContract.renameDocument(resolver, backup, filename);
                if (restored == null) throw new IOException("OUTPUT_RESTORE_FAILED");
                exactName(restored, filename); files.put(filename, restored);
            }
            files.remove(filename + ".bak");
        }
        Uri partial = files.get(filename + ".part"); if(partial==null&&catalog!=null)partial=findExact(filename+".part");
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
    byte[] thumbnail(String filename) throws IOException {
        Uri file = lookup(filename); return file == null ? null : read(resolver.openInputStream(file));
    }
    boolean write(String filename, byte[] bytes) throws IOException {
        if (!ThumbnailCodec.valid(bytes)) throw new IOException("OUTPUT_INVALID_WEBP");
        Uri current=lookup(filename);
        if(current!=null)files.put(filename,current);
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
        Uri old = files.get(filename), backup = null;
        if (old != null) {
            if (valid(filename)) {
                // Keep a complete original until the replacement is committed and verified.
                try { backup = DocumentsContract.renameDocument(resolver, old, filename + ".bak"); }
                catch (UnsupportedOperationException error) { throw new IOException("ATOMIC_RENAME_UNSUPPORTED", error); }
                if (backup == null) throw new IOException("OUTPUT_BACKUP_FAILED");
                files.remove(filename); files.put(filename + ".bak", backup); exactName(backup, filename + ".bak");
            } else {
                if (!DocumentsContract.deleteDocument(resolver, old)) throw new IOException("OUTPUT_REPLACE_FAILED"); files.remove(filename);
            }
        }
        try {
            Uri committed;
            try { committed = DocumentsContract.renameDocument(resolver, pending, filename); }
            catch (UnsupportedOperationException error) { throw new IOException("ATOMIC_RENAME_UNSUPPORTED", error); }
            if (committed == null) throw new IOException("ATOMIC_RENAME_UNSUPPORTED");
            files.remove(filename + ".part"); files.put(filename, committed); exactName(committed, filename);
            if (!valid(filename)) throw new IOException("OUTPUT_FINAL_VERIFY_FAILED");
        } catch (IOException | SecurityException error) {
            // If access is still available, restore now. Otherwise next open recovers the backup.
            if (backup != null) try { recover(filename); } catch (IOException | RuntimeException ignored) { }
            throw error;
        }
        if (backup != null) {
            if (!DocumentsContract.deleteDocument(resolver, backup)) throw new IOException("OUTPUT_BACKUP_CLEANUP_FAILED");
            files.remove(filename + ".bak");
        }
        if(catalog!=null){String user=Identity.normalize(filename.substring(0,filename.length()-5));android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);long size=bytes.length;long modified=System.currentTimeMillis();catalog.image(new QueueStore.Image(user,files.get(filename).toString(),filename,size,bounds.outWidth,bounds.outHeight,modified,true));}
        return true;
    }
}
