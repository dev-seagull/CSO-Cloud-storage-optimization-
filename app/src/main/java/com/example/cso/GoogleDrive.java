package com.example.cso;

import android.database.sqlite.SQLiteDatabase;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.checkerframework.checker.units.qual.A;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GoogleDrive {

    public GoogleDrive() {}

    public static class MediaItem{
        private String mediaItemId;
        private String mediaItemName;
        private  String mediaItemHash;
        private Long videoDuration;

        public MediaItem(String mediaItemId, String mediaItemName,
                         String mediaItemHash, Long videoDuration) {
            this.mediaItemId = mediaItemId;
            this.mediaItemName = mediaItemName;
            this.mediaItemHash = mediaItemHash;
            this.videoDuration = videoDuration;
        }
        public String getMediaItemId() {return mediaItemId;}
        public String getMediaItemName() {return mediaItemName;}
        public Long getVideoDuration() {return videoDuration;}
        public String getMediaItemHash() {return mediaItemHash;}
    }


    public static ArrayList<BackUpAccountInfo.MediaItem> getMediaItems(String accessToken) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final ArrayList<BackUpAccountInfo.MediaItem> mediaItems = new ArrayList<>();
        Callable<ArrayList<BackUpAccountInfo.MediaItem>> backgroundTask = () -> {
            try {
                final NetHttpTransport netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
                final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                HttpRequestInitializer httpRequestInitializer = request -> {
                    request.getHeaders().setAuthorization("Bearer " + accessToken);
                    request.getHeaders().setContentType("application/json");
                };
                Drive driveService = new Drive.Builder(netHttpTransport, jsonFactory, httpRequestInitializer)
                        .setApplicationName("cso").build();
                FileList result = driveService.files().list()
                        .setFields("files(id, name, sha256Checksum)")
                        .execute();
                List<File> files = result.getFiles();
                if (files != null && !files.isEmpty()) {
                    for (File file : files) {
                        if (GooglePhotos.isVideo(GoogleCloud.getMemeType(file.getName())) |
                                GooglePhotos.isImage(GoogleCloud.getMemeType(file.getName()))){
                            BackUpAccountInfo.MediaItem mediaItem = new BackUpAccountInfo.MediaItem(file.getName(),
                                    file.getSha256Checksum().toLowerCase(), file.getId());
                            mediaItems.add(mediaItem);
                        }
                    }
                    LogHandler.saveLog( mediaItems.size() + " files were found in Google Drive back up account",false);
                } else {
                    LogHandler.saveLog("No file was found in Google Drive back up account",false);
                }
                return mediaItems;
            }catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                LogHandler.saveLog("Error when trying to get files from google drive: " + e.getLocalizedMessage());
            }
            return mediaItems;
        };
        Future<ArrayList<BackUpAccountInfo.MediaItem>> future = executor.submit(backgroundTask);
        ArrayList<BackUpAccountInfo.MediaItem> uploadFileIDs_fromFuture = null;
        try {
            uploadFileIDs_fromFuture = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LogHandler.saveLog("Error when trying to get drive files from future: " + e.getLocalizedMessage());
        } finally {
            executor.shutdown();
        }
        return uploadFileIDs_fromFuture;
    }

    public static void deleteDuplicatedMediaItems (String accessToken, String userEmail){
        String[] driveColumns = {"fileHash", "id","assetId", "fileId", "fileName", "userEmail"};
        List<String[]> drive_rows = MainActivity.dbHelper.getDriveTable(driveColumns);

        ArrayList<String> fileHashChecker = new ArrayList<>();
        for(String[] drive_row: drive_rows){
            String fileHash = drive_row[0];
            String id= drive_row[1];
            String assetId = drive_row[2];
            String fileId = drive_row[3];
            String fileName = drive_row[4];
            System.out.println("user email for test:" + drive_row[5]);
            if(fileHashChecker.contains(fileHash) && drive_row[5].equals(userEmail)){
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Callable<Boolean> backgroundTask = () -> {
                   Boolean[] isDeleted = new Boolean[0];
                    try{
                        System.out.println("for test the drive file id is:  " + fileId);

                        URL url = new URL("https://www.googleapis.com/drive/v3/files/" + fileId);

                        for(int i=0; i<3; i++){
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod("DELETE");
                            connection.setRequestProperty("Content-type", "application/json");
                            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                            int responseCode = connection.getResponseCode();
                            LogHandler.saveLog("responseCode of deleting duplicate drive : " + responseCode,false);
                            if(responseCode == HttpURLConnection.HTTP_NO_CONTENT){
                                LogHandler.saveLog("Deleting Duplicated file in backup drive :" +
                                        fileName,false);
                                isDeleted[0] = true;
                                break;
                            }else{
                                LogHandler.saveLog("Retrying to delete duplicated file " + fileName+
                                        "from Drive back up account" +
                                        " with response code of " + responseCode);
                            }
                        }
                    }catch (Exception e){
                        LogHandler.saveLog("error in deleting duplicated media items in drive: " + e.getLocalizedMessage());
                    }
                    return isDeleted[0];
                };
                Future<Boolean> future = executor.submit(backgroundTask);
                Boolean isDeletedFuture = false;
                try{
                    isDeletedFuture = future.get();
                }catch (Exception e ){
                    LogHandler.saveLog("Exception when trying to delete file from drive back up: " + e.getLocalizedMessage());
                }
                if(isDeletedFuture == true){
                    MainActivity.dbHelper.deleteFileFromDriveTable(fileHash, id, assetId, fileId , userEmail);
                }

            }else if(!fileHashChecker.contains(fileHash) && drive_row[5].equals(userEmail)){
                fileHashChecker.add(fileHash);
            }
        }
    }
}