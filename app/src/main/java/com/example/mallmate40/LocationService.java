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

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "location_tracking_channel";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private final IBinder binder = new LocalBinder();
    private Path currentPath;
    private boolean isTracking = false;

    // Binder class for client binding
    public class LocalBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationService created");

        // יצירת אובייקט Path חדש לשמירת נקודות המסלול
        currentPath = new Path();

        // אתחול שירות המיקום
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // הגדרת בקשת מיקום
        locationRequest = new LocationRequest.Builder(1000) // עדכון כל שנייה
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(500)
                .build();

        // יצירת ערוץ התראות עבור Android 8.0+
        createNotificationChannel();

        // הגדרת התנהגות בקבלת מיקום חדש
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || !isTracking) {
                    return;
                }

                // קבלת המיקום האחרון
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // שמירת המיקום החדש
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        // הפעלת השירות כשירות foreground
        startForeground(NOTIFICATION_ID, createNotification());

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        Log.d(TAG, "LocationService destroyed");
    }


    public boolean startTracking() {
        if (isTracking) {
            return true; // כבר מתבצע מעקב
        }

        // בדיקת הרשאות
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted");
            return false;
        }

        // איפוס המסלול הנוכחי
        currentPath = new Path();

        // התחלת מעקב מיקום
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );

        isTracking = true;
        Log.d(TAG, "Location tracking started");

        // עדכון ההתראה
        updateNotification("מעקב מיקום פעיל");

        return true;
    }


    public void stopTracking() {
        if (!isTracking) {
            return;
        }

        // עצירת עדכוני מיקום
        fusedLocationClient.removeLocationUpdates(locationCallback);

        // שמירת המסלול במסד הנתונים
        if (currentPath != null && !currentPath.getPoints().isEmpty()) {
            currentPath.updateDatabase();
            Log.d(TAG, "Path saved to database with " + currentPath.getPoints().size() + " points");
        }

        isTracking = false;
        Log.d(TAG, "Location tracking stopped");

        // עדכון ההתראה
        updateNotification("מעקב מיקום הופסק");
    }

    public boolean isTracking() {
        return isTracking;
    }


    public Path getCurrentPath() {
        return currentPath;
    }


    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "מעקב מיקום",
                    NotificationManager.IMPORTANCE_LOW);

            channel.setDescription("ערוץ להתראות מעקב מיקום");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    private Notification createNotification() {
        // וודא שיש לך ערוץ התראות תקין
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MallMate 4.0")
                .setContentText("מעקב מיקום פעיל")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)  // שימוש באייקון מובנה
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // חשוב: מונע מהמשתמש למחוק את ההתראה
                .build();
    }


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
}
