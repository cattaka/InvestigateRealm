package net.cattaka.android.investigaterealm.model;

import io.realm.RealmList;
import io.realm.RealmObject;

/**
 * Created by cattaka on 18/02/16.
 */

public class Chapter extends RealmObject {
    private String title;
    private RealmList<Page> pages;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public RealmList<Page> getPages() {
        return pages;
    }

    public void setPages(RealmList<Page> pages) {
        this.pages = pages;
    }
}
