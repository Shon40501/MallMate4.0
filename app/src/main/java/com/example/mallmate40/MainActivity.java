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
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * MainActivity is the main learning screen of the app. It allows the user to start and stop location tracking, save points of interest, and navigate to the map screen. It manages permissions, UI state, and interaction with the LocationService.
 */
public class MainActivity extends AppCompatActivity implements LocationPermissionManager.PermissionCallback {

    private String TAG = "MainActivity";

    // ממשק משתמש
    private Button startTrackingButton;
    private Button stopTrackingButton;
    private TextView statusTextView;
    private TextView pointsCountTextView;
    private PointOfInterestManager poiManager; // של המחלקה POIM
    private Button savePointButton; // של המחלקה POIM


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

    /**
     * Called when the activity is created. Initializes UI, permissions, and services.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        poiManager = new PointOfInterestManager();


        // אתחול רכיבי ממשק משתמש
        startTrackingButton = findViewById(R.id.startTrackingButton);
        stopTrackingButton = findViewById(R.id.stopTrackingButton);
        statusTextView = findViewById(R.id.statusTextView);
        pointsCountTextView = findViewById(R.id.pointsCountTextView);
        Button showMapButton = findViewById(R.id.showMapButton);
        savePointButton = findViewById(R.id.savePointButton);


        showMapButton.setOnClickListener(v -> {
            Intent mapIntent = new Intent(MainActivity.this, MapsActivity.class);
            startActivity(mapIntent);
        });

        savePointButton.setOnClickListener(v -> {
            showSavePointDialog();
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

    /**
     * Called when the activity becomes visible. Re-binds to the location service if needed.
     */
    @Override
    protected void onStart() {
        super.onStart();

        // חיבור מחדש לשירות אם לא מחובר
        if (!isLocationServiceBound) {
            bindLocationService();
        }
    }

    /**
     * Called when the activity is no longer visible. Unbinds or stops the location service as needed.
     */
    @Override
    protected void onStop() {
        super.onStop();

        // אל תנתק את השירות אם הוא עדיין במצב מעקב
        if (isLocationServiceBound) {
            if (locationService != null && locationService.isTracking()) {
                // ניתוק מהשירות אבל השארת השירות פעיל
                unbindService(serviceConnection);
                isLocationServiceBound = false;
            } else {
                // אם לא במצב מעקב, ניתוק וסגירת השירות
                unbindService(serviceConnection);
                stopService(new Intent(this, LocationService.class));
                isLocationServiceBound = false;
            }
        }
    }

    /**
     * Called when the activity is destroyed. Ensures the location service is unbound.
     */
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

    /**
     * Callback for when location permissions are granted. Updates UI.
     */
    @Override
    public void onPermissionsGranted() {
        Log.d(TAG, "Location permissions granted");
        Toast.makeText(this, "הרשאות מיקום התקבלו", Toast.LENGTH_SHORT).show();

        // עדכון ממשק המשתמש
        updateUIState();
    }

    /**
     * Callback for when location permissions are denied. Updates UI.
     */
    @Override
    public void onPermissionsDenied() {
        Log.d(TAG, "Location permissions denied");
        Toast.makeText(this, "הרשאות מיקום נדחו - פונקציונליות מוגבלת", Toast.LENGTH_LONG).show();

        // עדכון ממשק המשתמש
        updateUIState();
    }

    // מתודות פנימיות


    /**
     * Starts and binds the location service.
     */
    private void startAndBindLocationService() {
        // התחלת השירות
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);

        // התחברות לשירות
        bindLocationService();
    }


    /**
     * Binds to the location service.
     */
    private void bindLocationService() {
        Intent intent = new Intent(this, LocationService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Starts location tracking if possible. Updates UI and shows toasts.
     */
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

    /**
     * Stops location tracking, saves the path, and updates UI.
     */
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


    /**
     * Updates the points count TextView with the number of collected points.
     */
    private void updatePointsCount() {
        if (isLocationServiceBound && locationService != null && locationService.isTracking()) {
            Path currentPath = locationService.getCurrentPath();
            if (currentPath != null) {
                int pointsCount = currentPath.getPoints().size();
                pointsCountTextView.setText("מספר נקודות שנאספו: " + pointsCount);
            }
        }
    }


    /**
     * Updates the UI state (buttons and status) based on permissions and tracking state.
     */
    private void updateUIState() {
        boolean hasPermissions = permissionManager.hasBasicLocationPermissions();
        boolean isTracking = (locationService != null && locationService.isTracking());

        // עדכון הכפתורים
        startTrackingButton.setEnabled(hasPermissions && !isTracking);
        stopTrackingButton.setEnabled(hasPermissions && isTracking);

        savePointButton.setEnabled(hasPermissions && isTracking);


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


    // פעולות שקשורות לPOIM
    /**
     * Shows a dialog to save a point of interest. Handles saving logic.
     */
    private void showSavePointDialog() {
        // יצירת תיבת דיאלוג
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("שמירת נקודת עניין");

        // הוספת שדה טקסט להזנת שם
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("הזן שם לנקודה (לדוגמה: כיתה 101)");
        builder.setView(input);

        // כפתור אישור
        builder.setPositiveButton("שמור", (dialog, which) -> {
            // קבלת השם שהוזן
            String pointName = input.getText().toString().trim();

            if (pointName.isEmpty()) {
                Toast.makeText(this, "נא להזין שם לנקודה", Toast.LENGTH_SHORT).show();
                return;
            }

            // קבלת המיקום הנוכחי
            Path currentPath = locationService.getCurrentPath();
            if (currentPath != null && !currentPath.getPoints().isEmpty()) {
                List<Point> points = currentPath.getPoints();
                Point currentPoint = points.get(points.size() - 1);

                // Find the closest path point to the current location (within 5 meters)
                int closestIndex = -1;
                double minDistance = Double.MAX_VALUE;
                for (int i = 0; i < points.size(); i++) {
                    Point p = points.get(i);
                    double dist = haversineDistanceMeters(p.getX(), p.getY(), currentPoint.getX(), currentPoint.getY());
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestIndex = i;
                    }
                }
                Point snapPoint = points.get(closestIndex);

                // שמירת הנקודה בדאטאבייס
                boolean saved = poiManager.savePointOfInterest(
                        pointName,
                        snapPoint.getX(),
                        snapPoint.getY(),
                        snapPoint.getZ()
                );

                if (saved) {
                    Toast.makeText(this, "נקודה נשמרה: " + pointName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "שגיאה בשמירת הנקודה", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // כפתור ביטול
        builder.setNegativeButton("ביטול", (dialog, which) -> dialog.cancel());

        // הצגת הדיאלוג
        builder.show();
    }

    /**
     * Calculates the Haversine distance in meters between two lat/lon points.
     */
    private double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

}

