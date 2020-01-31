package com.pollux.dexshells;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

public class ProxyApplication extends Application {
    static final String iTAG = "cs";
    private String odexPath;
    private String libPath;
    private String apkFileName;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.i(iTAG,"ProxyApplication attachBaseContext");
        File odex = this.getDir("apk_odex", MODE_PRIVATE);//创建新文件夹app_apk_odex
        File lib = this.getDir("apk_lib", MODE_PRIVATE);
        odexPath = odex.getAbsolutePath();
        libPath = lib.getAbsolutePath();
        apkFileName = odexPath + "/source.apk";
        File dexFile = new File(apkFileName);
        if (dexFile.exists()) {
            //如果文件存在
            Log.i(iTAG, dexFile.getName() + " already exist");
        }
        try {
            dexFile.createNewFile();
            byte[] dexData = this.readDexFromApk();
            this.extractSourceFromDex(dexData);//save to app_apk_odex/source.apk
            //---get LoadedApk Object
            Object currentActivityThread = RefInvoke.invokeStaticMethod("android.app.ActivityThread",
                    "currentActivityThread",
                    new Class[]{},new Object[]{});
            ArrayMap mPackages = (ArrayMap)RefInvoke.getFieldObject("android.app.ActivityThread",
                    currentActivityThread,
                    "mPackages");
            String packageName = this.getPackageName();
            WeakReference wr = (WeakReference) mPackages.get(packageName);//get LoadedApk

            //---replace the classloader in the LoadedApk
            ClassLoader mClassLoader = (ClassLoader)RefInvoke.getFieldObject("android.app.LoadedApk",
                    wr.get(),
                    "mClassLoader");
            Log.i(iTAG,"original classloader:"+mClassLoader);
            DexClassLoader dexClassLoader = new DexClassLoader(apkFileName, odexPath, libPath, mClassLoader);
            RefInvoke.setFieldObject("android.app.LoadedApk", "mClassLoader",wr.get(),
                    dexClassLoader);
            Log.i(iTAG,"after classloader:"+dexClassLoader);

            //---loadClass
            try{
                Object object = dexClassLoader.loadClass("com.pollux.sourceapk.MainActivity");
                Log.i(iTAG,"loadClass object: "+object);
            }catch (Exception e){
                Log.i(iTAG,"loadClass error: "+Log.getStackTraceString(e));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(iTAG,"ProxyApplication onCreate");

        String applicationClassName = null;
        try {
            ApplicationInfo ai = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if(bundle!=null && bundle.containsKey("APPLICATION_CLASS_NAME")){
                applicationClassName = bundle.getString("APPLICATION_CLASS_NAME");
            }else{
                Log.i(iTAG,"can not find APPLICATION_CLASS_NAME in meta label");
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        //---set LoadedApk#mApplication = null
        Object mcurrentActivityThread = RefInvoke.invokeStaticMethod("android.app.ActivityThread",
                "currentActivityThread",new Class[]{},new Object[]{});
        Object mBoundApplication = RefInvoke.getFieldObject("android.app.ActivityThread",
                mcurrentActivityThread,"mBoundApplication");
        Object mLoadedApk = RefInvoke.getFieldObject("android.app.ActivityThread$AppBindData",
                mBoundApplication,"info");
        RefInvoke.setFieldObject("android.app.LoadedApk","mApplication",mLoadedApk,null);

        //---remove the old application in the ActivityThread#mAllApplications
        Object oldApplication = RefInvoke.getFieldObject("android.app.ActivityThread",
                mcurrentActivityThread,
                "mInitialApplication");
        ArrayList<Application> mAllApplications = (ArrayList<Application>)RefInvoke.getFieldObject("android.app.ActivityThread",
                mcurrentActivityThread, "mAllApplications");
        mAllApplications.remove(oldApplication);

        //---set LoadedApk#mApplicationInfo   ActivityThread$AppBindData#appInfo
        ApplicationInfo appInfoLoadedApk = (ApplicationInfo) RefInvoke.getFieldObject("android.app.LoadedApk",
                mLoadedApk,"mApplicationInfo");
        appInfoLoadedApk.className = applicationClassName;
        ApplicationInfo appInfoAT$AppBindData = (ApplicationInfo) RefInvoke.getFieldObject("android.app.ActivityThread$AppBindData",
                mBoundApplication,"appInfo");
        appInfoAT$AppBindData.className = applicationClassName;

        Application app = (Application)RefInvoke.invokeMethod("android.app.LoadedApk","makeApplication",
                mLoadedApk,new Class[]{boolean.class, Instrumentation.class},new Object[]{false,null});
        RefInvoke.setFieldObject("android.app.ActivityThread","mInitialApplication",
                mcurrentActivityThread,app);

        //---set ActivityThread#mInitialApplication
        RefInvoke.setFieldObject("android.app.ActivityThread","mInitialApplication",
                mcurrentActivityThread,app);

        ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldObject("android.app.ActivityThread",
                mcurrentActivityThread,"mProviderMap");
        Iterator iterator = mProviderMap.values().iterator();
        while(iterator.hasNext()){
            Object providerClientRecord = iterator.next();
            Object mLocalProvider = RefInvoke.getFieldObject("android.app.ActivityThread$ProviderClientRecord",
                    providerClientRecord,"mLocalProvider");
            RefInvoke.setFieldObject("android.content.ContentProvider",
                    "mContext",mLocalProvider,app);
        }
        Log.i(iTAG,"app: "+app);
        app.onCreate();
    }

    private byte[] readDexFromApk() throws IOException {
        ByteArrayOutputStream dexByteArrayInputStream = new ByteArrayOutputStream();
        ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(
                                this.getApplicationInfo().sourceDir)));
        while (true) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            if (zipEntry == null) {
                zipInputStream.close();
                break;
            }
            if (zipEntry.getName().equals("classes.dex")) {
                byte b[] = new byte[1024];
                while (true) {
                    int len = zipInputStream.read(b);
                    if (len == -1)
                        break;
                    dexByteArrayInputStream.write(b, 0, len);
                }
            }
            zipInputStream.closeEntry();
        }
        zipInputStream.close();
        return dexByteArrayInputStream.toByteArray();
    }//end func readDexFromApk

    private void extractSourceFromDex(byte[] dexData) throws IOException{
        int dexLen = dexData.length;
        byte[] apkLenb = new byte[4];
        System.arraycopy(dexData,dexLen-4,apkLenb,0,4);
        ByteArrayInputStream bis = new ByteArrayInputStream(apkLenb);
        DataInputStream dis = new DataInputStream(bis);
        int apkLeni = dis.readInt();
        byte[] realApk = new byte[apkLeni];
        System.arraycopy(dexData,dexLen-4-apkLeni,realApk,0,apkLeni);
        realApk = decrypt(realApk);

        //写入源apk
        File file = new File(apkFileName);
        try{
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(realApk);
            fileOutputStream.close();
        }catch (IOException e){
            throw new RuntimeException(e);
        }

        ZipInputStream apkZipInputStream = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(file)
                ));
        ZipEntry apkEntry;
        String apkEntryName;
        while(true){
            apkEntry = apkZipInputStream.getNextEntry();
            if(apkEntry == null){
                apkZipInputStream.close();
                break;
            }
            //写lib文件到/data/user/0/com.pollux.dexshell/app_apk_lib
            apkEntryName = apkEntry.getName();
            if(apkEntryName.startsWith("lib/")
                    && apkEntryName.endsWith(".so")){
                File soFile = new File(libPath+apkEntryName.substring(apkEntryName.lastIndexOf("/")));
                soFile.createNewFile();
                FileOutputStream soFileOutputStream = new FileOutputStream(soFile);
                byte[] soByte = new byte[1024];
                while(true){
                    int len = apkZipInputStream.read(soByte);
                    if(len == -1)
                        break;
                    soFileOutputStream.write(soByte,0,len);
                }
                soFileOutputStream.flush();
                soFileOutputStream.close();
            }
            //写lib文件结束
            apkZipInputStream.closeEntry();
        }
        apkZipInputStream.close();
    }//end func extractSourceFromDex

    private byte[] decrypt(byte[] data){
        for(int i =0;i<data.length;i++){
            data[i] = (byte) (data[i]^0xff);
        }
        return data;
    }
}

