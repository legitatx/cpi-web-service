package me.legit.models.igloo;

import me.legit.models.igloo.decoration.DecorationLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MutableSceneLayout {

    public String name;
    public String zoneId;
    private int lightingId;
    private int musicId;
    private Map<String, String> extraInfo;
    private List<DecorationLayout> decorationsLayout;

    MutableSceneLayout(String name, String zoneId, int lightingId, int musicId, Map<String, String> extraInfo, List<DecorationLayout> decorationsLayout) {
        this.name = name;
        this.zoneId = zoneId;
        this.lightingId = lightingId;
        this.musicId = musicId;
        this.extraInfo = extraInfo;
        this.decorationsLayout = decorationsLayout;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public int getLightingId() {
        return lightingId;
    }

    public void setLightingId(int lightingId) {
        this.lightingId = lightingId;
    }

    public int getMusicId() {
        return musicId;
    }

    public void setMusicId(int musicId) {
        this.musicId = musicId;
    }

    public Map<String, String> getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(Map<String, String> extraInfo) {
        this.extraInfo = extraInfo;
    }

    public List<DecorationLayout> getDecorationsLayout() {
        return decorationsLayout;
    }

    public void setDecorationsLayout(List<DecorationLayout> decorationsLayout) {
        this.decorationsLayout = decorationsLayout;
    }
}
