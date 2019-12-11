package com.heshicaihao.recorded.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class RecordedService extends Service  {
    private final IBinder mIBinder = new MyBinder();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mIBinder;
    }


    public class MyBinder extends Binder {
        public RecordedService getService() {
            return RecordedService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
