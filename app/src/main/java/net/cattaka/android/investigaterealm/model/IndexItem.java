package net.cattaka.android.investigaterealm.model;

import io.realm.RealmObject;

/**
 * Created by cattaka on 18/02/16.
 */

public class IndexItem extends RealmObject {
    private String name;
    private Page page;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }
}
