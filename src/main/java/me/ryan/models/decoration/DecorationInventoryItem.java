package me.legit.models.decoration;

public class DecorationInventoryItem {

    private DecorationId decorationId;
    private Number count;

    public DecorationInventoryItem(DecorationId decorationId, Number count) {
        this.decorationId = decorationId;
        this.count = count;
    }

    public DecorationId getDecorationId() {
        return decorationId;
    }

    public Number getCount() {
        return count;
    }
}
