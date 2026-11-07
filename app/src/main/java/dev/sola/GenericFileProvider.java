package dev.sola;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal file provider allowing recorded CAN sessions to be exported/shared
 * without pulling in AndroidX dependencies.
 */
public final class GenericFileProvider extends ContentProvider {

    public static final String AUTHORITY = "dev.sola.files";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File dir = getContext().getExternalFilesDir("can_recordings");
        if (dir == null) {
            dir = new File(getContext().getFilesDir(), "can_recordings");
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            throw new FileNotFoundException("No file name specified");
        }
        File target = new File(dir, fileName);
        if (!target.exists()) {
            throw new FileNotFoundException("File not found: " + target.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            return null;
        }
        File dir = getContext().getExternalFilesDir("can_recordings");
        if (dir == null) {
            dir = new File(getContext().getFilesDir(), "can_recordings");
        }
        File target = new File(dir, fileName);
        MatrixCursor cursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        });
        cursor.addRow(new Object[]{
                target.getName(),
                target.exists() ? target.length() : 0
        });
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        if (path != null && path.endsWith(".csv")) {
            return "text/csv";
        }
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static Uri getUriForFile(File file) {
        return Uri.parse("content://" + AUTHORITY + "/can_recordings/" + file.getName());
    }
}
