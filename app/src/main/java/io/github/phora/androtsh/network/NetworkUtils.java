package io.github.phora.androtsh.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by phora on 8/24/15.
 */
public class NetworkUtils {

    private static NetworkUtils nm;
    private static final String FULL_SPLITTY = "(%s)/([a-zA-Z0-9]+)/([^/]*)";

    private Context context;


    public static NetworkUtils getInstance(Context ctxt) {
        if (nm == null) {
            nm = new NetworkUtils(ctxt);
        }
        return nm;
    }

    private NetworkUtils(Context context) {
        this.context = context;
    }

    public HttpURLConnection openConnection(String serverPath) {

        URL url = null;

        try {
            url = new URL(serverPath);
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        }

        HttpURLConnection conn;

        try {
            if (isConnectedToInternet(context)) {

                if (url != null) {
                    conn = (HttpURLConnection) url.openConnection();
                } else {
                    //listen
                    return null;
                }

                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    serverPath = loc; //needed?
                    url = new URL(loc);
                }
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setUseCaches(false);
                conn.setDoInput(true);
                conn.setDoOutput(true);

                conn.setRequestProperty("User-Agent", "AndroTSH");
                conn.setRequestProperty("Expect", "100-continue");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + RequestData.boundary);

                return conn;
            }
        }
        catch (IOException e) {
            //some error handling
            Log.d("NetworkManager", "Failed uploads: "+e.getMessage());
            return null;
        }

        return null;
    }

    public List<UploadData> getUploadResults(String serverPath, HttpURLConnection conn) throws IOException {
        LinkedList<UploadData> output = new LinkedList<UploadData>();

        //should we flush request manually before going in here?
        conn.getOutputStream().flush();
        InputStream stream = conn.getInputStream();

        if (stream != null) {
            Log.d("NetworkManager", "Got response for uploads, reading now");
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            boolean isReading = true;
            String data;
            String lastToken = null;
            String lastServer = null;
            boolean thisIsGroup = false;
            Pattern p = Pattern.compile(String.format(FULL_SPLITTY, Pattern.quote(serverPath)));

            do {
                try {
                    data = br.readLine();
                    if (data != null) {
                        Matcher m = p.matcher(data);
                        m.matches();
                        UploadData ud = new UploadData(m.group(1), m.group(2), m.group(3));
                        if (lastToken != null) {
                            if (m.group(2).equals(lastToken)) {
                                thisIsGroup = true;
                            }
                        }
                        else {
                            lastToken = m.group(2);
                            lastServer = m.group(1);
                        }
                        output.add(ud);
                    }
                    else
                        isReading = false;
                } catch (IOException e) {
                    e.printStackTrace();
                    isReading = false;
                }
            } while (isReading);

            if (thisIsGroup) {
                output.addFirst(new UploadData(lastServer, lastToken, null));
            }


            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("NetworkManager", "Finished uploads");
        return output;
    }

    //https://github.com/Schoumi/Goblim/blob/master/app/src/main/java/fr/mobdev/goblim/NetworkManager.java
    private boolean isConnectedToInternet(Context context)
    {
        //verify the connectivity
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null)
        {
            NetworkInfo.State networkState = networkInfo.getState();
            if (networkState.equals(NetworkInfo.State.CONNECTED))
            {
                return true;
            }
        }
        return false;
    }
}
