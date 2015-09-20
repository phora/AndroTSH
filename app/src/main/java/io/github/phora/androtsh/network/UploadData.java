package io.github.phora.androtsh.network;

/**
 * Created by phora on 8/24/15.
 */
public class UploadData {
    private String serverUrl;
    private String token;
    private String fpath;

    public UploadData(String serverUrl, String token, String fpath) {
        this.serverUrl = serverUrl;
        this.token = token;
        this.fpath = fpath;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String server_url) {
        this.serverUrl = server_url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFPath() {
        return fpath;
    }

    public void setFPath(String fpath) {
        this.fpath = fpath;
    }
}
