package io.realm;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.internal.ColumnInfo;
import io.realm.internal.OsList;
import io.realm.internal.OsSchemaInfo;
import io.realm.internal.RealmInternalInspector;

/**
 * Created by cattaka on 18/03/07.
 */
public class RealmDirtyUtils {
    RealmConfiguration mConfiguration;
    List<ModelDef> mModelDefs;
    Map<Class<? extends RealmModel>, ModelDef> mClass2ModelDefs;

    public RealmDirtyUtils(RealmConfiguration configuration) {
        mConfiguration = configuration;
        Realm realm = Realm.getInstance(mConfiguration);
        mModelDefs = Collections.synchronizedList(sortByDependencies(obtainModelDefs(realm)));

        mClass2ModelDefs = new HashMap<>();
        for (ModelDef md : mModelDefs) {
            mClass2ModelDefs.put(md.clazz, md);
        }
    }

    /**
     * NOTE: This can not handle circular reference correctly
     */
    public void deleteFromRealmAndCleanUp(@NonNull RealmModel object) {
        Realm realm = Realm.getInstance(mConfiguration);
        List<RealmModel> descendants = new ArrayList<>();
        Set<RealmModel> visiteds = new HashSet<>();

        findDescendantsOrdered(descendants, visiteds, object);

        descendants.remove(object);
        RealmObject.deleteFromRealm(object);

        removeStillUsed:
        for (Iterator<RealmModel> itr = descendants.iterator(); itr.hasNext(); ) {
            RealmModel t = itr.next();
            ModelDef md = findModelDefByClass(t.getClass());
            if (md == null) {
                continue;  // Impossible
            }
            for (ColumnDef revColumnDef : md.revObjectFields) {
                RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
                for (RealmModel parent : parents) {
                    if (!descendants.contains(parent)) {
                        RealmModel child = getColumnObject(parent, revColumnDef.columnDetails.columnIndex, md.clazz, false, Collections.<String>emptyList());
                        if (child != null && child.equals(t)) {
                            itr.remove();
                            continue removeStillUsed;   // t is still used.
                        }
                    }
                }
            }
            for (ColumnDef revColumnDef : md.revListFields) {
                RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
                for (RealmModel parent : parents) {
                    if (!descendants.contains(parent)) {
                        RealmList<? extends RealmModel> children = getColumnObjects(parent, revColumnDef.columnDetails.columnIndex, md.clazz);
                        if (children != null && children.contains(t)) {
                            itr.remove();
                            continue removeStillUsed;   // t is still used.
                        }
                    }
                }
            }
        }
        for (RealmModel t : descendants) {
            RealmObject.deleteFromRealm(t);
        }
    }

    /**
     * NOTE: This can not handle circular reference correctly
     */
    private void findDescendantsOrdered(@NonNull List<RealmModel> dest, @NonNull Set<RealmModel> visiteds, @NonNull RealmModel object) {
        ModelDef modelDef = findModelDefByClass(object.getClass());
        if (modelDef != null) {
            if (!modelDef.objectSchema.hasPrimaryKey()) {
                dest.remove(object);
                dest.add(object);
            }
            if (visiteds.contains(object)) {
                return;
            }
            visiteds.add(object);
            for (ColumnDef cd : modelDef.objectFields.values()) {
                RealmModel child = getColumnObject(object, cd.columnDetails.columnIndex, cd.modelDef.clazz, false, Collections.<String>emptyList());
                if (child != null) {
                    findDescendantsOrdered(dest, visiteds, child);
                }
            }
            for (ColumnDef cd : modelDef.listFields.values()) {
                List<? extends RealmModel> children = getColumnObjects(object, cd.columnDetails.columnIndex, cd.modelDef.clazz);
                if (children != null) {
                    for (RealmModel child : children) {
                        findDescendantsOrdered(dest, visiteds, child);
                    }
                }
            }
        }
    }

    @Nullable
    private ModelDef findModelDefByClass(@NonNull Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null) {
            ModelDef md = mClass2ModelDefs.get(c);
            if (md != null) {
                return md;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    public Map<Class<? extends RealmModel>, Integer> cleanUp(@NonNull Realm realm, boolean dryRun) {
        Map<Class<? extends RealmModel>, Integer> deletedCounts = new HashMap<>();
        realm.beginTransaction();
        for (ModelDef md : mModelDefs) {
            if (md.objectSchema.hasPrimaryKey()) {
                continue;   // Only delete no primary key models
            }
            Collection<? extends RealmModel> unusedObjects = findUnusedObjects(realm, md);
            deletedCounts.put(md.clazz, unusedObjects.size());
            for (RealmModel unusedObject : unusedObjects) {
                RealmObject.deleteFromRealm(unusedObject);
            }
        }
        if (dryRun) {
            realm.cancelTransaction();
        } else {
            realm.commitTransaction();
        }
        return deletedCounts;
    }

    private static List<ModelDef> sortByDependencies(@NonNull Collection<ModelDef> modelDefs) {
        // NOTO: This do not ensure circular reference
        List<ModelDef> roots = new ArrayList<>();
        for (ModelDef md : modelDefs) {
            if (md.revObjectFields.isEmpty() && md.revListFields.isEmpty()) {
                roots.add(md);
            }
        }
        List<ModelDef> results = new ArrayList<>();
        for (ModelDef md : modelDefs) {
            dumpToList(results, null, md);
        }
        return results;
    }

    private static void dumpToList(@NonNull List<ModelDef> dest, @Nullable Set<ModelDef> visiteds, ModelDef md) {
        if (visiteds == null) {
            visiteds = new HashSet<>();
        }
        dest.remove(md);
        dest.add(md);
        if (!visiteds.contains(md)) {
            visiteds.add(md);
            for (ColumnDef child : md.objectFields.values()) {
                dumpToList(dest, visiteds, child.modelDef);
            }
            for (ColumnDef child : md.listFields.values()) {
                dumpToList(dest, visiteds, child.modelDef);
            }
        }
    }

    private static Collection<? extends RealmModel> findUnusedObjects(@NonNull Realm realm, @NonNull ModelDef md) {
        Set<? extends RealmModel> results = new HashSet<>(realm.where(md.clazz).findAll());
        for (ColumnDef revColumnDef : md.revObjectFields) {
            RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
            for (RealmModel parent : parents) {
                RealmModel child = getColumnObject(parent, revColumnDef.columnDetails.columnIndex, md.clazz, false, Collections.<String>emptyList());
                if (child != null) {
                    results.remove(child);
                }
            }
        }
        for (ColumnDef revColumnDef : md.revListFields) {
            RealmResults<? extends RealmModel> parents = realm.where(revColumnDef.modelDef.clazz).findAll();
            for (RealmModel parent : parents) {
                RealmList<? extends RealmModel> children = getColumnObjects(parent, revColumnDef.columnDetails.columnIndex, md.clazz);
                if (children != null) {
                    results.removeAll(children);
                }
            }
        }
        return results;
    }

    private static Collection<ModelDef> obtainModelDefs(@NonNull Realm realm) {
        DefaultRealmModuleMediator mediator = new DefaultRealmModuleMediator();
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

    private static <E extends RealmModel> E getFromBaseRealm(@NonNull ProxyState proxyState, Class<E> clazz, long rowIndex, boolean acceptDefaultValue, List<String> excludeFields) {
        return proxyState.getRealm$realm().get(clazz, rowIndex, acceptDefaultValue, excludeFields);
    }

    @Nullable
    private static <E extends RealmModel> E getColumnObject(@NonNull RealmModel parent, long columnIndex, Class<E> clazz, boolean acceptDefaultValue, List<String> excludeFields) {
        ProxyState proxyState = RealmInternalInspector.pullProxyState(parent);
        if (proxyState == null || proxyState.getRow$realm().isNullLink(columnIndex)) {
            return null;
        }
        return getFromBaseRealm(proxyState, clazz, proxyState.getRow$realm().getLink(columnIndex), false, Collections.<String>emptyList());
    }

    @Nullable
    private static <E extends RealmModel> RealmList<E> getColumnObjects(@NonNull RealmModel parent, long columnIndex, Class<E> clazz) {
        ProxyState proxyState = RealmInternalInspector.pullProxyState(parent);
        if (proxyState == null) {
            return null;
        }
        OsList osList = proxyState.getRow$realm().getModelList(columnIndex);
        return new RealmList<>(clazz, osList, proxyState.getRealm$realm());
    }

    static class ColumnDef {
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

    static class ModelDef {
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
