package me.legit.models.igloo;

import com.google.gson.Gson;
import me.legit.models.igloo.decoration.DecorationLayout;

import java.util.List;
import java.util.Map;

public class SavedSceneLayout extends SceneLayout {

    private long layoutId;

    public SavedSceneLayout(String name, String zoneId, int lightingId, int musicId, Map<String, String> extraInfo, List<DecorationLayout> decorationsLayout, Long createdDate, Long lastModifiedDate, boolean memberOnly, long layoutId) {
        super(name, zoneId, lightingId, musicId, extraInfo, decorationsLayout, createdDate, lastModifiedDate, memberOnly);
        this.layoutId = layoutId;
    }

    public long getLayoutId() {
        return layoutId;
    }

    public void setLayoutId(long layoutId) {
        this.layoutId = layoutId;
    }

    public String toJson(Gson gson) {
        return gson.toJson(this);
    }
}
