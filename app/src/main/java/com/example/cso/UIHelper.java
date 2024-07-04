package com.example.cso;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class UIHelper {
    public TextView syncMessageTextView = MainActivity.activity.findViewById(R.id.syncMessageTextView);
    public static ColorStateList backupAccountButtonColor = ColorStateList.valueOf(Color.parseColor("#42A5F5"));
    public TextView deviceStorageTextView = MainActivity.activity.findViewById(R.id.deviceStorage);
    public SwitchMaterial syncSwitchMaterialButton = MainActivity.activity.findViewById(R.id.syncSwitchMaterial);
    public static ColorStateList addBackupAccountButtonColor = ColorStateList.valueOf(Color.parseColor("#FF5722"));
    public static ColorStateList primaryAccountButtonColor = ColorStateList.valueOf(Color.parseColor("#0D47A1"));
    public static ImageView waitingGif = MainActivity.activity.findViewById(R.id.waitingGif);
    public static ImageView waitingSyncGif = MainActivity.activity.findViewById(R.id.waitingSyncGif);
    public static ColorStateList offSwitchMaterialThumb  = ColorStateList.valueOf(Color.parseColor("#808080"));
    public static ColorStateList offSwitchMaterialTrack  = ColorStateList.valueOf(Color.parseColor("#808080"));
    public static ColorStateList onSwitchMaterialThumb  = ColorStateList.valueOf(Color.GREEN);
    public static ColorStateList onSwitchMaterialTrack  = ColorStateList.valueOf(Color.GREEN);
    public static int buttonTextColor = Color.WHITE;
    public static Drawable driveImage = MainActivity.activity.getApplicationContext().getResources()
            .getDrawable(R.drawable.googledriveimage);
    public TextView androidStatisticsTextView = MainActivity.activity.findViewById(R.id.androidStatistics);
    public TextView deviceStorage = MainActivity.activity.findViewById(R.id.deviceStorage);
    public ImageButton displayDirectoriesUsagesButton = MainActivity.activity.findViewById(R.id.directoriesButton);

}
