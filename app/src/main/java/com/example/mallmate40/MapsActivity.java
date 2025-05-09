package com.example.mallmate40;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.Manifest;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private Button navigateToButton;
    private Button backToMainButton;
    private PointOfInterestManager poiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Initialize UI components
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        navigateToButton = findViewById(R.id.navigateToButton);
        backToMainButton = findViewById(R.id.backToMainButton);

        // Initialize Point of Interest Manager
        poiManager = new PointOfInterestManager();

        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class);
                Log.d(TAG, "Firebase connected: " + connected);
                Toast.makeText(MapsActivity.this, "Firebase connected: " + connected, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase connection error: " + error.getMessage());
            }
        });

        // Set up button click listeners
        navigateToButton.setOnClickListener(v -> showSavedPointsDialog());
        backToMainButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapsActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable my location if permission is granted
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error: " + e.getMessage());
        }

        // Set default location (Tel Aviv as example)
        LatLng defaultLocation = new LatLng(32.0853, 34.7818);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12));
    }

    private void showSavedPointsDialog() {
        Toast.makeText(this, "טוען נקודות עניין...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Starting to load points");

        poiManager.loadSavedLocations(new PointOfInterestManager.LocationsLoadCallback() {
            @Override
            public void onLocationsLoaded(List<Map<String, Object>> locations) {
                Log.d(TAG, "Successfully loaded " + locations.size() + " points");
                if (locations.isEmpty()) {
                    Toast.makeText(MapsActivity.this,
                            "לא נמצאו נקודות עניין שמורות",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Create array of point names
                String[] pointNames = new String[locations.size()];
                for (int i = 0; i < locations.size(); i++) {
                    pointNames[i] = (String) locations.get(i).get("name");
                    Log.d(TAG, "Point found: " + pointNames[i]);
                }

                // Show selection dialog
                new AlertDialog.Builder(MapsActivity.this)
                        .setTitle("בחר יעד ניווט")
                        .setItems(pointNames, (dialog, which) -> {
                            String selectedPointName = pointNames[which];
                            showSelectedPointOnMap(locations.get(which));
                        })
                        .setNegativeButton("ביטול", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading points: " + error);
                Toast.makeText(MapsActivity.this,
                        "שגיאה בטעינת נקודות: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSelectedPointOnMap(Map<String, Object> pointData) {
        if (mMap == null) return;

        // Get point data
        Point point = (Point) pointData.get("location");
        String pointName = (String) pointData.get("name");

        if (point == null || pointName == null) {
            Toast.makeText(this, "שגיאה בטעינת נתוני הנקודה", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert to LatLng
        LatLng position = new LatLng(point.getX(), point.getY());

        // Clear previous markers and add new one
        mMap.clear();
        mMap.addMarker(new MarkerOptions()
                .position(position)
                .title(pointName));

        // Zoom to selected point
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18));

        Toast.makeText(this, "נבחר: " + pointName, Toast.LENGTH_SHORT).show();
    }
}