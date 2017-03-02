package com.mercdev.tangotest;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;

import java.io.File;

/**
 * Created by gnusin on 02.03.2017.
 */

public class ImportExportADFHelper {
    public static final int ADF_IMPORT_PERMISSION_CODE = 200;
    public static final int ADF_EXPORT_PERMISSION_CODE = 300;

    private static final String ADF_NAME_LOCAL_FOLDER = "TangoTestADFs";

    public static void importAreaDescriptionFile(Activity activity, String filepath) {
        Intent importIntent = new Intent();
        importIntent.setClassName("com.google.tango", "com.google.atap.tango.RequestImportExportActivity");
        if(importIntent.resolveActivity(activity.getPackageManager()) == null) {
            importIntent = new Intent();
            importIntent.setClassName("com.projecttango.tango", "com.google.atap.tango.RequestImportExportActivity");
        }

        importIntent.putExtra("SOURCE_FILE", filepath);
        activity.startActivityForResult(importIntent, ADF_IMPORT_PERMISSION_CODE);
    }

    public static void exportAreaDescriptionFile(Activity activity, String uuid, String filepathDirectory) {
        Intent exportIntent = new Intent();
        exportIntent.setClassName("com.google.tango", "com.google.atap.tango.RequestImportExportActivity");
        if(exportIntent.resolveActivity(activity.getPackageManager()) == null) {
            exportIntent = new Intent();
            exportIntent.setClassName("com.projecttango.tango", "com.google.atap.tango.RequestImportExportActivity");
        }

        exportIntent.putExtra("SOURCE_UUID", uuid);
        exportIntent.putExtra("DESTINATION_FILE", filepathDirectory);
        activity.startActivityForResult(exportIntent, ADF_EXPORT_PERMISSION_CODE);
    }

    public static String getLastAdfFilePath() {
        File adfDir = getAdfFolder();
        File lastAdfFile = null;
        if (adfDir.listFiles() != null) {
            for (File adfFile : adfDir.listFiles()) {
                if (lastAdfFile == null || lastAdfFile.lastModified() < adfFile.lastModified()) {
                    lastAdfFile = adfFile;
                }
            }
        }

        return (lastAdfFile != null) ? lastAdfFile.getPath() : null;
    }

    public static File getAdfFolder() {
        String folderPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + ADF_NAME_LOCAL_FOLDER;
        File dir = new File(folderPath);
        if (!dir.exists()) {
            dir.mkdir();
        }

        return dir;
    }

}
