package net.cattaka.android.investigaterealm;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import net.cattaka.android.investigaterealm.databinding.ActivityMainBinding;
import net.cattaka.android.investigaterealm.model.Book;
import net.cattaka.android.investigaterealm.model.Chapter;
import net.cattaka.android.investigaterealm.model.IndexItem;
import net.cattaka.android.investigaterealm.model.Page;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.MyDefaultRealmModuleMediator;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmFieldType;
import io.realm.RealmList;
import io.realm.RealmModel;
import io.realm.RealmObjectSchema;
import io.realm.RealmResults;
import io.realm.RealmSchema;
import io.realm.internal.ColumnInfo;
import io.realm.internal.OsSchemaInfo;

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
        Collection<ModelDef> modelDefs = obtainModelDefs(mRealm);
        Log.d("test", modelDefs.toString());

        for (ModelDef md : modelDefs) {
            List<? extends RealmModel> unusedObjects = findUnusedObjects(mRealm, md);
            Log.d("test", md.toString() + unusedObjects);
        }
    }

    public static List<? extends RealmModel> findUnusedObjects(@NonNull Realm realm, @NonNull ModelDef md) {
        List<? extends RealmModel> results = new ArrayList<>(realm.where(md.clazz).findAll());
        for (ColumnDef revColumnDef : md.revObjectFields) {
            RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
            for (RealmModel parent : parents) {
                RealmModel child = MyDefaultRealmModuleMediator.getColumnObject(parent, revColumnDef.columnDetails.columnIndex, md.clazz, false, Collections.<String>emptyList());
                if (child != null) {
                    results.remove(child);
                }
            }
        }
        for (ColumnDef revColumnDef : md.revListFields) {
            RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
            for (RealmModel parent : parents) {
                RealmList<? extends RealmModel> children = MyDefaultRealmModuleMediator.getObjects(parent, revColumnDef.columnDetails.columnIndex, md.clazz);
                if (children != null) {
                    results.removeAll(children);
                }
            }
        }
        return results;
    }

    public static Collection<ModelDef> obtainModelDefs(@NonNull Realm realm) {
        MyDefaultRealmModuleMediator mediator = new MyDefaultRealmModuleMediator();
        Map<String, ModelDef> name2ModelDef = new HashMap<>();
        OsSchemaInfo schemaInfo = realm.sharedRealm.getSchemaInfo();
        {   // List up RealmModel classes
            Map<String, Class<? extends RealmModel>> name2Class = new HashMap<>();
            {
                for (Class<? extends RealmModel> clazz : mediator.getModelClasses()) {
                    name2Class.put(clazz.getSimpleName(), clazz);
                }
            }
            RealmSchema schema = realm.getSchema();
            Set<RealmObjectSchema> objectSchemas = schema.getAll();
            for (RealmObjectSchema os : objectSchemas) {
                Class<? extends RealmModel> clazz = name2Class.get(os.getClassName());
                if (clazz != null) {
                    ModelDef modelDef = new ModelDef(os, clazz);
                    name2ModelDef.put(os.getClassName(), modelDef);
                }
            }
        }
        {   // Find forward relations
            for (ModelDef modelDef : name2ModelDef.values()) {
                ColumnInfo columnInfo = mediator.createColumnInfo(modelDef.clazz, schemaInfo);
                for (Map.Entry<String, ColumnInfo.ColumnDetails> entry : columnInfo.getIndicesMap().entrySet()) {
                    ColumnInfo.ColumnDetails columnDetails = entry.getValue();
                    ModelDef md = name2ModelDef.get(columnDetails.linkedClassName);
                    if (md != null) {
                        if (columnDetails.columnType == RealmFieldType.OBJECT) {
                            modelDef.objectFields.put(entry.getKey(), new ColumnDef(columnDetails, md));
                        } else if (columnDetails.columnType == RealmFieldType.LIST) {
                            modelDef.listFields.put(entry.getKey(), new ColumnDef(columnDetails, md));
                        }
                    }
                }
            }
        }
        {   // Find backward relations
            for (ModelDef modelDef : name2ModelDef.values()) {
                for (ColumnDef child : modelDef.objectFields.values()) {
                    child.modelDef.revObjectFields.add(new ColumnDef(child.columnDetails, modelDef));
                }
                for (ColumnDef child : modelDef.listFields.values()) {
                    child.modelDef.revListFields.add(new ColumnDef(child.columnDetails, modelDef));
                }
            }
        }
        return name2ModelDef.values();
    }

    public static class ColumnDef {
        ColumnInfo.ColumnDetails columnDetails;
        ModelDef modelDef;

        public ColumnDef(@NonNull ColumnInfo.ColumnDetails columnDetails, @NonNull ModelDef modelDef) {
            this.columnDetails = columnDetails;
            this.modelDef = modelDef;
        }

        @Override
        public String toString() {
            return "ColumnDef{" +
                    "columnDetails=" + columnDetails +
                    ", modelDef=" + modelDef +
                    '}';
        }
    }

    public static class ModelDef {
        RealmObjectSchema objectSchema;
        Class<? extends RealmModel> clazz;
        Map<String, ColumnDef> objectFields;
        Map<String, ColumnDef> listFields;
        Set<ColumnDef> revObjectFields;
        Set<ColumnDef> revListFields;

        public ModelDef(@NonNull RealmObjectSchema objectSchema, @NonNull Class<? extends RealmModel> clazz) {
            this.objectSchema = objectSchema;
            this.clazz = clazz;
            objectFields = new HashMap<>();
            listFields = new HashMap<>();
            revObjectFields = new HashSet<>();
            revListFields = new HashSet<>();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ModelDef modelDef = (ModelDef) o;

            return clazz.equals(modelDef.clazz);
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        @Override
        public String toString() {
            return "ModelDef{" +
                    "clazz=" + clazz.getSimpleName() +
                    ", objectFields=" + objectFields +
                    ", listFields=" + listFields +
//                    ", revObjectFields=" + revObjectFields +
//                    ", revListFields=" + revListFields +
                    '}';
        }
    }
}
