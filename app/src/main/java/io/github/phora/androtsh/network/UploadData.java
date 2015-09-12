package io.github.phora.androtsh.network;

/**
 * Created by phora on 8/24/15.
 */
public class UploadData {
    private String server_url;
    private String token;
    private String fpath;

    public UploadData(String server_url, String token, String fpath) {
        this.server_url = server_url;
        this.token = token;
        this.fpath = fpath;
    }

    public String getServerUrl() {
        return server_url;
    }

    public void setServerUrl(String server_url) {
        this.server_url = server_url;
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
