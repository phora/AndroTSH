package io.github.phora.androtsh.network;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by phora on 8/27/15.
 */
public class RequestData {
    public final static String crlf = "\r\n";
    public final static String hyphens = "--";
    public final static String boundary = "------------------------8977674a05eb9620";

    private DataOutputStream request;
    private ContentResolver cr;

    public RequestData(DataOutputStream request, ContentResolver cr) {
        this.request = request;
        this.cr = cr;
    }

    // http://stackoverflow.com/questions/566462/upload-files-with-httpwebrequest-multipart-form-data
    public void addFile(Uri fpath) throws IOException {
        //setup filename and say that octets follow
        Cursor fileInfo = cr.query(fpath, null, null, null, null);
        fileInfo.moveToFirst();
        String fname = fileInfo.getString(fileInfo.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        //long file_length = fileInfo.getLong(fileInfo.getColumnIndex(OpenableColumns.SIZE));
        fileInfo.close();

        String mimetype = cr.getType(fpath);
        if (mimetype == null) {
            mimetype = "application/octet-stream";
        }
        Log.d("NetworkManager", "Uploading " + fname);

        request.writeBytes("Content-Disposition: form-data; name=\"filedata\"; filename=\"" + fname + "\"" + crlf);
        request.writeBytes(String.format("Content-Type: %s", mimetype) + crlf);
        request.writeBytes(crlf);
        //request.flush();

        InputStream instream = cr.openInputStream(fpath);
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        byte[] bArray = null;

        int readed = 0;
        byte[] buffer = new byte[1024];
        while(readed != -1) {
            try {
                readed = instream.read(buffer);
                if(readed != -1)
                    outstream.write(buffer,0,readed);
            } catch (IOException e) {
                e.printStackTrace();
                readed = -1;
            }
        }
        bArray = outstream.toByteArray();
        outstream.close();
        instream.close();

        //write image data
        request.write(bArray);
        Log.d("NetworkManager", "Got data? " + (bArray != null));

        //finish the format http post packet
        request.writeBytes(crlf);
        request.writeBytes(hyphens + boundary + crlf);
        //oldflush
    }
}
