package com.pollux.sourceapk;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base){
        super.attachBaseContext(base);
        Log.i("cs","MyApplication attachBaseContext ctx: "+base.toString());
        Log.i("cs","MyApplication attachBaseContext classloader: "+base.getClassLoader().toString());
    }


    @Override
    public void onCreate(){
        super.onCreate();
        Log.i("cs","MyApplication onCreate ctx: "+getApplicationContext());
        Log.i("cs","MyApplication onCreate classloader: "+this.getClassLoader().toString());
    }
}
