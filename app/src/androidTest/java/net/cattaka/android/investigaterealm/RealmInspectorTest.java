package net.cattaka.android.investigaterealm;

import net.cattaka.android.investigaterealm.model.Book;
import net.cattaka.android.investigaterealm.model.Chapter;
import net.cattaka.android.investigaterealm.model.IndexItem;
import net.cattaka.android.investigaterealm.model.Page;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmInspector;
import io.realm.RealmModel;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Created by cattaka on 18/03/07.
 */
public class RealmInspectorTest {
    private Realm mRealm;

    @Before
    public void setUp() throws Exception {
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name("RealmInspectorTest")
                .schemaVersion(1)
                .build();
        mRealm = Realm.getInstance(config);
        mRealm.beginTransaction();
        mRealm.deleteAll();
        mRealm.commitTransaction();
    }

    @Test
    public void testRun() {
        List<Page> pages = Arrays.asList(
                new Page("aaa"),
                new Page("aab"),
                new Page("aba"),    // not used
                new Page("aad"),    // will be deleted by cleanUp
                new Page("baa"),
                new Page("bab"),
                new Page("bba"),
                new Page("ccc"),
                new Page("ccd"),    // not used
                new Page("cdc"),    // not used
                new Page("cce"),    // not used
                new Page("dcc"),    // will be deleted by cleanUp
                new Page("dcd"),    // will be deleted by cleanUp
                new Page("ddc"),    // not used
                new Page("eee"),    // will be deleted by cleanUp
                new Page("eef"),    // will be deleted by cleanUp
                new Page("efe"),    // not used
                new Page("eeg"),    // not used
                new Page("fee"),    // will be deleted by cleanUp
                new Page("fef"),    // will be deleted by cleanUp
                new Page("ffe")     // not used
        );
        List<IndexItem> indexItems = Arrays.asList(
                new IndexItem("a_a", pages.get(0)),
                new IndexItem("b_a", pages.get(6)),
                new IndexItem("c_c", pages.get(7)),     // will be deleted by manually
                new IndexItem("d_c", pages.get(11)),    // will be deleted by manually
                new IndexItem("e_e", pages.get(14)),    // will be deleted by cleanUp
                new IndexItem("f_e", pages.get(18))     // will be deleted by cleanUp
        );
        List<Chapter> chapters = Arrays.asList(
                new Chapter("aa", Arrays.asList(pages.get(0), pages.get(1), pages.get(3))),
                new Chapter("ba", Arrays.asList(pages.get(4), pages.get(5))),
                new Chapter("cc", Arrays.asList(pages.get(7))),
                new Chapter("dc", Arrays.asList(pages.get(11), pages.get(12))), // will be deleted by manually
                new Chapter("ee", Arrays.asList(pages.get(14), pages.get(15))), // will be deleted by cleanUp
                new Chapter("fe", Arrays.asList(pages.get(18), pages.get(19)))  // will be deleted by cleanUp
        );
        List<Book> books = Arrays.asList(
                new Book(1, "AB", Arrays.asList(chapters.get(0), chapters.get(1)), Arrays.asList(indexItems.get(0), indexItems.get(1))),
                new Book(2, "DC", Arrays.asList(chapters.get(2), chapters.get(3)), Arrays.asList(indexItems.get(2), indexItems.get(3))),
                new Book(3, "FE", Arrays.asList(chapters.get(4), chapters.get(5)), Arrays.asList(indexItems.get(4), indexItems.get(5))) // will be deleted by manually
        );

        mRealm.beginTransaction();
        mRealm.insert(books);
        mRealm.commitTransaction();

        {   // Nothing are deleted
            Map<Class<? extends RealmModel>, Integer> results = RealmInspector.cleanUp(mRealm);
            for (Map.Entry<Class<? extends RealmModel>, Integer> entry : results.entrySet()) {
                assertThat("No " + entry.getKey().getSimpleName() + "should be deleted", entry.getValue(), is(0));
            }
        }

        {   // Delete some parent objects
            List<Book> realmBooks = mRealm.where(Book.class).findAll().sort("id");
            mRealm.beginTransaction();
            realmBooks.get(0).getChapters().get(0).getPages().remove(2);
            realmBooks.get(1).getChapters().get(1).deleteFromRealm();
            realmBooks.get(1).getIndexItems().get(1).deleteFromRealm();
            realmBooks.get(1).getIndexItems().get(0).deleteFromRealm();
            realmBooks.get(2).deleteFromRealm();
            mRealm.commitTransaction();
        }

        // Execute cleanUp
        Map<Class<? extends RealmModel>, Integer> results = RealmInspector.cleanUp(mRealm);

        {   // Check existence of objects
            // Book
            assertThat("Book id=1 should not be null", mRealm.where(Book.class).equalTo("id", 1).findFirst(), is(notNullValue()));
            assertThat("Book id=2 should not be null", mRealm.where(Book.class).equalTo("id", 2).findFirst(), is(notNullValue()));
            assertThat("Book id=3 should be null", mRealm.where(Book.class).equalTo("id", 3).findFirst(), is(nullValue()));
            // Chapter
            assertThat("Chapter title=aa should not be null", mRealm.where(Chapter.class).equalTo("title", "aa").findFirst(), is(notNullValue()));
            assertThat("Chapter title=ba should not be null", mRealm.where(Chapter.class).equalTo("title", "ba").findFirst(), is(notNullValue()));
            assertThat("Chapter title=cc should not be null", mRealm.where(Chapter.class).equalTo("title", "cc").findFirst(), is(notNullValue()));
            assertThat("Chapter title=dc should be null", mRealm.where(Chapter.class).equalTo("title", "dc").findFirst(), is(nullValue()));
            assertThat("Chapter title=ee should be null", mRealm.where(Chapter.class).equalTo("title", "ee").findFirst(), is(nullValue()));
            assertThat("Chapter title=fa should be null", mRealm.where(Chapter.class).equalTo("title", "fa").findFirst(), is(nullValue()));
            // IndexItem
            assertThat("IndexItem name=a_a should not be null", mRealm.where(IndexItem.class).equalTo("name", "a_a").findFirst(), is(notNullValue()));
            assertThat("IndexItem name=b_a should not be null", mRealm.where(IndexItem.class).equalTo("name", "b_a").findFirst(), is(notNullValue()));
            assertThat("IndexItem name=c_c should be null", mRealm.where(IndexItem.class).equalTo("name", "c_c").findFirst(), is(nullValue()));
            assertThat("IndexItem name=d_c should be null", mRealm.where(IndexItem.class).equalTo("name", "d_c").findFirst(), is(nullValue()));
            assertThat("IndexItem name=e_e should be null", mRealm.where(IndexItem.class).equalTo("name", "e_e").findFirst(), is(nullValue()));
            assertThat("IndexItem name=f_a should be null", mRealm.where(IndexItem.class).equalTo("name", "f_a").findFirst(), is(nullValue()));
            // Page
            assertThat("Page text=aaa should not be null", mRealm.where(Page.class).equalTo("text", "aaa").findFirst(), is(notNullValue()));
            assertThat("Page text=aab should not be null", mRealm.where(Page.class).equalTo("text", "aab").findFirst(), is(notNullValue()));
            assertThat("Page text=aba should be null", mRealm.where(Page.class).equalTo("text", "aba").findFirst(), is(nullValue()));
            assertThat("Page text=aad should be null", mRealm.where(Page.class).equalTo("text", "aad").findFirst(), is(nullValue()));
            assertThat("Page text=baa should not be null", mRealm.where(Page.class).equalTo("text", "baa").findFirst(), is(notNullValue()));
            assertThat("Page text=bab should not be null", mRealm.where(Page.class).equalTo("text", "bab").findFirst(), is(notNullValue()));
            assertThat("Page text=bba should not be null", mRealm.where(Page.class).equalTo("text", "bba").findFirst(), is(notNullValue()));
            assertThat("Page text=ccc should not be null", mRealm.where(Page.class).equalTo("text", "ccc").findFirst(), is(notNullValue()));
            assertThat("Page text=ccd should be null", mRealm.where(Page.class).equalTo("text", "ccd").findFirst(), is(nullValue()));
            assertThat("Page text=cdc should be null", mRealm.where(Page.class).equalTo("text", "cdc").findFirst(), is(nullValue()));
            assertThat("Page text=cce should be null", mRealm.where(Page.class).equalTo("text", "cce").findFirst(), is(nullValue()));
            assertThat("Page text=dcc should be null", mRealm.where(Page.class).equalTo("text", "dcc").findFirst(), is(nullValue()));
            assertThat("Page text=dcd should be null", mRealm.where(Page.class).equalTo("text", "dcd").findFirst(), is(nullValue()));
            assertThat("Page text=ddc should be null", mRealm.where(Page.class).equalTo("text", "ddc").findFirst(), is(nullValue()));
            assertThat("Page text=eee should be null", mRealm.where(Page.class).equalTo("text", "eee").findFirst(), is(nullValue()));
            assertThat("Page text=eef should be null", mRealm.where(Page.class).equalTo("text", "eef").findFirst(), is(nullValue()));
            assertThat("Page text=efe should be null", mRealm.where(Page.class).equalTo("text", "efe").findFirst(), is(nullValue()));
            assertThat("Page text=eeg should be null", mRealm.where(Page.class).equalTo("text", "eeg").findFirst(), is(nullValue()));
            assertThat("Page text=fee should be null", mRealm.where(Page.class).equalTo("text", "fee").findFirst(), is(nullValue()));
            assertThat("Page text=fef should be null", mRealm.where(Page.class).equalTo("text", "fef").findFirst(), is(nullValue()));
            assertThat("Page text=ffe should be null", mRealm.where(Page.class).equalTo("text", "ffe").findFirst(), is(nullValue()));
        }
        {   // Check count of orphaned objects
            assertThat("2 Chapter should be deleted", results.get(Chapter.class), is(2));
            assertThat("2 IndexItem should be deleted", results.get(IndexItem.class), is(2));
            assertThat("7 Page should be deleted", results.get(Page.class), is(7));
        }
        {   // Check count of existing objects
            assertThat("There should be only 2 Book", mRealm.where(Book.class).count(), is(2L));
            assertThat("There should be only 3 Chapter", mRealm.where(Chapter.class).count(), is(3L));
            assertThat("There should be only 2 IndexItem", mRealm.where(IndexItem.class).count(), is(2L));
        }
    }
}
