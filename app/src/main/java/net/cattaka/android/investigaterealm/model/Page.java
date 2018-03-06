package net.cattaka.android.investigaterealm.model;

import io.realm.RealmObject;

/**
 * Created by cattaka on 18/02/16.
 */

public class Page extends RealmObject {
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
