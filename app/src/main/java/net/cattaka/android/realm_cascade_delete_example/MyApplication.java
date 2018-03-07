package net.cattaka.android.realm_cascade_delete_example;

import android.app.Application;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

import java.util.regex.Pattern;

import io.realm.Realm;

/**
 * Created by cattaka on 18/02/16.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Realm.init(this);

        if (BuildConfig.DEBUG) {
            Stetho.initialize(
                    Stetho.newInitializerBuilder(this)
                            .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                            .enableWebKitInspector(RealmInspectorModulesProvider
                                    .builder(this)
                                    .databaseNamePattern(Pattern.compile(Constants.DB_NAME))
                                    .withLimit(100000)
                                    .build())
                            .build());

        }
    }
}
