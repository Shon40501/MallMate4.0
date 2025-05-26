package com.example.mallmate40;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Location tracking service that runs in the foreground to continuously monitor user location.
 * Collects location points and saves them as paths when tracking is stopped.
 * Uses Google's Fused Location Provider for accurate location tracking.
 *
 * @author Shon Aronov
 * @version 1.0
 * @since 1.0
 */
public class LocationService extends Service {

    /** Tag used for logging purposes */
    private static final String TAG = "LocationService";

    /** Notification ID for the foreground service notification */
    private static final int NOTIFICATION_ID = 12345;

    /** Channel ID for location tracking notifications */
    private static final String CHANNEL_ID = "location_tracking_channel";

    /** Google's fused location provider client for location updates */
    private FusedLocationProviderClient fusedLocationClient;

    /** Callback that handles location update results */
    private LocationCallback locationCallback;

    /** Configuration for location update requests */
    private LocationRequest locationRequest;

    /** Binder for client connections to this service */
    private final IBinder binder = new LocalBinder();

    /** Current path being tracked */
    private Path currentPath;

    /** Flag indicating whether location tracking is currently active */
    private boolean isTracking = false;

    /**
     * Binder class for connecting clients to the service.
     * Allows activities to get a reference to the service instance.
     */
    public class LocalBinder extends Binder {
        /**
         * Returns the LocationService instance.
         *
         * @return the service instance
         */
        public LocationService getService() {
            return LocationService.this;
        }
    }

    /**
     * Initializes the service - creates a new Path object, sets up location service,
     * and creates notification channel for Android 8.0+.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationService created");

        // Create new Path object for storing route points
        currentPath = new Path();

        // Initialize location service
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up location request
        locationRequest = new LocationRequest.Builder(1000) // Update every second
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(500)
                .build();

        // Create notification channel for Android 8.0+
        createNotificationChannel();

        // Set up behavior for receiving new location
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || !isTracking) {
                    return;
                }

                // Get the last location
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // Save the new location
                    currentPath.addPoint(
                            location.getLatitude(),
                            location.getLongitude(),
                            location.getAltitude()
                    );

                    Log.d(TAG, "New location: " + location.getLatitude() + ", " +
                            location.getLongitude() + ", " + location.getAltitude());
                }
            }
        };
    }

    /**
     * Starts the service as a foreground service with notification.
     *
     * @param intent the intent that started the service
     * @param flags additional data about the start request
     * @param startId unique integer representing this specific request to start
     * @return how the system should continue the service if killed
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        // Start service as foreground service
        startForeground(NOTIFICATION_ID, createNotification());

        return START_STICKY;
    }

    /**
     * Returns the communication channel to the service.
     *
     * @param intent the intent that was used to bind to this service
     * @return an IBinder through which clients can call on to the service
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Cleans up the service when destroyed - stops location updates.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        Log.d(TAG, "LocationService destroyed");
    }

    /**
     * Starts location tracking - checks permissions, resets current path,
     * and begins receiving location updates.
     *
     * @return true if tracking started successfully, false if no permissions or already active
     */
    public boolean startTracking() {
        if (isTracking) {
            return true; // Already tracking
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted");
            return false;
        }

        // Reset current path
        currentPath = new Path();

        // Start location tracking
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );

        isTracking = true;
        Log.d(TAG, "Location tracking started");

        // Update notification
        updateNotification("Location tracking active");

        return true;
    }

    /**
     * Stops location tracking - stops location updates and saves current path to database.
     */
    public void stopTracking() {
        if (!isTracking) {
            return;
        }

        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback);

        // Save path to database
        if (currentPath != null && !currentPath.getPoints().isEmpty()) {
            currentPath.updateDatabase();
            Log.d(TAG, "Path saved to database with " + currentPath.getPoints().size() + " points");
        }

        isTracking = false;
        Log.d(TAG, "Location tracking stopped");

        // Update notification
        updateNotification("Location tracking stopped");
    }

    /**
     * Creates notification channel for Android 8.0 and above.
     * Required for foreground services on newer Android versions.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW);

            channel.setDescription("Channel for location tracking notifications");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Creates notification for the foreground service.
     *
     * @return Notification object for the foreground service
     */
    private Notification createNotification() {
        // Ensure we have a valid notification channel
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MallMate 4.0")
                .setContentText("Location tracking active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)  // Using built-in icon
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // Important: prevents user from dismissing notification
                .build();
    }

    /**
     * Updates the notification content.
     *
     * @param contentText the new text for the notification
     */
    private void updateNotification(String contentText) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mallmate 4.0")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Stops location updates and sets tracking flag to false.
     */
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
    }

    /**
     * Returns whether location tracking is currently active.
     *
     * @return true if currently tracking location, false otherwise
     */
    public boolean isTracking() {
        return isTracking;
    }

    /**
     * Returns the current path being tracked.
     *
     * @return the current Path object containing collected location points
     */
    public Path getCurrentPath() {
        return currentPath;
    }
}
