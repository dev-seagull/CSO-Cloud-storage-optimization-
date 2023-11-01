    package com.example.cso;

    import android.content.Intent;
    import android.os.Bundle;
    import android.text.Layout;
    import android.text.Spannable;
    import android.text.SpannableString;
    import android.text.style.AlignmentSpan;
    import android.view.MenuItem;
    import android.view.View;
    import android.view.ViewGroup;
    import android.view.ViewParent;
    import android.widget.Button;
    import android.widget.LinearLayout;
    import android.widget.PopupMenu;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.activity.result.ActivityResultLauncher;
    import androidx.activity.result.contract.ActivityResultContracts;
    import androidx.appcompat.app.ActionBarDrawerToggle;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.appcompat.widget.AppCompatButton;
    import androidx.core.view.GravityCompat;
    import androidx.drawerlayout.widget.DrawerLayout;

    import com.google.android.material.navigation.NavigationView;
    import com.jaredrummler.android.device.DeviceName;

    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.concurrent.Callable;
    import java.util.concurrent.ExecutionException;
    import java.util.concurrent.Executor;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.Future;


    public class MainActivity extends AppCompatActivity {

        private DrawerLayout drawerLayout;
        private NavigationView navigationView;
        Button syncToBackUpAccountButton;
        GoogleCloud googleCloud;
        ActivityResultLauncher<Intent> signInToPrimaryLauncher;
        ActivityResultLauncher<Intent> signInToBackUpLauncher;
        GooglePhotos googlePhotos;
        HashMap<String, PrimaryAccountInfo> primaryAccountHashMap = new HashMap<>();
        HashMap<String, BackUpAccountInfo> backUpAccountHashMap = new HashMap<String, BackUpAccountInfo>();
        ArrayList<Android.MediaItem> androidMediaItems = new ArrayList<>();
        Android android;
        String androidDeviceName;


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            drawerLayout = findViewById(R.id.drawer_layout);
            navigationView = findViewById(R.id.navigationView);
            ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(
                    this, drawerLayout, R.string.navigation_drawer_open,
                    R.string.navigation_drawer_close);
            drawerLayout.addDrawerListener(actionBarDrawerToggle);
            actionBarDrawerToggle.syncState();
            MenuItem menuItem1 = navigationView.getMenu().findItem(R.id.navMenuItem1);
            String appVersion = BuildConfig.VERSION_NAME;
            SpannableString centeredText = new SpannableString("Version: " + appVersion);
            centeredText.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, appVersion.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            menuItem1.setTitle(centeredText);
            AppCompatButton infoButton = findViewById(R.id.infoButton);
            infoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    drawerLayout.openDrawer(GravityCompat.END);
                }
            });

            syncToBackUpAccountButton = findViewById(R.id.syncToBackUpAccountButton);
            TextView androidDeviceNameTextView = findViewById(R.id.androidDeviceTextView);
            androidDeviceName = DeviceName.getDeviceName();
            androidDeviceNameTextView.setText(androidDeviceName);

            LinearLayout primaryAccountsButtonsLayout= findViewById(R.id.primaryAccountsButtons);
            LinearLayout backupAccountsButtonsLayout= findViewById(R.id.backUpAccountsButtons);
            googleCloud = new GoogleCloud(this);
            googleCloud.createPrimaryLoginButton(primaryAccountsButtonsLayout);
            googleCloud.createBackUpLoginButton(backupAccountsButtonsLayout);
        }


        @Override
        protected void onStart(){
            super.onStart();

            runOnUiThread(new Runnable() {@Override public void run() {updateButtonsListeners();}});

            try{
                googleCloud = new GoogleCloud(this);
                googlePhotos = new GooglePhotos();
                android = new Android(androidMediaItems);
            }catch (Exception e){
                runOnUiThread(new Runnable() {@Override public void run() {
                    Toast.makeText(getApplicationContext(),e.getLocalizedMessage(),Toast.LENGTH_LONG).show();
                }});
            }
            LogHandler.CreateLogFile();
            System.out.println("Starting android executor");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Callable<ArrayList<Android.MediaItem>> androidBackgroundTask = () -> {
                androidMediaItems = android.getGalleryMediaItems(MainActivity.this);
                return androidMediaItems;
            };
            Future<ArrayList<Android.MediaItem>> future = executor.submit(androidBackgroundTask);
            try {
                androidMediaItems = future.get();
            } catch (ExecutionException e) {throw new RuntimeException(e);
            } catch (InterruptedException e) {throw new RuntimeException(e);}
            executor.shutdown();
            System.out.println("shut down android executor");
            android = new Android(androidMediaItems);

            signInToPrimaryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK){
                        Executor signInExecutor = Executors.newSingleThreadExecutor();
                        try {
                            Runnable backgroundTask = () -> {
                                PrimaryAccountInfo primaryAccountInfo = googleCloud.handleSignInToPrimaryResult(result.getData());
                                String userEmail = primaryAccountInfo.getUserEmail();
                                primaryAccountHashMap.put(primaryAccountInfo.getUserEmail(), primaryAccountInfo);

                                runOnUiThread(new Runnable(){
                                    @Override
                                    public void run(){
                                        System.out.println("here running");
                                        System.out.println(userEmail);
                                        LinearLayout primaryAccountsButtonsLinearLayout = findViewById(R.id.primaryAccountsButtons);
                                        if(primaryAccountsButtonsLinearLayout.getChildCount() == 1){
//                                            Button bt = findViewById(R.id.loginButton);
//                                            bt.setText(userEmail);
                                        }else{
                                            View childview = primaryAccountsButtonsLinearLayout.getChildAt(
                                                    primaryAccountsButtonsLinearLayout.getChildCount() - 2);
                                            if(childview instanceof Button){
                                                Button bt = (Button) childview;
                                                bt.setText(userEmail);
                                            }
                                        }

                                        updateButtonsListeners();
                                    }
                                });
                            };
                            signInExecutor.execute(backgroundTask);
                        }catch (Exception e){
                            Toast.makeText(this,"Failed to sign in", Toast.LENGTH_LONG);
                        }
                   }
                }
            );
            System.out.println("is it true :"+androidMediaItems.size());

            signInToBackUpLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK){
                        Executor signInToBackupExecutor = Executors.newSingleThreadExecutor();
                        try{
                            Runnable backgroundTask = () -> {
                                BackUpAccountInfo backUpAccountInfo = googleCloud.handleSignInToBackupResult(result.getData());
                                String userEmail = backUpAccountInfo.getUserEmail();
                                backUpAccountHashMap.put(backUpAccountInfo.getUserEmail(), backUpAccountInfo);

                                runOnUiThread(new Runnable(){
                                    @Override
                                    public void run(){
                                        System.out.println(userEmail);
                                        LinearLayout backupAccountsButtonsLinearLayout = findViewById(R.id.backUpAccountsButtons);
                                        if(backupAccountsButtonsLinearLayout.getChildCount() == 1){
//                                            Button bt = findViewById(R.id.backUpLoginButton);
//                                            bt.setText(userEmail);
                                        }else{
                                            View childview = backupAccountsButtonsLinearLayout.getChildAt(
                                                    backupAccountsButtonsLinearLayout.getChildCount() - 2);
                                            if(childview instanceof Button){
                                                Button bt = (Button) childview;
                                                bt.setText(userEmail);
                                            }
                                        }

                                        updateButtonsListeners();
                                    }
                                });
                            };
                            signInToBackupExecutor.execute(backgroundTask);
                        }catch (Exception e){
                            Toast.makeText(this,"Failed to sign in", Toast.LENGTH_LONG);
                        }
                    }
                });

            syncToBackUpAccountButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
    //                Double totalVolume = 0.0 ;
    //                System.out.println("total usage volume 1:" + totalVolume);
    //                for (PrimaryAccountInfo primaryAccountInfo : primaryAccountHashMap.values()) {
    //                    totalVolume += primaryAccountInfo.getStorage().getUsedInGmailAndPhotosStorage();
    //                }
    //                for (Android.MediaItem mediaItem : androidMediaItems){
    //                    totalVolume += mediaItem.getFileSize();
    //
    //                    //System.out.println(" android file size : " + mediaItem.getFileSize());
    //                }
    //                System.out.println("total usage volume :" + totalVolume);
                    runOnUiThread(() ->{
                        TextView syncToBackUpAccountTextView = findViewById(R.id.syncToBackUpAccountTextView);
                        syncToBackUpAccountTextView.setText("Wait until the uploading process is finished");
                    });

                    BackUpAccountInfo firstBackUpAccountInfo = backUpAccountHashMap.values().iterator().next();
                    PrimaryAccountInfo.Tokens backupTokens = firstBackUpAccountInfo.getTokens();
                    String backUpAccessToken = firstBackUpAccountInfo.getTokens().getAccessToken();
                    ArrayList<BackUpAccountInfo.MediaItem> backupMediaItems = firstBackUpAccountInfo.getMediaItems();

                    //LogHandler.SaveLog("Duplicate Media Deleted in Backup Google Drive");

                    Thread driveBackUpThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            GoogleDrive.deleteDuplicatedMediaItems(backupMediaItems,backupTokens);
                            synchronized (this){
                                notify();
                            }
                        }
                    });

                    Thread photosUploadThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (driveBackUpThread){
                                try{
                                    driveBackUpThread.join();
                                }catch (Exception e){

                                }
                            }

                            for (PrimaryAccountInfo primaryAccountInfo : primaryAccountHashMap.values()) {
                                ArrayList<BackUpAccountInfo.MediaItem> backUpMediaItems = firstBackUpAccountInfo.getMediaItems();
                                ArrayList<GooglePhotos.MediaItem> mediaItems = primaryAccountInfo.getMediaItems();
                                googlePhotos.uploadPhotosToGoogleDrive(mediaItems, backUpAccessToken
                                        ,backUpMediaItems);
                            }
                        }
                    });

                    Thread androidUploadThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (photosUploadThread){
                                try{
                                    photosUploadThread.join();
                                }catch (Exception e){

                                }
                            }
                            uploadAndroidToDriveAccounts(backUpAccessToken);
                        }
                    });

                    Thread updateUIThread =  new Thread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (androidUploadThread){
                                try{
                                    androidUploadThread.join();
                                }catch (Exception e){

                                }
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationHandler.sendNotification("1","syncingAlert", MainActivity.this,
                                            "Syncing is finished","You're files are backed-up!");
                                    TextView syncToBackUpAccountTextView = findViewById(R.id.syncToBackUpAccountTextView);
                                    syncToBackUpAccountTextView.setText("Uploading process is finished");
                                }
                            });
                        }
                    });

                    driveBackUpThread.start();
                    photosUploadThread.start();
                    androidUploadThread.start();
                    updateUIThread.start();

                    //LogHandler.SaveLog("Photos Media Uploaded into Backup Google Drive");

                    //LogHandler.SaveLog("Android Media Uploaded into Backup Google Drive");
                    //LogHandler.BackupLogFile(firstBackUpAccountInfo.getTokens());

                }
            });
        }


        void uploadAndroidToDriveAccounts(String backUpAccessToken){
            try{
                Executor uploadExecutor = Executors.newSingleThreadExecutor();
                Runnable backgroundTask = () -> {
                    BackUpAccountInfo firstBackUpAccountInfo = backUpAccountHashMap.values().iterator().next();
                    ArrayList<BackUpAccountInfo.MediaItem> backUpMediaItems = firstBackUpAccountInfo.getMediaItems();
                    for(PrimaryAccountInfo primaryAccountInfo: primaryAccountHashMap.values()){
                        ArrayList<GooglePhotos.MediaItem> primaryMediaItems = primaryAccountInfo.getMediaItems();
                        System.out.println("here to sync android!");
                        googlePhotos.uploadAndroidToGoogleDrive(androidMediaItems, primaryMediaItems ,backUpAccessToken,
                                backUpMediaItems, this);
                    }
                };
                uploadExecutor.execute(backgroundTask);
            }catch (Exception e){
            }
        }

        private void updateButtonsListeners() {
            LinearLayout primaryAccountsButtonsLinearLayout = findViewById(R.id.primaryAccountsButtons);
            for (int i = 0; i < primaryAccountsButtonsLinearLayout.getChildCount(); i++) {
                View childView = primaryAccountsButtonsLinearLayout.getChildAt(i);
                if (childView instanceof Button) {
                    Button button = (Button) childView;
                    button.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    String buttonText = button.getText().toString().toLowerCase();
                                    if (buttonText.equals("add a primary account")){
                                        button.setText("Wait");
                                        googleCloud.signInToGoogleCloud(signInToPrimaryLauncher);
                                    }else {
                                        PopupMenu popupMenu = new PopupMenu(MainActivity.this,button);
                                        popupMenu.getMenuInflater().inflate(R.menu.account_button_menu,popupMenu.getMenu());
                                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                            @Override
                                            public boolean onMenuItemClick(MenuItem item) {
                                                switch (item.getItemId()) {
                                                    case R.id.sign_out:
                                                        googleCloud.signOut();
                                                        ViewGroup parentView = (ViewGroup) button.getParent();
                                                        parentView.removeView(button);
                                                }
                                                return true;
                                            }
                                        });
                                        popupMenu.show();
                                    }
                                }
                            }
                    );
                }
            }

            LinearLayout backUpAccountsButtonsLinearLayout = findViewById(R.id.backUpAccountsButtons);
            for (int i = 0; i < backUpAccountsButtonsLinearLayout.getChildCount(); i++) {
                View childView = backUpAccountsButtonsLinearLayout.getChildAt(i);
                if (childView instanceof Button) {
                    Button button = (Button) childView;
                    button.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    String buttonText = button.getText().toString().toLowerCase();
                                    if (buttonText.equals("add a back up account")) {
                                        button.setText("Wait");
                                        googleCloud.signInToGoogleCloud(signInToBackUpLauncher);
                                    } else {
                                        PopupMenu popupMenu = new PopupMenu(MainActivity.this, button);
                                        popupMenu.getMenuInflater().inflate(R.menu.account_button_menu, popupMenu.getMenu());
                                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                            @Override
                                            public boolean onMenuItemClick(MenuItem item) {
                                                switch (item.getItemId()) {
                                                    case R.id.sign_out:
                                                        googleCloud.signOut();
                                                        ViewGroup parentView = (ViewGroup) button.getParent();
                                                        parentView.removeView(button);
                                                }
                                                return true;
                                            }
                                        });
                                        popupMenu.show();
                                    }
                                }
                            });
                        }
                    }
                }





        private void updatePrimaryAccountsStorageChart() {
            /*
            try {
                HorizontalBarChart horizontalBarChart = findViewById(R.id.StorageHorizontalBarChart);
                horizontalBarChart.getDescription().setEnabled(false);
                //horizontalBarChart.setDrawBarShadow(false);
                horizontalBarChart.setDrawValueAboveBar(true);
                horizontalBarChart.setDrawGridBackground(false);
                horizontalBarChart.getLegend().setEnabled(false); // Hide legends

                XAxis xAxis = horizontalBarChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setDrawGridLines(false);
                xAxis.setDrawAxisLine(false);
                xAxis.setDrawLabels(false);

                YAxis yAxis = horizontalBarChart.getAxisLeft();
                yAxis.setDrawGridLines(false);
                yAxis.setDrawAxisLine(false);
                yAxis.setDrawLabels(false);

                horizontalBarChart.getAxisRight().setEnabled(false);

                // Use a list of Float to store usedStorage values
               // List<Float> chartfloatvalues = new ArrayList<>();

                //List<BarEntry> entries = new ArrayList<>();
                //BarData barData = new BarData();

                Double freeStorage = 0.0;
                int m = 1;

                int[] barColors = {Color.RED, Color.GRAY, Color.GREEN, Color.BLACK, Color.BLUE};

                List<Float> chartfloatvalues = new ArrayList<>();


                ArrayList<BarEntry> barEntries = new ArrayList<>();
                ArrayList<String> barLabels = new ArrayList<>();
                //barEntries.add(new BarEntry(0,new float[] {availableSpace,usedSpace} ));
               // barLabels.add("Free Space: " + availableSpace + " GB");
               // barLabels.add("Used space: " + usedSpace + " GB");

                for (String userEmail : primaryAccountHashMap.keySet()) {
                    PrimaryAccountInfo primaryAccountInfo = primaryAccountHashMap.get(userEmail);
                    Double totalStorage = primaryAccountInfo.getStorage().getTotalStorage();
                    Double usedStorage = primaryAccountInfo.getStorage().getUsedStorage();
                    freeStorage += totalStorage - usedStorage;

                    // Store usedStorage values in the list
                    chartfloatvalues.add(usedStorage.floatValue());
                    m++;
                }

                float[] usedStorageArray = new float[chartfloatvalues.size()];
                for (int i = 0; i < chartfloatvalues.size(); i++) {
                    usedStorageArray[i] = chartfloatvalues.get(i);
                }

                BarEntry barEntry = new BarEntry(0, usedStorageArray);
                barEntries.add(barEntry);
                //androidStorageBarChartDataSet.(PieDataSet.ValuePosition.OUTSIDE_SLICE);

                // Convert the list of usedStorage values to an array
                barEntries.add(new BarEntry(0,new float[] {freeStorage.floatValue()}));

                for(BarEntry be: barEntries){
                    //
                }

                BarDataSet barDataSet = new BarDataSet(barEntries, "");
                barDataSet.setDrawValues(true);// Adjust the width as needed
                barDataSet.setColors(barColors);
                barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);

                BarData barData = new BarData(barDataSet);
                androidStorageBarChart.setData(barData);
                androidStorageBarChart.invalidate();

                //BarEntry barEntry = new BarEntry(m, usedStorageArray);
                //String freeSpaceLabel = "used Space: " + freeStorage;
                //freeSpaceLabel.setD
                //entries.add(barEntry);

               // BarEntry freeSpaceEntry = new BarEntry(m , new float[]{freeStorage.floatValue()});
               // String freeSpaceLabel = "Free Space: " + freeStorage;
               // freeSpaceEntry.setData(freeSpaceLabel);
               // entries.add(freeSpaceEntry);

                // Set the bar data to the horizontalBarChart
                horizontalBarChart.setData(barData);
                horizontalBarChart.invalidate();

                // Configure other properties as needed

                System.out.println("successful2");
            } catch (Exception e) {
                System.out.println("error " + e.getLocalizedMessage());
            }
             */
        }
    }



