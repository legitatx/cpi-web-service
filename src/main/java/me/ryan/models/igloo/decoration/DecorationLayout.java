package me.legit.models.igloo.decoration;

import me.legit.models.decoration.DecorationType;
import me.legit.utils.Quaternion;
import me.legit.utils.Vec3D;

import java.util.Map;

public class DecorationLayout {

    public DecorationLayout.Id id;
    public short type;
    public long definitionId;
    private Vec3D position;
    private Quaternion rotation;
    private float scale;
    private Vec3D normal;

    private Map<String, String> customProperties;

    private class Id {
        public String name;
        private String parent;

        public Id(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }
    }

    public DecorationLayout(Id id, short type, long definitionId, Vec3D position, Quaternion rotation, float scale, Vec3D normal, Map<String, String> customProperties) {
        this.id = id;
        this.type = type;
        this.definitionId = definitionId;
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
        this.normal = normal;
        this.customProperties = customProperties;
    }

    public Id getId() {
        return id;
    }

    public void setId(Id id) {
        this.id = id;
    }

    public short getType() {
        return type;
    }

    public void setType(short type) {
        this.type = type;
    }

    public long getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(long definitionId) {
        this.definitionId = definitionId;
    }

    public Vec3D getPosition() {
        return position;
    }

    public void setPosition(Vec3D position) {
        this.position = position;
    }

    public Quaternion getRotation() {
        return rotation;
    }

    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public Vec3D getNormal() {
        return normal;
    }

    public void setNormal(Vec3D normal) {
        this.normal = normal;
    }

    public Map<String, String> getCustomProperties() {
        return customProperties;
    }

    public void setCustomProperties(Map<String, String> customProperties) {
        this.customProperties = customProperties;
    }
}
