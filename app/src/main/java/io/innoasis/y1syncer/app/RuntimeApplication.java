package io.innoasis.y1syncer.app;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;

import io.innoasis.y1syncer.runtime.CoreRuntimeController;

public class RuntimeApplication extends Application {
    private CoreRuntimeController runtimeController;

    @Override
    public void onCreate() {
        super.onCreate();
        runtimeController = new CoreRuntimeController(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public CoreRuntimeController getRuntimeController() {
        return runtimeController;
    }
}
