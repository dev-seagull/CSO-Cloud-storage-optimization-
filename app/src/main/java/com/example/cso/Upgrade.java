package com.example.cso;

import android.content.SharedPreferences;
import android.widget.Toast;

public class Upgrade {

    public static void versionHandler(SharedPreferences preferences){
        int savedVersionCode = preferences.getInt("currentVersionCode", -1); // Default to -1 if not found
        int currentVersionCode = BuildConfig.VERSION_CODE;
        if (savedVersionCode == -1){
            DBHelper.deleteTableContent("PROFILE");
        DBHelper.deleteTableContent("ACCOUNTS");
        }
        else if (savedVersionCode <= currentVersionCode) {
            switch (savedVersionCode){
                case 13:
                    upgrade_13_to_14();
                    break;
                case 14:
                    upgrade_14_to_15();
                    break;
                case 15:
                    upgrade_15_to_16();
                    break;
                case 16:
                    upgrade_16_to_17();
                    break;
                case 17:
                    upgrade_17_to_18();
                    break;
                case 18:
                    upgrade_18_to_19();
                    break;
                case 19 :
                    upgrade_19_to_20();
                    break;
                case 20 :
                    upgrade_20_to_21();
                    break;
                case 21:
                    upgrade_21_to_22();
                    break;
                case 22:
                    upgrade_22_to_23();
                    break;
                default:
                    lastVersion();
            }
        } else if (savedVersionCode > currentVersionCode) {
            Toast.makeText(MainActivity.activity, "Please install last version of App", Toast.LENGTH_SHORT).show();
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("currentVersionCode", currentVersionCode);
        editor.apply();
    }


    public static void upgrade_17_to_18() {
        MainActivity.activity.runOnUiThread(() -> {
            Toast.makeText(MainActivity.activity, "you are upgraded from version 17 to version 18 by Upgrader", Toast.LENGTH_SHORT).show();
        });
        DBHelper.deleteTableContent("DEVICE");
        DBHelper.deleteTableContent("ACCOUNTS");
        upgrade_18_to_19();
    }


    public static void lastVersion() {
        MainActivity.activity.runOnUiThread(() -> {
            Toast.makeText(MainActivity.activity, "You are upgraded to last version", Toast.LENGTH_SHORT).show();
        });
        DBHelper.deleteTableContent("ACCOUNTS");
    }


    public static void upgrade_16_to_17() {
        MainActivity.activity.runOnUiThread(() -> {
            Toast.makeText(MainActivity.activity, "you are upgraded from version 16 to version 17 by Upgrader", Toast.LENGTH_SHORT).show();
            DBHelper.deleteTableContent("DEVICE");
        });
        upgrade_17_to_18();
    }

    public static void upgrade_15_to_16() {
        MainActivity.activity.runOnUiThread(() -> {
            Toast.makeText(MainActivity.activity, "you are upgraded from version 15 to version 16 by Upgrader", Toast.LENGTH_SHORT).show();
        });
        upgrade_16_to_17();
    }

    public static void upgrade_14_to_15() {
//        dropProfileIdColumn();
        DBHelper.deleteTableContent("PROFILE");
        upgrade_15_to_16();
    }

    public static void upgrade_13_to_14(){
//        cutFromUserProfileToAccounts();
        upgrade_14_to_15();
    }

    public static void upgrade_18_to_19(){
        DBHelper.removeColumn("folderId","ACCOUNTS");
        upgrade_19_to_20();
    }

    public static void upgrade_19_to_20(){
        DBHelper.removeColumn("profileId","ACCOUNTS");
        DBHelper.dropTable("PROFILE");
        upgrade_20_to_21();
    }

    public static void upgrade_20_to_21(){
        DBHelper oldDBHelper = new DBHelper(MainActivity.activity.getApplicationContext(), "CSODatabase");
        oldDBHelper.copyDataFromOldToNew(MainActivity.dbHelper);
        upgrade_21_to_22();
    }

    public static void upgrade_21_to_22(){
        upgrade_22_to_23();
    }

    public static void upgrade_22_to_23(){
        DBHelper.dropTable("ERRORS");
    }

}
