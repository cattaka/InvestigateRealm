package io.realm;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import io.realm.internal.OsList;
import io.realm.internal.RealmInternalInspector;

/**
 * Created by cattaka on 18/03/05.
 */

public class MyDefaultRealmModuleMediator extends DefaultRealmModuleMediator {
    public static <E extends RealmModel> E getFromBaseRealm(@NonNull ProxyState proxyState, Class<E> clazz, long rowIndex, boolean acceptDefaultValue, List<String> excludeFields) {
        return proxyState.getRealm$realm().get(clazz, rowIndex, acceptDefaultValue, excludeFields);
    }

    @Nullable
    public static <E extends RealmModel> E getColumnObject(@NonNull RealmModel parent, long columnIndex, Class<E> clazz, boolean acceptDefaultValue, List<String> excludeFields) {
        ProxyState proxyState = RealmInternalInspector.pullProxyState(parent);
        if (proxyState == null || proxyState.getRow$realm().isNullLink(columnIndex)) {
            return null;
        }
        return MyDefaultRealmModuleMediator.getFromBaseRealm(proxyState, clazz, proxyState.getRow$realm().getLink(columnIndex), false, Collections.<String>emptyList());
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
}
