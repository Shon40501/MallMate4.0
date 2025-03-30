package com.example.mallmate40;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * מנהל הרשאות מיקום - מטפל בכל הקשור לבקשת הרשאות מיקום
 */
public class LocationPermissionManager {
    private static final String TAG = "LocationPermissionMgr";

    // קוד בקשת הרשאות
    private static final int REQUEST_LOCATION_PERMISSIONS = 100;

    // הרשאות נדרשות
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    // הרשאת מיקום ברקע (נדרשת רק באנדרואיד 10 ומעלה)
    private static final String BACKGROUND_LOCATION_PERMISSION =
            Manifest.permission.ACCESS_BACKGROUND_LOCATION;

    // ממשק לקולבקים
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    private final AppCompatActivity activity;
    private PermissionCallback callback;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String> backgroundPermissionLauncher;


    public LocationPermissionManager(AppCompatActivity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;

        // רישום מאזינים לתוצאות בקשת הרשאות בשיטה המודרנית
        setupPermissionLaunchers();
    }


    private void setupPermissionLaunchers() {
        // מאזין להרשאות בסיסיות
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;

                    // בדיקה שכל ההרשאות אושרו
                    for (String permission : REQUIRED_PERMISSIONS) {
                        if (result.get(permission) == null || !result.get(permission)) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        // אם קיבלנו את כל ההרשאות הבסיסיות, בקש הרשאות רקע אם צריך
                        if (needsBackgroundPermission()) {
                            requestBackgroundLocationPermission();
                        } else {
                            callback.onPermissionsGranted();
                        }
                    } else {
                        handlePermissionDenial();
                    }
                });

        // מאזין להרשאת מיקום ברקע
        backgroundPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    // גם אם הרשאת הרקע לא אושרה, נמשיך כרגיל
                    // כי ניתן לפעול גם בלעדיה
                    callback.onPermissionsGranted();
                });
    }


    private boolean needsBackgroundPermission() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
    }


    public void requestLocationPermissions() {
        // בדיקה האם שירותי המיקום מופעלים במכשיר
        if (!isLocationEnabled()) {
            showLocationServicesDialog();
            return;
        }

        // בדיקה אם יש כבר את כל ההרשאות הבסיסיות
        if (hasBasicLocationPermissions()) {
            // אם יש הרשאות בסיסיות, בדוק אם צריך הרשאת רקע
            if (needsBackgroundPermission() && !hasBackgroundLocationPermission()) {
                requestBackgroundLocationPermission();
            } else {
                // יש את כל ההרשאות הנדרשות
                callback.onPermissionsGranted();
            }
        } else {
            // בקשת הרשאות בסיסיות
            permissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }


    private void handlePermissionDenial() {
        boolean shouldShowRationale = false;

        // בדיקה אם יש צורך להציג הסבר
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            // הצגת הסבר למה צריך הרשאות
            showPermissionRationaleDialog();
        } else {
            // המשתמש סירב וסימן "אל תשאל שוב"
            showPermissionDeniedDialog();
        }
    }


    private void requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, BACKGROUND_LOCATION_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {

                // הצג הסבר לפני בקשת הרשאת רקע
                new AlertDialog.Builder(activity)
                        .setTitle("הרשאת מיקום ברקע")
                        .setMessage("על מנת לעקוב אחרי מיקומך גם כשהאפליקציה ברקע, " +
                                "נדרשת הרשאת מיקום מורחבת. האם לאפשר?")
                        .setPositiveButton("כן", (dialog, which) -> {
                            // בקשת הרשאת רקע
                            backgroundPermissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION);
                        })
                        .setNegativeButton("לא", (dialog, which) -> {
                            // גם בלי הרשאת רקע עדיין אפשר להשתמש באפליקציה
                            callback.onPermissionsGranted();
                        })
                        .create()
                        .show();
            } else {
                callback.onPermissionsGranted();
            }
        } else {
            callback.onPermissionsGranted();
        }
    }


    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("הרשאות מיקום נדרשות")
                .setMessage("לצורך מעקב אחר המיקום שלך במבנה, " +
                        "האפליקציה צריכה גישה להרשאות מיקום. אנא אשר את ההרשאות.")
                .setPositiveButton("אישור", (dialog, which) -> {
                    // בקשת הרשאות שוב
                    permissionLauncher.launch(REQUIRED_PERMISSIONS);
                })
                .setNegativeButton("ביטול", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }


    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("הרשאות חסרות")
                .setMessage("האפליקציה לא יכולה לפעול ללא הרשאות מיקום. " +
                        "אנא אפשר את ההרשאות בהגדרות המכשיר.")
                .setPositiveButton("להגדרות", (dialog, which) -> {
                    // פתיחת הגדרות האפליקציה
                    openAppSettings();
                })
                .setNegativeButton("ביטול", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }


    private void showLocationServicesDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("שירותי מיקום כבויים")
                .setMessage("האפליקציה צריכה את שירותי המיקום של המכשיר. " +
                        "אנא הפעל את שירותי המיקום בהגדרות המכשיר.")
                .setPositiveButton("להגדרות מיקום", (dialog, which) -> {
                    // פתיחת הגדרות המיקום
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(intent);
                })
                .setNegativeButton("ביטול", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }


    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }


    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        return locationManager != null &&
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }


    public boolean hasBasicLocationPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(activity, BACKGROUND_LOCATION_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED;
        }
        // בגרסאות ישנות יותר, הרשאת רקע כלולה בהרשאה הרגילה
        return hasBasicLocationPermissions();
    }
}
