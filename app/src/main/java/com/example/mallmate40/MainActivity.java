package com.example.mallmate40;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements LocationPermissionManager.PermissionCallback {

    private String TAG = "MainActivity";

    // ממשק משתמש
    private Button startTrackingButton;
    private Button stopTrackingButton;
    private TextView statusTextView;
    private TextView pointsCountTextView;


    // שירותים ומנהלים
    private LocationPermissionManager permissionManager;
    private LocationService locationService;
    private boolean isLocationServiceBound = false;

    // קישור לשירות
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locationService = binder.getService();
            isLocationServiceBound = true;

            Log.d(TAG, "Location service connected");
            updateUIState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            locationService = null;
            isLocationServiceBound = false;
            Log.d(TAG, "Location service disconnected");
        }
    };

    private final Runnable pointCountUpdater = new Runnable() {
        @Override
        public void run() {
            updatePointsCount();

            // עדכון כל שנייה כל עוד השירות פעיל ומחובר
            if (isLocationServiceBound && locationService != null && locationService.isTracking()) {
                statusTextView.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // אתחול רכיבי ממשק משתמש
        startTrackingButton = findViewById(R.id.startTrackingButton);
        stopTrackingButton = findViewById(R.id.stopTrackingButton);
        statusTextView = findViewById(R.id.statusTextView);
        pointsCountTextView = findViewById(R.id.pointsCountTextView);
        Button showMapButton = findViewById(R.id.showMapButton);

        showMapButton.setOnClickListener(v -> {
            Intent mapIntent = new Intent(MainActivity.this, MapsActivity.class);

            // אם יש ברשותך מסלול שאתה רוצה להעביר
            // mapIntent.putExtra("current_path", currentPath); // צריך לממש Parcelable

            startActivity(mapIntent);
        });


        // אתחול מנהל ההרשאות
        permissionManager = new LocationPermissionManager(this, this);

        // הגדרת אירועים לכפתורים
        startTrackingButton.setOnClickListener(v -> {
            if (permissionManager.hasBasicLocationPermissions()) {
                startTracking();
            } else {
                permissionManager.requestLocationPermissions();
            }
        });

        stopTrackingButton.setOnClickListener(v -> stopTracking());

        // בקשת הרשאות באתחול
        permissionManager.requestLocationPermissions();

        // התחלת שירות המיקום
        startAndBindLocationService();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // חיבור מחדש לשירות אם לא מחובר
        if (!isLocationServiceBound) {
            bindLocationService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // עצירת עדכוני UI
        statusTextView.removeCallbacks(pointCountUpdater);

        // שחרור הקישור לשירות אך השארת השירות פועל ברקע אם עדיין מבצע מעקב
        if (isLocationServiceBound && locationService != null) {
            if (!locationService.isTracking()) {
                unbindService(serviceConnection);
                stopService(new Intent(this, LocationService.class));
            } else {
                unbindService(serviceConnection);
            }
            isLocationServiceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // וידוא ניתוק מהשירות
        if (isLocationServiceBound) {
            unbindService(serviceConnection);
            isLocationServiceBound = false;
        }
    }

    // מימוש ממשק הרשאות

    @Override
    public void onPermissionsGranted() {
        Log.d(TAG, "Location permissions granted");
        Toast.makeText(this, "הרשאות מיקום התקבלו", Toast.LENGTH_SHORT).show();

        // עדכון ממשק המשתמש
        updateUIState();
    }

    @Override
    public void onPermissionsDenied() {
        Log.d(TAG, "Location permissions denied");
        Toast.makeText(this, "הרשאות מיקום נדחו - פונקציונליות מוגבלת", Toast.LENGTH_LONG).show();

        // עדכון ממשק המשתמש
        updateUIState();
    }

    // מתודות פנימיות


    private void startAndBindLocationService() {
        // התחלת השירות
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);

        // התחברות לשירות
        bindLocationService();
    }


    private void bindLocationService() {
        Intent intent = new Intent(this, LocationService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startTracking() {
        Log.d(TAG, "startTracking: method started");
        if (locationService != null) {
            Log.d(TAG, "startTracking: location service exist");
            Toast.makeText(this, "location service exist", Toast.LENGTH_SHORT).show();
            if (locationService.startTracking()) {
                Log.d(TAG, "Tracking started");
                Toast.makeText(this, "מעקב מיקום החל", Toast.LENGTH_SHORT).show();

                // התחלת עדכוני מספר נקודות
                statusTextView.post(pointCountUpdater);
            } else {
                Toast.makeText(this, "לא ניתן להתחיל מעקב מיקום", Toast.LENGTH_SHORT).show();
            }

            // עדכון ממשק המשתמש
            updateUIState();
        }
    }

    private void stopTracking() {
        if (locationService != null) {
            locationService.stopTracking();
            Log.d(TAG, "Tracking stopped");
            Toast.makeText(this, "מעקב מיקום הופסק ונשמר", Toast.LENGTH_SHORT).show();

            // עצירת עדכוני מספר נקודות
            statusTextView.removeCallbacks(pointCountUpdater);

            // עדכון ממשק המשתמש
            updateUIState();
        }
    }


    private void updatePointsCount() {
        if (isLocationServiceBound && locationService != null && locationService.isTracking()) {
            Path currentPath = locationService.getCurrentPath();
            if (currentPath != null) {
                int pointsCount = currentPath.getPoints().size();
                pointsCountTextView.setText("מספר נקודות שנאספו: " + pointsCount);
            }
        }
    }


    private void updateUIState() {
        boolean hasPermissions = permissionManager.hasBasicLocationPermissions();
        boolean isTracking = (locationService != null && locationService.isTracking());

        // עדכון הכפתורים
        startTrackingButton.setEnabled(hasPermissions && !isTracking);
        stopTrackingButton.setEnabled(hasPermissions && isTracking);

        // עדכון טקסט סטטוס
        if (!hasPermissions) {
            statusTextView.setText("נדרשות הרשאות מיקום");
            pointsCountTextView.setText("");
        } else if (isTracking) {
            statusTextView.setText("מעקב מיקום פעיל");
            updatePointsCount();
        } else {
            statusTextView.setText("מעקב מיקום מושבת");
            pointsCountTextView.setText("");
        }
    }
}
