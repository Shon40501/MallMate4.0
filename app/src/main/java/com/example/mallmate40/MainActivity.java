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
    private void showSavePointDialog() {
        if (locationService == null || !locationService.isTracking()) {
            Toast.makeText(this, "יש להפעיל מעקב מיקום תחילה", Toast.LENGTH_SHORT).show();
            return;
        }

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
                // קבלת הנקודה האחרונה (המיקום הנוכחי)
                List<Point> points = currentPath.getPoints();
                Point currentPoint = points.get(points.size() - 1);

                // שמירת הנקודה בדאטאבייס
                boolean saved = poiManager.savePointOfInterest(
                        pointName,
                        currentPoint.getX(),
                        currentPoint.getY(),
                        currentPoint.getZ()
                );

                if (saved) {
                    Toast.makeText(this, "נקודה נשמרה: " + pointName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "שגיאה בשמירת הנקודה", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "לא ניתן לקבל את המיקום הנוכחי", Toast.LENGTH_SHORT).show();
            }
        });

        // כפתור ביטול
        builder.setNegativeButton("ביטול", (dialog, which) -> dialog.cancel());

        // הצגת הדיאלוג
        builder.show();
    }


}

