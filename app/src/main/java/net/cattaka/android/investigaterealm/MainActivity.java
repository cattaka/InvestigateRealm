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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.realm.MyDefaultRealmModuleMediator;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmModel;
import io.realm.RealmObjectSchema;
import io.realm.RealmResults;
import io.realm.RealmSchema;

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
    }

    public static void findUnusedObjects(@NonNull Realm realm, @NonNull ModelDef md) {
        RealmResults results = realm.where(md.clazz).findAll();
        for (ModelDef parent : md.revObjectFields) {
        }
    }

    public static Collection<ModelDef> obtainModelDefs(@NonNull Realm realm) {
        Map<String, ModelDef> name2ModelDef = new HashMap<>();
        {
            Map<String, Class<? extends RealmModel>> name2Class = new HashMap<>();
            {
                MyDefaultRealmModuleMediator mediator = new MyDefaultRealmModuleMediator();
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
        for (ModelDef modelDef : name2ModelDef.values()) {
            Set<String> fieldNames = modelDef.objectSchema.getFieldNames();
            for (Method method : modelDef.clazz.getMethods()) {
                if (method.getParameterTypes().length > 0
                        || !method.getName().startsWith("get")
                        || method.getName().length() < 4) {
                    continue;
                }
                String name = method.getName().substring(3);
                name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                if (!fieldNames.contains(name)) {
                    continue;
                }

                if (RealmList.class.isAssignableFrom(method.getReturnType())) {
                    if (method.getGenericReturnType() instanceof ParameterizedType) {
                        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
                        Type[] args = returnType.getActualTypeArguments();
                        Class argType = (args.length == 1 && args[0] instanceof Class) ? (Class) args[0] : null;
                        ModelDef md = (argType != null) ? name2ModelDef.get(argType.getSimpleName()) : null;
                        if (md != null) {
                            modelDef.listFields.put(name, md);
                        }
                    }
                }
                if (RealmModel.class.isAssignableFrom(method.getReturnType())) {
                    ModelDef md = name2ModelDef.get(method.getReturnType().getSimpleName());
                    if (md != null) {
                        modelDef.objectFields.put(name, md);
                    }
                }
            }
        }
        for (ModelDef modelDef : name2ModelDef.values()) {
            for (ModelDef child : modelDef.objectFields.values()) {
                child.revObjectFields.add(modelDef);
            }
            for (ModelDef child : modelDef.listFields.values()) {
                child.revListFields.add(modelDef);
            }
        }
        return name2ModelDef.values();
    }

    public static class ModelDef {
        RealmObjectSchema objectSchema;
        Class<? extends RealmModel> clazz;
        Map<String, ModelDef> objectFields;
        Map<String, ModelDef> listFields;
        Set<ModelDef> revObjectFields;
        Set<ModelDef> revListFields;

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
    }
}
