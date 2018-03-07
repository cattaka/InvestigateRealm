package net.cattaka.android.realm_cascade_delete_example.model;

import io.realm.RealmObject;

/**
 * Created by cattaka on 18/02/16.
 */

public class IndexItem extends RealmObject {
    private String name;
    private Page page;

    public IndexItem() {
    }

    public IndexItem(String name, Page page) {
        this.name = name;
        this.page = page;
    }

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
