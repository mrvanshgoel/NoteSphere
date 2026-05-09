package com.notesphere.app.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";
    public static final String BASE_DIRECTORY = "NoteSphereDocs";

    public static File getBaseDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File noteSphereDir = new File(downloadsDir, BASE_DIRECTORY);
        if (!noteSphereDir.exists()) {
            noteSphereDir.mkdirs();
        }
        return noteSphereDir;
    }

    public static File saveUriToLocal(Context context, Uri uri, String fileName) {
        try {
            File destFile = new File(getBaseDir(), fileName);
            InputStream in = context.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(destFile);

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            return destFile;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file locally: " + e.getMessage());
            return null;
        }
    }

    public static boolean existsLocally(String fileName) {
        File file = new File(getBaseDir(), fileName);
        return file.exists();
    }

    public static File getLocalFile(String fileName) {
        return new File(getBaseDir(), fileName);
    }
}
