package net.cattaka.android.realm_cascade_delete_example;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import net.cattaka.android.realm_cascade_delete_example.databinding.ActivityMainBinding;
import net.cattaka.android.realm_cascade_delete_example.model.Book;
import net.cattaka.android.realm_cascade_delete_example.model.Chapter;
import net.cattaka.android.realm_cascade_delete_example.model.IndexItem;
import net.cattaka.android.realm_cascade_delete_example.model.Page;

import java.util.Collection;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmInspector;
import io.realm.RealmList;
import io.realm.RealmModel;

public class MainActivity extends AppCompatActivity {
    public static final RealmConfiguration REALM_CONFIGURATION = new RealmConfiguration.Builder()
            .name(Constants.DB_NAME)
            .schemaVersion(1)
            .build();

    private ActivityMainBinding mBinding;
    private Realm mRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mBinding.setActivity(this);

        mRealm = Realm.getInstance(REALM_CONFIGURATION);
    }

    public void onClickHoge(View v) {
        Book book = new Book();
        Chapter chapter = new Chapter();
        Page page1 = new Page();
        Page page2 = new Page();
        Page page3 = new Page();
        IndexItem indexItem = new IndexItem();
        indexItem.setName("HogeHoge");
        indexItem.setPage(page2);
        chapter.setTitle("Chap1");
        chapter.setPages(new RealmList<Page>(page1, page2, page3));
        book.setChapters(new RealmList<Chapter>(chapter));
        book.setIndexItems(new RealmList<IndexItem>(indexItem));

        book.setId(SystemClock.currentThreadTimeMillis());
        mRealm.beginTransaction();
        try {
            mRealm.insert(book);
            mRealm.where(Book.class).equalTo("id", book.getId()).findAll().deleteAllFromRealm();
            mRealm.commitTransaction();
        } finally {
            if (mRealm.isInTransaction()) {
                mRealm.cancelTransaction();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Collection<RealmInspector.ModelDef> modelDefs = RealmInspector.obtainModelDefs(mRealm);
        Log.d("test", modelDefs.toString());

        for (RealmInspector.ModelDef md : modelDefs) {
            Collection<? extends RealmModel> unusedObjects = RealmInspector.findUnusedObjects(mRealm, md);
            Log.d("test", md.toString() + unusedObjects);
        }
    }


}
