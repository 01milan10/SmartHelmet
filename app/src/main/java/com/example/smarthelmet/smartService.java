package com.example.smarthelmet;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class smartService extends Service {

    Landing landing = new Landing();

    public smartService(){

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent) {
        return START_STICKY;
    }

//    @Override
//    public void onTaskRemoved(Intent rootIntent) {
//        Intent restartServiceIntent =new Intent(getApplicationContext(),this.getClass());
//        restartServiceIntent.setPackage(getPackageName());
//        startService(restartServiceIntent);
//        super.onTaskRemoved(rootIntent);
//    }
}