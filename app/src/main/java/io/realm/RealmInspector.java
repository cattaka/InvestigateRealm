package io.realm;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

public class RealmInspector {
    public static Map<Class<? extends RealmModel>, Integer> cleanUp(@NonNull Realm realm) {
        Map<Class<? extends RealmModel>, Integer> deletedCounts = new HashMap<>();
        List<ModelDef> modelDefs = sortByDependencies(obtainModelDefs(realm));
        for (ModelDef md : modelDefs) {
            if (md.objectSchema.hasPrimaryKey()) {
                continue;   // Only delete no primary key models
            }
            List<? extends RealmModel> unusedObjects = findUnusedObjects(realm, md);
            deletedCounts.put(md.clazz, unusedObjects.size());
            realm.beginTransaction();
            for (RealmModel unusedObject : unusedObjects) {
                RealmObject.deleteFromRealm(unusedObject);
            }
            realm.commitTransaction();
        }
        return deletedCounts;
    }

    public static List<ModelDef> sortByDependencies(@NonNull Collection<ModelDef> modelDefs) {
        List<ModelDef> roots = new ArrayList<>();
        for (ModelDef md : modelDefs) {
            if (md.revObjectFields.isEmpty() && md.revListFields.isEmpty()) {
                roots.add(md);
            }
        }
        List<ModelDef> results = new ArrayList<>();
        for (ModelDef md : modelDefs) {
            dumpToList(results, md);
        }
        return results;
    }

    private static void dumpToList(@NonNull List<ModelDef> dest, ModelDef md) {
        dest.remove(md);
        dest.add(md);
        for (ColumnDef child : md.objectFields.values()) {
            dumpToList(dest, child.modelDef);
        }
        for (ColumnDef child : md.listFields.values()) {
            dumpToList(dest, child.modelDef);
        }
    }

    public static List<? extends RealmModel> findUnusedObjects(@NonNull Realm realm, @NonNull ModelDef md) {
        List<? extends RealmModel> results = new ArrayList<>(realm.where(md.clazz).findAll());
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
                RealmList<? extends RealmModel> children = getObjects(parent, revColumnDef.columnDetails.columnIndex, md.clazz);
                if (children != null) {
                    results.removeAll(children);
                }
            }
        }
        return results;
    }

    public static Collection<ModelDef> obtainModelDefs(@NonNull Realm realm) {
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

    public static <E extends RealmModel> E getFromBaseRealm(@NonNull ProxyState proxyState, Class<E> clazz, long rowIndex, boolean acceptDefaultValue, List<String> excludeFields) {
        return proxyState.getRealm$realm().get(clazz, rowIndex, acceptDefaultValue, excludeFields);
    }

    @Nullable
    public static <E extends RealmModel> E getColumnObject(@NonNull RealmModel parent, long columnIndex, Class<E> clazz, boolean acceptDefaultValue, List<String> excludeFields) {
        ProxyState proxyState = RealmInternalInspector.pullProxyState(parent);
        if (proxyState == null || proxyState.getRow$realm().isNullLink(columnIndex)) {
            return null;
        }
        return getFromBaseRealm(proxyState, clazz, proxyState.getRow$realm().getLink(columnIndex), false, Collections.<String>emptyList());
    }

    @Nullable
    public static <E extends RealmModel> RealmList<E> getObjects(@NonNull RealmModel parent, long columnIndex, Class<E> clazz) {
        ProxyState proxyState = RealmInternalInspector.pullProxyState(parent);
        if (proxyState == null) {
            return null;
        }
        OsList osList = proxyState.getRow$realm().getModelList(columnIndex);
        return new RealmList<>(clazz, osList, proxyState.getRealm$realm());
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
