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
 * Location Permission Manager that handles all logic related to requesting, checking, and managing
 * location permissions for the application. The class provides user dialogs and callbacks for permission results.
 *
 * <p>The class supports Android 10 and above including background location permissions,
 * and handles all different cases of permission denial and repeated requests.</p>
 *
 * @author Shon Aronov
 * @version 1.0
 * @since API level 21
 */
public class LocationPermissionManager {
    /** Tag used for logging purposes */
    private static final String TAG = "LocationPermissionMgr";

    /** Permission request code (not used in modern version) */
    private static final int REQUEST_LOCATION_PERMISSIONS = 100;

    /** Array of basic permissions required for location */
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /** Background location permission (required only on Android 10 and above) */
    private static final String BACKGROUND_LOCATION_PERMISSION =
            Manifest.permission.ACCESS_BACKGROUND_LOCATION;

    /**
     * Interface for receiving callbacks about permission request results.
     * This interface must be implemented to receive updates about permission status.
     */
    public interface PermissionCallback {
        /**
         * Called when all required permissions have been granted by the user.
         */
        void onPermissionsGranted();

        /**
         * Called when one or more permissions have been denied by the user.
         */
        void onPermissionsDenied();
    }

    /** The activity requesting the permissions */
    private final AppCompatActivity activity;

    /** The callback for permission results */
    private PermissionCallback callback;

    /** Launcher for multiple permission requests (modern approach) */
    private ActivityResultLauncher<String[]> permissionLauncher;

    /** Launcher for background location permission request */
    private ActivityResultLauncher<String> backgroundPermissionLauncher;

    /**
     * Constructor for the location permission manager.
     *
     * @param activity the activity requesting permissions (must be AppCompatActivity)
     * @param callback the callback that will receive updates about permission results
     * @throws IllegalArgumentException if any of the parameters is null
     */
    public LocationPermissionManager(AppCompatActivity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;

        // Register listeners for permission request results using modern approach
        setupPermissionLaunchers();
    }

    /**
     * Sets up the launchers for permission requests using the modern approach (ActivityResultLauncher).
     * This method replaces the old onRequestPermissionsResult approach.
     */
    private void setupPermissionLaunchers() {
        // Listener for basic permissions
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;

                    // Check that all permissions were granted
                    for (String permission : REQUIRED_PERMISSIONS) {
                        if (result.get(permission) == null || !result.get(permission)) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        // If we got all basic permissions, request background permissions if needed
                        if (needsBackgroundPermission()) {
                            requestBackgroundLocationPermission();
                        } else {
                            callback.onPermissionsGranted();
                        }
                    } else {
                        handlePermissionDenial();
                    }
                });

        // Listener for background location permission
        backgroundPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    // Even if background permission was not granted, we continue normally
                    // because the app can function without it
                    callback.onPermissionsGranted();
                });
    }

    /**
     * Checks if background location permission is needed.
     *
     * @return true if the device runs on Android 10 (API 29) or above
     */
    private boolean needsBackgroundPermission() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
    }

    /**
     * Starts the location permission request process.
     * The method first checks the status of location services and existing permissions,
     * then requests the required permissions as needed.
     */
    public void requestLocationPermissions() {
        // Check if location services are enabled on the device
        if (!isLocationEnabled()) {
            showLocationServicesDialog();
            return;
        }

        // Check if we already have all basic permissions
        if (hasBasicLocationPermissions()) {
            // If we have basic permissions, check if we need background permission
            if (needsBackgroundPermission() && !hasBackgroundLocationPermission()) {
                requestBackgroundLocationPermission();
            } else {
                // We have all required permissions
                callback.onPermissionsGranted();
            }
        } else {
            // Request basic permissions
            permissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    /**
     * Handles the case of permission denial by the user.
     * The method checks whether to show rationale to the user or direct them to device settings.
     */
    private void handlePermissionDenial() {
        boolean shouldShowRationale = false;

        // Check if we need to show rationale
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            // Show explanation for why permissions are needed
            showPermissionRationaleDialog();
        } else {
            // User refused and checked "Don't ask again"
            showPermissionDeniedDialog();
        }
    }

    /**
     * Requests background location permission (only on Android 10 and above).
     * The method shows an explanation to the user before requesting the permission.
     */
    private void requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, BACKGROUND_LOCATION_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {

                // Show explanation before requesting background permission
                new AlertDialog.Builder(activity)
                        .setTitle("Background Location Permission")
                        .setMessage("To track your location even when the app is in the background, " +
                                "extended location permission is required. Allow?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Request background permission
                            backgroundPermissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION);
                        })
                        .setNegativeButton("No", (dialog, which) -> {
                            // Even without background permission the app can still be used
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

    /**
     * Shows a rationale dialog to the user explaining why location permissions are required.
     * Called when the user denied permissions but we can still ask again.
     */
    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Location Permissions Required")
                .setMessage("To track your location within the building, " +
                        "the app needs access to location permissions. Please grant the permissions.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    // Request permissions again
                    permissionLauncher.launch(REQUIRED_PERMISSIONS);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }

    /**
     * Shows a dialog to the user when permissions have been permanently denied.
     * Offers the user to go to app settings for manual approval.
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Missing Permissions")
                .setMessage("The app cannot function without location permissions. " +
                        "Please enable the permissions in device settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    // Open app settings
                    openAppSettings();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }

    /**
     * Shows a dialog to the user when location services are disabled on the device.
     * Offers the user to go to location settings to enable them.
     */
    private void showLocationServicesDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Location Services Disabled")
                .setMessage("The app needs the device's location services. " +
                        "Please enable location services in device settings.")
                .setPositiveButton("Go to Location Settings", (dialog, which) -> {
                    // Open location settings
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    callback.onPermissionsDenied();
                })
                .create()
                .show();
    }

    /**
     * Opens the app settings on the device.
     * The method allows the user to manually grant permissions.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Checks if location services are enabled on the device.
     *
     * @return true if at least one location service (GPS or Network) is enabled
     */
    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        return locationManager != null &&
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    /**
     * Checks if basic location permissions (Fine and Coarse) are granted.
     *
     * @return true if both basic permissions are granted, false otherwise
     */
    public boolean hasBasicLocationPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if background location permission is granted.
     * On Android versions below 10, background permission is included in basic permissions.
     *
     * @return true if background location permission is granted or the device doesn't require it
     */
    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(activity, BACKGROUND_LOCATION_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED;
        }
        // On older versions, background permission is included in regular permission
        return hasBasicLocationPermissions();
    }
}
