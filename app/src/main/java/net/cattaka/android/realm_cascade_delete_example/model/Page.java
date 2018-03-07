package net.cattaka.android.realm_cascade_delete_example.model;

import io.realm.RealmObject;

/**
 * Created by cattaka on 18/02/16.
 */

public class Page extends RealmObject {
    private String text;

    public Page() {
    }

    public Page(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
