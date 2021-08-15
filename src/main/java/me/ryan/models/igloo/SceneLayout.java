package me.legit.models.igloo;

import me.legit.models.igloo.decoration.DecorationLayout;

import java.util.List;
import java.util.Map;

public class SceneLayout extends MutableSceneLayout {

    private Long createdDate;
    private Long lastModifiedDate;
    private boolean memberOnly;

    SceneLayout(String name, String zoneId, int lightingId, int musicId, Map<String, String> extraInfo, List<DecorationLayout> decorationsLayout, Long createdDate, Long lastModifiedDate, boolean memberOnly) {
        super(name, zoneId, lightingId, musicId, extraInfo, decorationsLayout);
        this.createdDate = createdDate;
        this.lastModifiedDate = lastModifiedDate;
        this.memberOnly = memberOnly;
    }

    public Long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Long createdDate) {
        this.createdDate = createdDate;
    }

    public Long getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Long lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public boolean isMemberOnly() {
        return memberOnly;
    }

    public void setMemberOnly(boolean memberOnly) {
        this.memberOnly = memberOnly;
    }
}
