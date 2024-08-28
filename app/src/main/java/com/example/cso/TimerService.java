package com.example.cso;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TimerService extends Service {
    private static int NOTIFICATION_ID = 12345;
    private static String CHANNEL_ID = "TimerServiceChannel";
    private static String CHANNEL_NAME = "Syncing Channel";

    private Timer timer;
    private Thread androidThreads;
    private Thread driveThreads;
    private Thread syncThreads;
    private boolean isTimerRunning = false;
    private TimerTask timerTask;
    private  Notification notification;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("service", "Service onCreate Started");
        requestNotificationPermission();
        createNotificationChannel();
        notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        startTimer();
        Log.d("service", "Service onCreate Finished");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("service", "Service onStartCommand Started");

        if (intent != null && intent.getAction() != null && intent.getAction().equals("STOP_SERVICE")) {
            Log.d("service", "Service stop request received");
            stopTimer();
//            stopService(MainActivity.serviceIntent);
            if(isAppInForeground()){
                UIHandler.handleSyncButtonClick(MainActivity.activity);
            }else{
                UIHandler.toggleSyncState();
            }

            stopForeground(true);
            stopSelf();
            Log.d("service","Service stopped");
        }
        Log.d("service", "Service onStartCommand Finished");
        return Service.START_STICKY;
    }

    private void startTimer() {
        Log.d("service","Service Timer Started");
        if(timer == null){
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
//                try{

                    if (isTimerRunning || MainActivity.isAnyProccessOn) {
                        return;
                    }
                    isTimerRunning = true;

                    if (Deactivation.isDeactivationFileExists()){
                        isTimerRunning = false;
                        stopForeground(true);
                        stopSelf();
                        UIHandler.handleDeactivatedUser();
                        return;
                    }

                    androidThreads = new Thread(new Runnable( ) {
                        @Override
                        public void run() {
                            try {
                                Android.startThreads(MainActivity.activity);
                            } catch (Exception e) {
                                LogHandler.saveLog("Error in Sync syncAndroidFiles : " + e.getLocalizedMessage());
                            }
                        }
                    });
                    androidThreads.start();

                    driveThreads = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GoogleDrive.startThreads();
                            } catch (Exception e) {
                                LogHandler.saveLog("Error in Sync syncAndroidFiles : " + e.getLocalizedMessage());
                            }
                        }
                    });
                    driveThreads.start();

                    syncThreads = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Sync.startSyncThread(getApplicationContext(), MainActivity.activity);
                            } catch (Exception e) {
                                LogHandler.saveLog("Error in Sync syncAndroidFiles : " + e.getLocalizedMessage());
                            } finally {
                                isTimerRunning = false;
                            }
                        }
                    });
                    syncThreads.start();

//                    storageUpdaterThreadTemp = new Thread(storageUpdaterThreadForService[0]);
//                    storageUpdaterThreadTemp.start();
//                    try{
//                        storageUpdaterThreadTemp.join();
//                    }catch (InterruptedException e) {
//                        LogHandler.saveLog("Failed to join storage update temp : " +
//                                e.getLocalizedMessage(), true);
//                    }
//                }catch (Exception e){
//                    LogHandler.saveLog("Failed to run timer in service" + e.getLocalizedMessage() , true);
//                }
                }
            };

            timer.schedule(timerTask, 5000 , 1000);
        }
        Log.d("service","Service Timer Finished");
    }

    private void requestNotificationPermission(){
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("service","Notification Permission required");
                int REQUEST_CODE = 1;
                ActivityCompat.requestPermissions(MainActivity.activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    public Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Syncing in progress...")
                .setContentText("Syncing process is running in the background...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSmallIcon(R.drawable.libzlogo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Syncing process is running in the background..."));

        Intent actionIntent = new Intent(getApplicationContext(), TimerService.class);
        actionIntent.setAction("STOP_SERVICE");

       PendingIntent actionPendingIntent;
       int request_code = 5;
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           actionPendingIntent = PendingIntent.getService(this, request_code, actionIntent, PendingIntent.FLAG_IMMUTABLE);
       } else {
           actionPendingIntent = PendingIntent.getService(this, request_code, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
       }

        builder.addAction(R.drawable.googledriveimage, "Stop Service", actionPendingIntent);

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }

        stopThreads();

        Log.d("TimerForegroundService", "Timer stopped");
    }

    private void stopThreads() {
        if (androidThreads != null) {
            androidThreads.interrupt();
            androidThreads = null;
        }

        if (driveThreads != null) {
            driveThreads.interrupt();
            driveThreads = null;
        }

        if (syncThreads != null) {
            syncThreads.interrupt();
            syncThreads = null;
        }
    }

    @Override
    public void onDestroy() {
        Log.d("service", "Service onDestroy Started");
        stopTimer();
        stopThreads();
        if (!isAppInForeground()) {
            if (MainActivity.dbHelper != null) {
                MainActivity.dbHelper.close();
                Log.d("service", "Database closed as app is not in foreground.");
                System.exit(0);
            }
        } else {
            Log.d("service", "App is in foreground, not closing the database.");
        }
        super.onDestroy();
        Log.d("service", "Service onDestroy Finished");
    }

    private boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }

        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }

        final String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }

        return false;
    }

    public static String isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                System.out.println("isMyServiceRunning : true");
                return "on";
            }
        }
        System.out.println("isMyServiceRunning : false");
        return "off";
    }
}
