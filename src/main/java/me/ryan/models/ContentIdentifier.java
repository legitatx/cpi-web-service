package me.legit.models;

public class ContentIdentifier {

    private String clientVersion;
    private String contentVersion;
    private String subContentVersion;

    public ContentIdentifier(String clientVersion, String contentVersion, String subContentVersion) {
        this.clientVersion = clientVersion;
        this.contentVersion = contentVersion;
        this.subContentVersion = subContentVersion;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public String getSubContentVersion() {
        return subContentVersion;
    }

    public void setSubContentVersion(String subContentVersion) {
        this.subContentVersion = subContentVersion;
    }
}
