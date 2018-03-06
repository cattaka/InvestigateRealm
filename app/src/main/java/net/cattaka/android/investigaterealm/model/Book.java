package net.cattaka.android.investigaterealm.model;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by cattaka on 18/02/16.
 */

public class Book extends RealmObject {
    @PrimaryKey
    private long id;

    private String title;
    private RealmList<Chapter> chapters;
    private RealmList<IndexItem> indexItems;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public RealmList<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(RealmList<Chapter> chapters) {
        this.chapters = chapters;
    }

    public RealmList<IndexItem> getIndexItems() {
        return indexItems;
    }

    public void setIndexItems(RealmList<IndexItem> indexItems) {
        this.indexItems = indexItems;
    }
}
