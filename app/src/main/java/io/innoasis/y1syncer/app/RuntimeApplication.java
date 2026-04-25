package io.innoasis.y1syncer.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import io.innoasis.y1syncer.runtime.CoreRuntimeController;

public class RuntimeApplication extends Application {
    private static final String TAG = "RuntimeApplication";
    private CoreRuntimeController runtimeController;

    @Override
    public void onCreate() {
        super.onCreate();
        installSecurityProviderIfAvailable();
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

    private void installSecurityProviderIfAvailable() {
        try {
            // Use reflection so app still builds/runs on minSdk 17 without a hard GMS dependency.
            Class<?> providerInstaller = Class.forName("com.google.android.gms.security.ProviderInstaller");
            providerInstaller.getMethod("installIfNeeded", Context.class).invoke(null, this);
            Log.i(TAG, "Security provider installed/verified via GMS");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "ProviderInstaller unavailable (no GMS on device)");
        } catch (Exception e) {
            Log.w(TAG, "Security provider setup failed: " + e.getMessage());
        }
    }
}
