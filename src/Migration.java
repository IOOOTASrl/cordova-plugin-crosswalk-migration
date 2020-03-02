package cordova.plugins.crosswalk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ComponentName;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import java.io.File;


public class Migration extends CordovaPlugin {

    public static final String TAG = "Migration";

    private static boolean hasRun = false;

    private static String XwalkPath = "app_xwalkcore/Default";

    // Root dir for system webview data used by Android 4.4+
    private static String modernWebviewDir = "app_webview";

    // Root dir for system webview data used by Android 4.3 and below
    private static String oldWebviewDir = "app_database";

    // Directory name for local storage files used by Android 4.4+ and XWalk
    private static String modernLocalStorageDir = "Local Storage";

    // Directory name for local storage files used by Android 4.3 and below
    private static String oldLocalStorageDir = "localstorage";

    // Storage directory names used by Android 4.4+ and XWalk
    private static String[] modernAndroidStorage = {
            "Cache",
            "Cookies",
            "Cookies-journal",
            "IndexedDB",
            "databases"
    };

    private Activity activity;
    private Context context;

    private String alertTitle;
    private String alertMessage;

    private boolean isModernAndroid;
    private File appRoot;
    private File XWalkRoot;
    private File webviewRoot;

    public Migration() {}

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        alertTitle = webView.getPreferences().getString("XwalkMigration.alertTitle", "App");
        alertMessage = webView.getPreferences().getString("XwalkMigration.alertMessage", "Application data updated. Press OK to restart.");

        Log.d(TAG, "initialize()");

        // Crosswalk sanity check
        try {
            Class.forName("org.crosswalk.engine.XWalkCordovaView");
            Log.d(TAG, "initialize::crosswalk_enabled");
            return;
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "initialize::crosswalk_not_found");
        }

        if(!hasRun){
            hasRun = true;
            activity = cordova.getActivity();
            context = activity.getApplicationContext();
            isModernAndroid = Build.VERSION.SDK_INT >= 19;
            run();
        }

        super.initialize(cordova, webView);
    }

    private void run(){
        Log.d(TAG, "running Crosswalk migration shim");


        boolean found = lookForXwalk(context.getFilesDir());
        if(!found){
            lookForXwalk(context.getExternalFilesDir(null));
        }

        if(found){
            migrateData();
        }
    }

    private boolean lookForXwalk(File filesPath){
        File root = getStorageRootFromFiles(filesPath);
        boolean found = testFileExists(root, XwalkPath);
        if(found){
            Log.d(TAG, "found Crosswalk directory");
            appRoot = root;
        }else{
            Log.d(TAG, "Crosswalk directory NOT FOUND");
        }
        return found;
    }

    private void migrateData(){
        XWalkRoot = constructFilePaths(appRoot, XwalkPath);

        webviewRoot = constructFilePaths(appRoot, getWebviewPath());

        boolean hasMigratedData = false;

        if(testFileExists(XWalkRoot, modernLocalStorageDir)){
            Log.d(TAG, "Local Storage data found");
            moveDirFromXWalkToWebView(modernLocalStorageDir, getWebviewLocalStoragePath());
            Log.d(TAG, "Moved Local Storage from XWalk to System Webview");
            hasMigratedData = true;
        }

        if(isModernAndroid){
            for(String dirName : modernAndroidStorage){
                if(testFileExists(XWalkRoot, dirName)) {
                    moveDirFromXWalkToWebView(dirName);
                    Log.d(TAG, "Moved " + dirName + " from XWalk to System Webview");
                    hasMigratedData = true;
                }
            }
        }

        if(hasMigratedData){
            deleteRecursive(XWalkRoot);
            restartCordova();
        }
    }

    private void moveDirFromXWalkToWebView(String dirName){
        File XWalkLocalStorageDir = constructFilePaths(XWalkRoot, dirName);
        File webviewLocalStorageDir = constructFilePaths(webviewRoot, dirName);
        XWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
    }

    private void moveDirFromXWalkToWebView(String sourceDirName, String targetDirName){
        File XWalkLocalStorageDir = constructFilePaths(XWalkRoot, sourceDirName);
        File webviewLocalStorageDir = constructFilePaths(webviewRoot, targetDirName);
        XWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
    }


    private String getWebviewPath(){
        if(isModernAndroid){
            return modernWebviewDir;
        }else{
            return oldWebviewDir;
        }
    }

    private String getWebviewLocalStoragePath(){
        if(isModernAndroid){
            return modernLocalStorageDir;
        }else{
            return oldLocalStorageDir;
        }
    }

    private void restartCordova(){
        Builder dlg = new AlertDialog.Builder(cordova.getActivity());
        dlg.setCancelable(false);
        dlg.setTitle(alertTitle);
        dlg.setMessage(alertMessage);
        dlg.setPositiveButton( "OK", new AlertDialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                triggerRebirth(cordova.getActivity().getApplicationContext());
                dialog.dismiss();
            }
        });
        dlg.create();
        AlertDialog dialog =  dlg.show();
    }

    private static void triggerRebirth(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }


    private boolean testFileExists(File root, String name) {
        boolean status = false;
        if (!name.equals("")) {
            File newPath = constructFilePaths(root.toString(), name);
            status = newPath.exists();
            Log.d(TAG, "exists '"+newPath.getAbsolutePath()+": "+status);
        }
        return status;
    }

    private File constructFilePaths (File file1, File file2) {
        return constructFilePaths(file1.getAbsolutePath(), file2.getAbsolutePath());
    }

    private File constructFilePaths (File file1, String file2) {
        return constructFilePaths(file1.getAbsolutePath(), file2);
    }

    private File constructFilePaths (String file1, String file2) {
        File newPath;
        if (file2.startsWith(file1)) {
            newPath = new File(file2);
        }
        else {
            newPath = new File(file1 + "/" + file2);
        }
        return newPath;
    }

    private File getStorageRootFromFiles(File filesDir){
        String filesPath = filesDir.getAbsolutePath();
        filesPath = filesPath.replaceAll("/files", "");
        return new File(filesPath);
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}

