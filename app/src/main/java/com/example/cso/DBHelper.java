package com.example.cso;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DBHelper extends SQLiteOpenHelper {
    private static final String OLD_DATABASE_NAME = "CSODatabase";
    private static final String NEW_DATABASE_NAME =  "StashDatabase";
    public static final int DATABASE_VERSION = 12;
    public static SQLiteDatabase dbReadable;
    public static SQLiteDatabase dbWritable;

    public DBHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
        dbReadable = getReadableDatabase();
        dbWritable = getWritableDatabase();
        onCreate(getWritableDatabase());
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String ACCOUNTS = "CREATE TABLE IF NOT EXISTS ACCOUNTS("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                +"userEmail TEXT ," +
                "type TEXT CHECK (type IN ('primary','backup')), " +
                "refreshToken TEXT, " +
                "accessToken TEXT, " +
                "totalStorage REAL," +
                "usedStorage REAL," +
                "usedInDriveStorage REAL,"+
                "UsedInGmailAndPhotosStorage REAL);";
        sqLiteDatabase.execSQL(ACCOUNTS);

        String DEVICE = "CREATE TABLE IF NOT EXISTS DEVICE("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "deviceName TEXT," +
                "totalStorage TEXT," +
                "freeStorage TEXT)";
        sqLiteDatabase.execSQL(DEVICE);

        String ASSET = "CREATE TABLE IF NOT EXISTS ASSET("
                +"id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "fileHash TEXT);";
        sqLiteDatabase.execSQL(ASSET);

        String BACKUPDB = "CREATE TABLE IF NOT EXISTS BACKUPDB("
                +"userEmail TEXT REFERENCES ACCOUNTS(userEmail), "+
                "fileId TEXT);";

        sqLiteDatabase.execSQL(BACKUPDB);


        String DRIVE = "CREATE TABLE IF NOT EXISTS DRIVE("
                +"id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "assetId INTEGER REFERENCES ASSET(id),"+
                "fileId TEXT," +
                "fileName TEXT," +
                "userEmail TEXT REFERENCES ACCOUNTS(userEmail) ON UPDATE CASCADE ON DELETE CASCADE, " +
                "fileHash TEXT)";
        sqLiteDatabase.execSQL(DRIVE);

        String ANDROID = "CREATE TABLE IF NOT EXISTS ANDROID("
                +"id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "assetId INTEGER,"+
                "fileName TEXT," +
                "filePath TEXT," +
                "device TEXT," +
                "fileSize REAL," +
                "fileHash TEXT," +
                "dateModified TEXT,"+
                "memeType TEXT,"+
                "CONSTRAINT fk_assetId FOREIGN KEY (assetId) REFERENCES ASSET(id));";
        sqLiteDatabase.execSQL(ANDROID);

        String PHOTOS = "CREATE TABLE IF NOT EXISTS PHOTOS("
                +"id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "assetId INTEGER REFERENCES ASSET(id) ON UPDATE CASCADE ON DELETE CASCADE,"+
                "fileId TEXT," +
                "fileName TEXT," +
                "userEmail TEXT REFERENCES ACCOUNTS(userEmail) ON UPDATE CASCADE ON DELETE CASCADE,"+
                "creationTime TEXT," +
                "fileHash TEXT," +
                "baseUrl TEXT)";
        sqLiteDatabase.execSQL(PHOTOS);

        String ERRORS = "CREATE TABLE IF NOT EXISTS ERRORS(" +
                "descriptionError TEXT," +
                "error TEXT," +
                "date TEXT)";
        sqLiteDatabase.execSQL(ERRORS);

        String TRANSACTIONS = "CREATE TABLE IF NOT EXISTS TRANSACTIONS(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "source TEXT,"+
                "fileName TEXT," +
                "destination TEXT,"+
                "assetId TEXT,"+
                "operation TEXT CHECK (operation IN ('duplicated','sync','syncPhotos','download','deletedInDevice')),"+
                "hash TEXT,"+
                "date TEXT)";
        sqLiteDatabase.execSQL(TRANSACTIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
    }

    public void copyDataFromOldToNew(DBHelper newDBHelper){
        String[] tableNames = {"ACCOUNTS", "DEVICE", "ASSET", "BACKUPDB", "DRIVE", "ANDROID", "PHOTOS", "ERRORS", "TRANSACTIONS"};
        SQLiteDatabase oldDatabase = getReadableDatabase();
        for (String tableName : tableNames) {
            String selectQuery = "SELECT * FROM " + tableName;
            Cursor cursor = oldDatabase.rawQuery(selectQuery, null);

            newDBHelper.getWritableDatabase().beginTransaction();
            try {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    // Map column names to values
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        String columnName = cursor.getColumnName(i);
                        switch (cursor.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                values.put(columnName, cursor.getInt(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                values.put(columnName, cursor.getFloat(i));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                values.put(columnName, cursor.getString(i));
                                break;
                            // Handle other data types if necessary
                        }
                    }
                    // Insert data into the corresponding table in the new database
                    newDBHelper.getWritableDatabase().insert(tableName, null, values);
                }
                newDBHelper.getWritableDatabase().setTransactionSuccessful();
            } catch (Exception e) {
                LogHandler.saveLog( "Error copying data from " + tableName + ": " + e.getMessage(), true);
            } finally {
                newDBHelper.getWritableDatabase().endTransaction();
                cursor.close();
            }
        }
    }

    public static void removeColumn(String column, String table) {
        try{
            List<String> existingColumns = getTableColumns(table);
            existingColumns.remove(column);
            String columnList = TextUtils.join(",", existingColumns);

            String createNewTableQuery = "CREATE TABLE temp_table AS SELECT " +
                    columnList +
                    " FROM " + table + ";";
            dbWritable.execSQL(createNewTableQuery);

//            String copyDataQuery = "INSERT INTO temp_table (" + columnList + ") " +
//                    "SELECT " + columnList + " FROM " + table + ";";
//            dbWritable.execSQL(copyDataQuery);

            dropTable(table);

            String renameTableQuery = "ALTER TABLE temp_table RENAME TO " + table + ";";
            dbWritable.execSQL(renameTableQuery);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static List<String> getTableColumns(String tableName) {
        List<String> columns = new ArrayList<>();
        try{
            Cursor cursor = dbWritable.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int columnIndex = cursor.getColumnIndex("name");
                    if(columnIndex >=0){
                        String columnName = cursor.getString(columnIndex);
                        columns.add(columnName);
                    }
                }
                cursor.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return columns;
    }

    public long insertAssetData(String fileHash) {
        long lastInsertedId = -1;
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ASSET WHERE fileHash = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,new String[]{fileHash});
        boolean existsInAsset = false;
        try{
           if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    existsInAsset = true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to select from ASSET in insertAssetData method: " + e.getLocalizedMessage());
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }

        if(!existsInAsset){
            try{
                dbWritable.beginTransaction();
                sqlQuery = "INSERT INTO ASSET(fileHash) VALUES (?);";
                dbWritable.execSQL(sqlQuery, new Object[]{fileHash});
                dbWritable.setTransactionSuccessful();
            }catch (Exception e){
                LogHandler.saveLog("Failed to insert data into ASSET : "  + e.getLocalizedMessage());
            }finally {
                dbWritable.endTransaction();
            }
        }
        try{
            sqlQuery = "SELECT id FROM ASSET WHERE fileHash = ?;";
            Cursor cursor2 = dbReadable.rawQuery(sqlQuery, new String[]{fileHash});
            if(cursor2 != null && cursor2.moveToFirst()){
                lastInsertedId = cursor2.getInt(0);
            }else{
                LogHandler.saveLog("Failed to find the existing file id in Asset database.", true);
            }
            cursor2.close();
        }catch (Exception e){
            LogHandler.saveLog("Failed select asset id from asset table: " + e.getLocalizedMessage());
        }
        return lastInsertedId;
    }

    public void deleteRedundantPhotos(ArrayList<String> fileIds, String userEmail){
        String sqlQuery = "SELECT * FROM PHOTOS where userEmail = ?";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{userEmail});
        if(cursor.moveToFirst()){
            do{
                int fileIdColumnIndex = cursor.getColumnIndex("fileId");
                if(fileIdColumnIndex >= 0) {
                    String fileId = cursor.getString(fileIdColumnIndex);
                    if (!fileIds.contains(fileId)) {
                        dbWritable.beginTransaction();
                        try {
                            sqlQuery = "DELETE FROM PHOTOS WHERE fileId = ?";
                            dbWritable.execSQL(sqlQuery, new Object[]{fileId});
                            dbWritable.setTransactionSuccessful();
                        } catch (Exception e) {
                            LogHandler
                                    .saveLog("Failed to delete the database in" +
                                            " deleteRedundantPhotos method. " + e.getLocalizedMessage());
                        } finally {
                            dbWritable.endTransaction();
                        }

                        int assetIdColumnIndex = cursor.getColumnIndex("assetId");
                        boolean existsInDatabase = false;
                        String assetId = "";
                        if (assetIdColumnIndex >= 0) {
                            dbReadable = getReadableDatabase();
                            assetId = cursor.getString(assetIdColumnIndex);
                            existsInDatabase = assetExistsInDatabase(assetId);
                        }

                        if (!existsInDatabase) {
                            dbWritable.beginTransaction();
                            try {
                                sqlQuery = "DELETE FROM ASSET WHERE id = ? ";
                                dbWritable.execSQL(sqlQuery, new Object[]{assetId});
                                dbWritable.setTransactionSuccessful();
                            } catch (Exception e) {
                                LogHandler.saveLog("Failed to delete the database in" +
                                        " deleteRedundantPhotos method. " + e.getLocalizedMessage());
                            } finally {
                                dbWritable.endTransaction();
                            }
                        }
                    }
                }
            }while (cursor.moveToNext());
        }
        cursor.close();
    }

    private boolean assetExistsInDatabase(String assetId){
        boolean existsInDatabase = false;
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ANDROID WHERE assetId = ?) " +
                "OR EXISTS(SELECT 1 FROM PHOTOS WHERE assetId = ?) " +
                "OR EXISTS(SELECT 1 FROM DRIVE WHERE assetId = ?)";
        try (Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{assetId, assetId, assetId})) {
            if (cursor != null && cursor.moveToFirst()) {
                int result = cursor.getInt(0);
                if (result == 1) {
                    existsInDatabase = true;
                }
            }
        } catch (Exception e) {
            LogHandler.saveLog("Failed to check if the data exists in Database : " + e.getLocalizedMessage());
        }
        return existsInDatabase;
    }

    public void insertIntoPhotosTable(Long assetId, String fileId,String fileName, String fileHash,
                                     String userEmail, String creationTime, String baseUrl){
        boolean existsInPhotos = false;
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM PHOTOS WHERE assetId = ?" +
                " and fileHash = ? and fileId =? and userEmail = ?)";
        try (Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{String.valueOf(assetId),
                fileHash, fileId, userEmail})) {
            if (cursor != null && cursor.moveToFirst()) {
                int result = cursor.getInt(0);
                if (result == 1) {
                    existsInPhotos = true;
                }
            }
        } catch (Exception e) {
            LogHandler.saveLog("Failed to select from PHOTOS in " +
                    "insertIntoPhotosTable method: " + e.getLocalizedMessage());
        }
        if(!existsInPhotos){
            dbWritable.beginTransaction();
            try{
                sqlQuery = "INSERT INTO PHOTOS (" +
                        "assetId," +
                        "fileId," +
                        "fileName, " +
                        "userEmail, " +
                        "creationTime, " +
                        "fileHash, " +
                        "baseUrl) VALUES (?,?,?,?,?,?,?)";
                   Object[] values = new Object[]{assetId,fileId,
                        fileName,userEmail,creationTime,fileHash,baseUrl};
                dbWritable.execSQL(sqlQuery, values);
                dbWritable.setTransactionSuccessful();
            }catch (Exception e){
                LogHandler.saveLog("Failed to save into the" +
                        " database in insertIntoPhotosTable method. "+e.getLocalizedMessage());
            }finally {
                dbWritable.endTransaction();
            }
        }
    }
    public static void insertIntoDriveTable(Long assetId, String fileId,String fileName, String fileHash,String userEmail){
        String sqlQuery = "";
        Boolean existsInDrive = false;
        sqlQuery = "SELECT EXISTS(SELECT 1 FROM DRIVE WHERE assetId = ? " +
                "and fileHash = ? and fileId =? and userEmail = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,new String[]{String.valueOf(assetId),
                fileHash, fileId, userEmail});
        try{
            if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    existsInDrive = true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to select from DRIVE " +
                    "in insertIntoDriveTable method: " + e.getLocalizedMessage());
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }

        if(existsInDrive == false){
            dbWritable.beginTransaction();
            try{
                sqlQuery = "INSERT INTO DRIVE (" +
                        "assetId," +
                        "fileId," +
                        "fileName, " +
                        "userEmail, " +
                        "fileHash) VALUES (?,?,?,?,?)";
                Object[] values = new Object[]{assetId,fileId,fileName,userEmail,fileHash};
                dbWritable.execSQL(sqlQuery, values);
                dbWritable.setTransactionSuccessful();
            }catch (Exception e){
                LogHandler.saveLog("Failed to save into the database" +
                        " in insertIntoDriveTable method. "+e.getLocalizedMessage());
            }finally {
                dbWritable.endTransaction();
            }
        }
    }

    public void insertTransactionsData(String source, String fileName, String destination
            ,String assetId, String operation, String fileHash) {
        dbWritable.beginTransaction();
       try{
            String sqlQuery = "INSERT INTO TRANSACTIONS(source, fileName, destination, assetId, operation, hash, date)" +
                    " VALUES (?,?,?,?,?,?,?);";
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            dbWritable.execSQL(sqlQuery, new Object[]{source,fileName, destination, assetId, operation, fileHash, timestamp});
            dbWritable.setTransactionSuccessful();
        }catch (Exception e){
            LogHandler.saveLog("Failed to insert data into ASSET : " + e.getLocalizedMessage() , true);
        }finally {
            dbWritable.endTransaction();
        }
    }

    public void insertIntoAccounts(String userEmail,String type,String refreshToken ,String accessToken,
                            Double totalStorage , Double usedStorage , Double usedInDriveStorage ,
                                   Double UsedInGmailAndPhotosStorage) {
        dbWritable.beginTransaction();
        try{
            String sqlQuery = "INSERT INTO ACCOUNTS (" +
                    "userEmail," +
                    "type, " +
                    "refreshToken, " +
                    "accessToken, " +
                    "totalStorage," +
                    "usedStorage," +
                    "usedInDriveStorage,"+
                    "UsedInGmailAndPhotosStorage) VALUES (?,?,?,?,?,?,?,?)";
            Object[] values = new Object[]{userEmail, type,refreshToken ,accessToken,
                    totalStorage ,usedStorage ,usedInDriveStorage ,UsedInGmailAndPhotosStorage};
            dbWritable.execSQL(sqlQuery, values);
            dbWritable.setTransactionSuccessful();
        }catch (Exception e){
            LogHandler.saveLog("Failed to save into the database " +
                    "in insertIntoAccounts method : "+e.getLocalizedMessage());
        }finally {
            dbWritable.endTransaction();
        }
    }


    public static void dropTable(String tableName){
        try{
            dbWritable.beginTransaction();
            String sql = "DROP TABLE IF EXISTS " + tableName;
            dbWritable.execSQL(sql);
            dbWritable.setTransactionSuccessful();
        }catch (Exception e){
            LogHandler.saveLog("Failed to drop the table in dropTable method : " + e.getLocalizedMessage());
        }finally {
            dbWritable.endTransaction();
        }

    }

    public static List<String []> getAccounts(String[] columns){
        List<String[]> resultList = new ArrayList<>();

        String sqlQuery = "SELECT ";
        for (String column:columns){
            sqlQuery += column + ", ";
        }
        sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 2);
        sqlQuery += " FROM ACCOUNTS" ;
        Cursor cursor = dbReadable.rawQuery(sqlQuery, null);
        if (cursor.moveToFirst()) {
            do {
                String[] row = new String[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    int columnIndex = cursor.getColumnIndex(columns[i]);
                    if (columnIndex >= 0) {
                        row[i] = cursor.getString(columnIndex);
                    }
                }
                resultList.add(row);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return resultList;
    }

    public void updateAccounts(String userEmail, Map<String, Object> updateValues,String type) {
        dbWritable.beginTransaction();
        try {
            StringBuilder sqlQueryBuilder = new StringBuilder("UPDATE ACCOUNTS SET ");

            List<Object> valuesList = new ArrayList<>();
            for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
                String columnName = entry.getKey();
                Object columnValue = entry.getValue();

                sqlQueryBuilder.append(columnName).append(" = ?, ");
                valuesList.add(columnValue);
            }

            sqlQueryBuilder.delete(sqlQueryBuilder.length() - 2, sqlQueryBuilder.length());//delete the last comma
            sqlQueryBuilder.append(" WHERE userEmail = ? and type = ?");
            valuesList.add(userEmail);
            valuesList.add(type);

            String sqlQuery = sqlQueryBuilder.toString();
            Object[] values = valuesList.toArray(new Object[0]);
            dbWritable.execSQL(sqlQuery, values);
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to update the database in updateAccounts method : " + e.getLocalizedMessage(), true);
        } finally {
            dbWritable.endTransaction();
        }
    }

    public boolean deleteFromAccountsTable(String userEmail, String type) {
        dbWritable.beginTransaction();
        try {
            String sqlQuery = "DELETE FROM ACCOUNTS WHERE userEmail = ? and type = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{userEmail,type});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete the database in deleteAccounts method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
        if (!accountExists(userEmail,type)){
            return true;
        }else{
            return false;
        }

    }

    public boolean deleteAccountFromDriveTable(String userEmail) {
        dbWritable.beginTransaction();
        try {
            String sqlQuery = "DELETE FROM DRIVE WHERE userEmail = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{userEmail});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete from drive in deleteFromDriveTable method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
        if(!accountExistsInDriveTable(userEmail)){
            return true;
        }else{
            return false;
        }
    }

    public boolean deleteAccountFromPhotosTable(String userEmail) {
        dbWritable.beginTransaction();
        try {
            String sqlQuery = "DELETE FROM PHOTOS WHERE userEmail = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{userEmail});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete from photos in deleteFromPhotosTable method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
        if(!accountExistsInPhotosTable(userEmail)){
            return true;
        }else{
            return false;
        }
    }

    public List<String []> getAndroidTable(String[] columns){
        List<String[]> resultList = new ArrayList<>();

        String sqlQuery = "SELECT ";
        for (String column:columns){
            sqlQuery += column + ", ";
        }
        sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 2);
        sqlQuery += " FROM ANDROID ORDER BY dateModified ASC" ;
        Cursor cursor = dbReadable.rawQuery(sqlQuery, null);
        if (cursor.moveToFirst()) {
            do {
                String[] row = new String[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    int columnIndex = cursor.getColumnIndex(columns[i]);
                    if (columnIndex >= 0) {
                        row[i] = cursor.getString(columnIndex);
                    }
                }
                resultList.add(row);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return resultList;
    }

    public void insertIntoAndroidTable(long assetId,String fileName,String filePath,String device,
                                       String fileHash, Double fileSize,String dateModified,String memeType) {
        fileHash = fileHash.toLowerCase();
        String sqlQuery = "";
        boolean existsInAndroid = existsInAndroid(assetId, filePath, device, fileSize, fileHash);
        if(existsInAndroid == false){
            dbWritable.beginTransaction();
            try{
                sqlQuery = "INSERT INTO ANDROID (" +
                        "assetId," +
                        "fileName," +
                        "filePath, " +
                        "device, " +
                        "fileSize, " +
                        "fileHash," +
                        "dateModified," +
                        "memeType) VALUES (?,?,?,?,?,?,?,?)";
                Object[] values = new Object[]{assetId,fileName,filePath,device,
                        fileSize,fileHash,dateModified,memeType};
                dbWritable.execSQL(sqlQuery, values);
                dbWritable.setTransactionSuccessful();
            }catch (Exception e){
                LogHandler.saveLog("Failed to save into the database.in insertIntoAndroidTable method. "+e.getLocalizedMessage());
            }finally {
                dbWritable.endTransaction();
            }
        }
    }

    private static void deleteFromAndroidTable(String filePath, String assetId){
        dbWritable.beginTransaction();
        try {
            String sqlQuery = "DELETE FROM ANDROID WHERE filePath = ? and assetId = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{filePath, assetId});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete the database in ANDROID, deleteRedundantAndroid method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
    }

    public boolean deleteFromAndroidTable(String assetId,String fileSize, String filePath, String fileName, String fileHash){
        String sqlQuery = "DELETE FROM ANDROID WHERE fileSize = ?  and fileHash = ? and fileName =  ? and filePath = ?";
        dbWritable.beginTransaction();
        try {
            Object[] values = new Object[]{fileSize,fileHash,fileName,filePath};
            dbWritable.execSQL(sqlQuery, values);
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete from the database in deleteFromAndroidTable method. "
                    + e.getLocalizedMessage(), true);
        } finally {
            dbWritable.endTransaction();
        }

        boolean existsInAndroid = existsInAndroid(Long.valueOf(assetId), filePath, MainActivity.androidDeviceName,
                Double.valueOf(fileSize), fileHash);
        if(!existsInAndroid){
            return true;
        }
        return false;
    }

    private boolean existsInAndroid(long assetId, String filePath, String device,
                                    Double fileSize, String fileHash){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ANDROID WHERE" +
                " assetId = ? and filePath = ? and fileHash = ? and fileSize = ? and device = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,new String[]{String.valueOf(assetId), filePath, fileHash,
                String.valueOf(fileSize), device});
        try{
            if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    return true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to check existing in Android : " + e.getLocalizedMessage());
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return false;
    }

    public boolean existsInAndroidWithoutHash(String filePath, String device,String date,
                                    Double fileSize){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ANDROID WHERE" +
                " filePath = ? and fileSize = ? and device = ? and dateModified = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,new String[]{filePath,
                String.valueOf(fileSize), device, date});
        try{
            if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    return true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to check existing in Android : " + e.getLocalizedMessage());
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return false;
    }

    public void deleteFileFromDriveTable(String fileHash, String id, String assetId, String fileId, String userEmail){
        String sqlQuery  = "DELETE FROM DRIVE WHERE fileHash = ? and id = ? and assetId = ? and fileId = ? and userEmail = ?";
        dbWritable.execSQL(sqlQuery, new String[]{fileHash, id, assetId, fileId, userEmail});

        boolean existsInDatabase = assetExistsInDatabase(assetId);
        if (existsInDatabase == false) {
            dbWritable = getWritableDatabase();
            dbWritable.beginTransaction();
            try {
                sqlQuery = "DELETE FROM ASSET WHERE id = ? ";
                dbWritable.execSQL(sqlQuery, new Object[]{assetId});
                dbWritable.setTransactionSuccessful();
            } catch (Exception e) {
                LogHandler.saveLog("Failed to delete the database" +
                        " in ASSET , deleteFileFromDriveTable method : " + e.getLocalizedMessage());
            } finally {
                dbWritable.endTransaction();
            }
        }
    }

    public List<String[]> getDriveTable(String[] columns, String userEmail){
        List<String[]> resultList = new ArrayList<>();

        String sqlQuery = "SELECT ";
        for (String column:columns){
            sqlQuery += column + ", ";
        }

        sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 2);
        sqlQuery += " FROM DRIVE WHERE userEmail = ?" ;
        Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{userEmail});
        if (cursor.moveToFirst()) {
            do {
                String[] row = new String[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    int columnIndex = cursor.getColumnIndex(columns[i]);
                    if (columnIndex >= 0) {
                        row[i] = cursor.getString(columnIndex);
                    }
                }
                resultList.add(row);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return resultList;
    }

    public int countAndroidAssets(){
        String sqlQuery = "SELECT COUNT(filePath) AS pathCount FROM ANDROID where device = ?";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{MainActivity.androidDeviceName});
        int pathCount = 0;
        if(cursor != null){
            cursor.moveToFirst();
            int pathCountColumnIndex = cursor.getColumnIndex("pathCount");
            if(pathCountColumnIndex >= 0){
                pathCount = cursor.getInt(pathCountColumnIndex);
            }
        }
        if(pathCount == 0){
            LogHandler.saveLog("No android file was found in count android assets.",false);
        }
        cursor.close();
        return pathCount;
    }

    public int countAndroidSyncedAssets(){
        String sqlQuery = "SELECT COUNT(DISTINCT androidTable.filePath) AS rowCount FROM ANDROID androidTable\n" +
                "JOIN DRIVE driveTable ON driveTable.assetId = androidTable.assetId WHERE androidTable.device = ?;";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{MainActivity.androidDeviceName});
        int count = 0;
        if(cursor != null && cursor.moveToFirst()){
            count = cursor.getInt(0);
        }
        if(count == 0){
            LogHandler.saveLog("No android synced asset was found in countAndroidSyncedAssets",false);
        }
        cursor.close();
        return count;
    }

    public boolean backupAccountExists(){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ACCOUNTS WHERE type = 'backup')";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, null);
        boolean exists = false;
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getInt(0);
            if(result == 1){
                exists = true;
            }
        }
        cursor.close();
        return exists;
    }

    public static boolean accountExists(String userEmail, String type){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ACCOUNTS WHERE userEmail = ? and type = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,  new String[]{userEmail, type});
        boolean exists = false;
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getInt(0);
            if(result == 1){
                exists = true;
            }
        }
        cursor.close();
        return exists;
    }

    public static boolean accountExistsInDriveTable(String userEmail){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM DRIVE WHERE userEmail = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,  new String[]{userEmail});
        boolean exists = false;
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getInt(0);
            if(result == 1){
                exists = true;
            }
        }
        cursor.close();
        return exists;
    }

    public static boolean accountExistsInPhotosTable(String userEmail){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM PHOTOS WHERE userEmail = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,  new String[]{userEmail});
        boolean exists = false;
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getInt(0);
            if(result == 1){
                exists = true;
            }
        }
        cursor.close();
        return exists;
    }

    public static boolean assetExistsInAssetTable(String assetId){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM ASSET WHERE id = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,  new String[]{assetId});
        boolean exists = false;
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getInt(0);
            if(result == 1){
                exists = true;
            }
        }
        cursor.close();
        return exists;
    }

    public static String getAccessToken(String userEmail){
        String sqlQuery = "SELECT accessToken FROM ACCOUNTS WHERE userEmail = ?";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,  new String[]{userEmail});
        String accessToken = "";
        if(cursor != null && cursor.moveToFirst()){
            int result = cursor.getColumnIndex("accessToken");
            if(result >= 0){
                accessToken = cursor.getString(result);
            }
        }
        cursor.close();
        return accessToken;
    }

    public void deleteRedundantDriveFromDB(ArrayList<String> fileIds, String userEmail){
        String sqlQuery = "SELECT * FROM DRIVE where userEmail = ?";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, new String[]{userEmail});
        if(cursor.moveToFirst()){
            do{
                int fileIdColumnIndex = cursor.getColumnIndex("fileId");
                if(fileIdColumnIndex >= 0) {
                    String fileId = cursor.getString(fileIdColumnIndex);
                    if (!fileIds.contains(fileId)) {
                        deleteDriveEntry(fileId);

                        int assetIdColumnIndex = cursor.getColumnIndex("assetId");
                        boolean existsInDatabase = false;
                        String assetId = "";
                        if (assetIdColumnIndex >= 0) {
                            assetId = cursor.getString(assetIdColumnIndex);
                            existsInDatabase = assetExistsInDatabase(assetId);
                        }

                        if (existsInDatabase == false) {
                            deleteFromAssetTable(assetId);
                        }
                    }
                }
            }while (cursor.moveToNext());
        }
        cursor.close();
    }

    private static void deleteDriveEntry(String fileId) {
        String sqlQuery = "DELETE FROM DRIVE WHERE fileId = ?";
        dbWritable.beginTransaction();
        try {
            dbWritable.execSQL(sqlQuery, new Object[]{fileId});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete the database in DRIVE, deleteRedundantDRIVE method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
    }

    private static void deleteFromAssetTable(String assetId){
        String sqlQuery = "DELETE FROM ASSET WHERE id = ? ";
        dbWritable.beginTransaction();
        try {
            dbWritable.execSQL(sqlQuery, new Object[]{assetId});
            dbWritable.setTransactionSuccessful();
        } catch (Exception e) {
            LogHandler.saveLog("Failed to delete the database in ASSET , deleteRedundantDrive method. " + e.getLocalizedMessage());
        } finally {
            dbWritable.endTransaction();
        }
    }

    public static boolean androidFileExistsInDrive(Long assetId,String fileHash){
        Boolean existsInDrive = false;
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM DRIVE WHERE assetId = ? " +
                "and fileHash = ?)";
        Cursor cursor = MainActivity.dbHelper.dbReadable.rawQuery(sqlQuery,new String[]{String.valueOf(assetId), fileHash});
        try{
            if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    existsInDrive = true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to check if android file exists in drive : " +  e.getLocalizedMessage(), true);
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return existsInDrive;
    }

    public void deleteRedundantAndroidFromDB(){
        String sqlQuery = "SELECT * FROM ANDROID";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, null);
        if(cursor.moveToFirst()){
            do{
                String filePath = "";String assetId = "";String device = "";String fileHash = "";String fileName = "";
                int filePathColumnIndex = cursor.getColumnIndex("filePath");
                int deviceColumnIndex = cursor.getColumnIndex("device");
                int assetIdColumnIndex = cursor.getColumnIndex("assetId");
                int fileHashColumnIndex = cursor.getColumnIndex("fileHash");
                int fileNameColumnIndex = cursor.getColumnIndex("fileName");
                if(filePathColumnIndex >= 0){
                    filePath = cursor.getString(filePathColumnIndex);
                    assetId = cursor.getString(assetIdColumnIndex);
                    fileName = cursor.getString(fileNameColumnIndex);
                    device = cursor.getString(deviceColumnIndex);
                    fileHash = cursor.getString(fileHashColumnIndex);
                }

                File androidFile = new File(filePath);
                if (!androidFile.exists() && device.equals(MainActivity.androidDeviceName)){
                    deleteFromAndroidTable(filePath, assetId);
                    insertTransactionsData(filePath,fileName,device,assetId,"deletedInDevice",fileHash);

                    boolean existsInDatabase = false;
                    existsInDatabase = assetExistsInDatabase(assetId);

                    if(existsInDatabase == false){
                        dbWritable.beginTransaction();
                        deleteFromAssetTable(assetId);
                    }
                }
            }while (cursor.moveToNext());
        }
        cursor.close();
    }

    public void deleteRedundantAsset(){
        ArrayList<String> assetIds = selectAllAssetIds();
        for (String assetId : assetIds){
            boolean existsInDatabase = false;
            existsInDatabase = assetExistsInDatabase(assetId);

            if (existsInDatabase == false) {
                deleteFromAssetTable(assetId);
            }
        }
    }

    private ArrayList<String> selectAllAssetIds() {
        ArrayList<String> assetIds = new ArrayList<>();
        String sqlQuery = "SELECT id FROM ASSET";
        Cursor cursor = dbReadable.rawQuery(sqlQuery, null);
        if (cursor.moveToFirst()) {
            do {
                int assetIdColumnIndex = cursor.getColumnIndex("id");
                if (assetIdColumnIndex >= 0) {
                    String assetId = cursor.getString(assetIdColumnIndex);
                    assetIds.add(assetId);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        return assetIds;
    }

    public List<String> backUpDataBase(Context context) {

        String dataBasePath = context.getDatabasePath("CSODatabase").getPath();
        final String[] userEmail = {""};
        final String[] uploadFileId = new String[1];

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> uploadTask = () -> {
            try {
                String driveBackupAccessToken = "";
                String[] drive_backup_selected_columns = {"userEmail", "type", "accessToken"};
                List<String[]> drive_backUp_accounts = MainActivity.dbHelper.getAccounts(drive_backup_selected_columns);
                for (String[] drive_backUp_account : drive_backUp_accounts) {
                    if (drive_backUp_account[1].equals("backup")) {
                        driveBackupAccessToken = drive_backUp_account[2];
                        userEmail[0] = drive_backUp_account[0];
                        break;
                    }
                }
                Drive service = GoogleDrive.initializeDrive(driveBackupAccessToken);
                String folder_name = "Stash_DataBase";
                String backupDbFolderId = null;
                com.google.api.services.drive.model.File folder = null;

                FileList fileList = service.files().list()
                        .setQ("mimeType='application/vnd.google-apps.folder' and name='"
                                + folder_name + "'")
                        .setSpaces("drive")
                        .setFields("files(id)")
                        .execute();
                List<com.google.api.services.drive.model.File> driveFolders = fileList.getFiles();
                for(com.google.api.services.drive.model.File driveFolder: driveFolders){
                    backupDbFolderId = driveFolder.getId();
                }

                if (backupDbFolderId == null) {
                    com.google.api.services.drive.model.File folder_metadata =
                            new com.google.api.services.drive.model.File();
                    folder_metadata.setName(folder_name);
                    folder_metadata.setMimeType("application/vnd.google-apps.folder");
                    folder = service.files().create(folder_metadata)
                            .setFields("id").execute();

                    backupDbFolderId = folder.getId();
                }


                com.google.api.services.drive.model.File fileMetadata =
                            new com.google.api.services.drive.model.File();
                fileMetadata.setName("StashDatabase.db");
                fileMetadata.setParents(java.util.Collections.singletonList(backupDbFolderId));

                File androidFile = new File(dataBasePath);
                if (!androidFile.exists()) {
                    LogHandler.saveLog("Failed to upload database from Android to backup because it doesn't exist");
                }
                FileContent mediaContent = new FileContent("application/x-sqlite3", androidFile);
                if (mediaContent == null) {
                    LogHandler.saveLog("Failed to upload database from Android to backup because it's null");
                }
                com.google.api.services.drive.model.File uploadFile =
                        service.files().create(fileMetadata, mediaContent).setFields("id").execute();
                uploadFileId[0] = uploadFile.getId();
//                while (uploadFileId[0] == null) {
//                    wait();
//                }
                if (uploadFileId[0] == null | uploadFileId[0].isEmpty()) {
                    LogHandler.saveLog("Failed to upload database from Android to backup because it's null");
                } else {
                    LogHandler.saveLog("Uploading database from android into backup " +
                            "account uploadId : " + uploadFileId[0], false);
                }
            } catch (Exception e) {
                LogHandler.saveLog("Failed to upload database from Android to backup : " + e.getLocalizedMessage());
            }
            return uploadFileId[0];
        };
        Future<String> future = executor.submit(uploadTask);
        String uploadFileIdFuture = new String();
        try{
            uploadFileIdFuture = future.get();
        }catch (Exception e){
            System.out.println(e.getLocalizedMessage());
        }
        List<String> result = Arrays.asList(userEmail[0],uploadFileIdFuture);
        return result;
    }

    public boolean backUpProfileMap(boolean hasRemoved,String signedOutEmail) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final boolean[] isBackedUp = {false};
        Callable<Boolean> uploadTask = () -> {
            try {
                String driveBackupAccessToken = "";
                String[] selected_columns = {"userEmail", "type", "accessToken"};
                List<String[]> account_rows = MainActivity.dbHelper.getAccounts(selected_columns);
//                ArrayList<String> backupAccountsDb = new ArrayList<>();
//                ArrayList<String> primaryAccountsDb  = new ArrayList<>();
//                for(String[] account_row: account_rows){
//                    if (account_row[1].equals("backup")) {
//                        primaryAccountsDb.add(account_row[1]);
//                    }else if(account_row[1].equals("primary")){
//                        primaryAccountsDb.add(account_row[1]);
//                    }
//                }

                int backUpAccountCounts = 0;
                for (String[] account_row : account_rows) {
                    if (hasRemoved == true && account_row[0].equals(signedOutEmail)) {
                        continue;
                    }
                    if (account_row[1].equals("backup")){

                        backUpAccountCounts ++;
                        driveBackupAccessToken = account_row[2];
//                        String userEmail = account_row[0];
                        Drive service = GoogleDrive.initializeDrive(driveBackupAccessToken);
                        String folder_name = "stash_user_profile";
                        String profileFolderId = null;
                        com.google.api.services.drive.model.File folder = null;

                        FileList fileList = service.files().list()
                                .setQ("mimeType='application/vnd.google-apps.folder' and name='"
                                        + folder_name + "' and trashed=false")
                                .setSpaces("drive")
                                .setFields("files(id)")
                                .execute();
                        List<com.google.api.services.drive.model.File> driveFolders = fileList.getFiles();
                        for(com.google.api.services.drive.model.File driveFolder: driveFolders){
                            profileFolderId = driveFolder.getId();
                        }

                        if (profileFolderId == null) {
                            com.google.api.services.drive.model.File folder_metadata =
                                    new com.google.api.services.drive.model.File();
                            folder_metadata.setName(folder_name);
                            folder_metadata.setMimeType("application/vnd.google-apps.folder");
                            folder = service.files().create(folder_metadata)
                                    .setFields("id").execute();

                            profileFolderId = folder.getId();
                        }

                        fileList = service.files().list()
                                .setQ("name contains 'profileMap' and '" + profileFolderId + "' in parents")
                                .setSpaces("drive")
                                .setFields("files(id)")
                                .execute();
                        List<com.google.api.services.drive.model.File> existingFiles = fileList.getFiles();
                        for (com.google.api.services.drive.model.File existingFile : existingFiles) {
                            service.files().delete(existingFile.getId()).execute();
                        }

                        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                        fileMetadata.setName("profileMap.json");
                        fileMetadata.setParents(java.util.Collections.singletonList(profileFolderId));

                        String content = Profile.createProfileMapContent(account_row[0]).toString();

                        ByteArrayContent mediaContent = ByteArrayContent.fromString("application/json", content);

                        com.google.api.services.drive.model.File uploadedFile = service.files().create(fileMetadata, mediaContent)
                                .setFields("id")
                                .execute();

                        String uploadedFileId = uploadedFile.getId();

//                        while (uploadedFileId == null) {
//                            this.wait();");
//                        }
                        if (uploadedFileId == null | uploadedFileId.isEmpty()) {
                            LogHandler.saveLog("Failed to upload profileMap from Android to backup because it's null");
                        }else{
                            isBackedUp[0] = true;
                        }
//                        JsonObject profileMapContent = Profile.readProfileMapContent(userEmail);
//                        JsonArray backupAccounts = profileMapContent.get("backupAccounts").getAsJsonArray();
//                        ArrayList<String> backUpUserEmails = new ArrayList<>();
//                        for (int i = 0; i < backupAccounts.size(); i++) {
//                            try {
//                                JsonObject backupAccount = backupAccounts.get(i).getAsJsonObject();
//                                String backupEmail = backupAccount.get("backupEmail").getAsString();
//                                backUpUserEmails.add(backupEmail);
//                            }catch (Exception e){}
//                        }
//                        JsonArray primaryAccounts = profileMapContent.get("backupAccounts").getAsJsonArray();
//                        ArrayList<String> primaryUserEmails = new ArrayList<>();
//                        for (int i = 0; i < primaryAccounts.size(); i++) {
//                            try {
//                                JsonObject primaryAccount = primaryAccounts.get(i).getAsJsonObject();
//                                String primaryEmail = primaryAccount.get("primaryEmail").getAsString();
//                                primaryUserEmails.add(primaryEmail);
//                            }catch (Exception e){}
//                        }
//                        if(primaryUserEmails.containsAll(primaryAccountsDb) && backUpUserEmails.containsAll(backupAccountsDb)){
//                            isBackedUp = true;
//                        }
                    }
                }
                if(backUpAccountCounts == 0){
                    isBackedUp[0] = true;
                }
            } catch (Exception e) {
                LogHandler.saveLog("Failed to upload profileMap from Android to backup in backUpProfileMap: " + e.getLocalizedMessage());
            }
            return isBackedUp[0];
        };

        Future<Boolean> future = executor.submit(uploadTask);
        boolean isBackedUpFuture = false;
        try{
            isBackedUpFuture = future.get();
        }catch (Exception e){
            System.out.println(e.getLocalizedMessage());
        }
        return isBackedUpFuture;
    }

    public static boolean insertIntoDeviceTable(String deviceName,String totalSpace,String freeSpace){
        if (deviceNameExists(deviceName)){
            return updateDeviceTable(deviceName, freeSpace);
        }
        dbWritable.beginTransaction();
        try {
            String sqlQuery = "INSERT INTO DEVICE (" +
                    "deviceName," +
                    "totalStorage," +
                    "freeStorage) VALUES (?,?,?)";
            dbWritable.execSQL(sqlQuery, new Object[]{deviceName, totalSpace, freeSpace});
            dbWritable.setTransactionSuccessful();
            return true;
        }catch (SQLiteConstraintException e1){
            updateDeviceTable(deviceName, totalSpace, freeSpace);
            e1.printStackTrace();
            return true;
        }catch (Exception e){
            LogHandler.saveLog("Failed to insert into DEVICE table : " + e.getLocalizedMessage());
            return false;
        }finally {
            dbWritable.endTransaction();
        }
    }

    public static boolean updateDeviceTable(String deviceName,String freeSpace){
        dbWritable.beginTransaction();
        try{
            String sqlQuery = "UPDATE DEVICE SET freeStorage = ? WHERE deviceName = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{freeSpace,deviceName});
            dbWritable.setTransactionSuccessful();
            return true;
        }catch (Exception e){
            LogHandler.saveLog("Failed to update DEVICE table : " + e.getLocalizedMessage());
            return false;
        }finally {
            dbWritable.endTransaction();
        }
    }

    public static boolean deviceNameExists(String deviceName){
        String sqlQuery = "SELECT EXISTS(SELECT 1 FROM DEVICE WHERE deviceName = ?)";
        Cursor cursor = dbReadable.rawQuery(sqlQuery,new String[]{deviceName});
        boolean exists = false;
        try{
            if(cursor != null && cursor.moveToFirst()){
                int result = cursor.getInt(0);
                if(result == 1){
                    exists = true;
                }
            }
        }catch (Exception e){
            LogHandler.saveLog("Failed to check if device name exists : " + e.getLocalizedMessage());
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return exists;
    }

    public static boolean updateDeviceTable(String deviceName,String totalStorage,String freeSpace){
        dbWritable.beginTransaction();
        try{
            String sqlQuery = "UPDATE DEVICE SET totalStorage = ? and freeStorage = ? WHERE deviceName = ?";
            dbWritable.execSQL(sqlQuery, new Object[]{totalStorage,freeSpace ,deviceName ,});
            dbWritable.setTransactionSuccessful();
            return true;
        }catch (Exception e){
            LogHandler.saveLog("Failed to update DEVICE table : " + e.getLocalizedMessage());
            return false;
        }finally {
            dbWritable.endTransaction();
        }
    }


    public String getPhotosAndVideosStorage(){
        createIndex();
        double sum = 0.0;
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT SUM(fileSize) FROM ANDROID";
        Cursor cursor = db.rawQuery(query, null);

        try {
            if (cursor.moveToFirst()) {
                sum = cursor.getDouble(0) / Math.pow(10, 3);
            }
        } catch (Exception e) {
            LogHandler.saveLog("Failed to get storage of videos and photos : " + e.getLocalizedMessage(), true);
        } finally {
            cursor.close();
        }

        return String.format("%.2f GB", sum);
    }

    public void createIndex() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS fileSize_index ON ANDROID(fileSize)");
        } catch (Exception e) {
            LogHandler.saveLog("Failed to create index: " + e.getLocalizedMessage(), true);
        }
    }
}
