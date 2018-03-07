package io.realm.internal;

import android.support.annotation.Nullable;

import io.realm.ProxyState;
import io.realm.RealmModel;

/**
 * Created by cattaka on 18/03/07.
 */

public class RealmInternalInspector {
    @Nullable
    public static ProxyState pullProxyState(@Nullable RealmModel model) {
        if (model instanceof RealmObjectProxy) {
            return ((RealmObjectProxy) model).realmGet$proxyState();
        } else {
            return null;
        }
    }
}
