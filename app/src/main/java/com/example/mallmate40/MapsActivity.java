package com.example.mallmate40;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.Manifest;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private Button navigateToButton;
    private Button goToLearningButton;
    private PointOfInterestManager poiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Initialize UI components
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        navigateToButton = findViewById(R.id.navigateToButton);
        goToLearningButton = findViewById(R.id.goToLearningButton);

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
        goToLearningButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapsActivity.this, MainActivity.class);
            startActivity(intent);
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
        Point selectedPoint = (Point) pointData.get("location");
        String pointName = (String) pointData.get("name");

        if (selectedPoint == null || pointName == null) {
            Toast.makeText(this, "שגיאה בטעינת נתוני הנקודה", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear previous markers
        mMap.clear();

        // Add destination marker
        LatLng position = new LatLng(selectedPoint.x, selectedPoint.y);
        mMap.addMarker(new MarkerOptions()
                .position(position)
                .title(pointName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Zoom to selected point
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18));

        // Get user's current location and display path
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    Point userLocation = new Point(
                            location.getLatitude(),
                            location.getLongitude(),
                            getCurrentFloor() // You need to implement this
                    );

                    // Add user location marker
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(userLocation.x, userLocation.y))
                            .title("Your Location")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                    // Display path between points with fallback to rounded location
                    displayPathWithFallback(userLocation, selectedPoint);
                } else {
                    Toast.makeText(MapsActivity.this,
                            "Could not get current location",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        Toast.makeText(this, "נבחר: " + pointName, Toast.LENGTH_SHORT).show();
    }

    private void displayPathWithFallback(Point userLocation, Point selectedPoint) {
        displayPathBetweenPoints(userLocation, selectedPoint, new PathCallback() {
            @Override
            public void onPathFound() {
                // Path found, nothing else to do
            }

            @Override
            public void onPathNotFound() {
                // Try with rounded location
                double roundedLat = Math.round(userLocation.x * 1000.0) / 1000.0;
                double roundedLng = Math.round(userLocation.y * 1000.0) / 1000.0;
                Point roundedLocation = new Point(roundedLat, roundedLng, userLocation.z);

                if (roundedLat == userLocation.x && roundedLng == userLocation.y) {
                    // Already tried rounded location, avoid infinite loop
                    Toast.makeText(MapsActivity.this,
                            "No connecting path found between these points",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                displayPathBetweenPoints(roundedLocation, selectedPoint, new PathCallback() {
                    @Override
                    public void onPathFound() {
                        // Path found with rounded location
                    }

                    @Override
                    public void onPathNotFound() {
                        Toast.makeText(MapsActivity.this,
                                "No connecting path found between these points (even with nearby location)",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void displayPathBetweenPoints(Point startPoint, Point endPoint, PathCallback callback) {
        DatabaseReference pathsRef = FirebaseDatabase.getInstance().getReference("paths");
        pathsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean pathFound = false;

                for (DataSnapshot pathSnapshot : dataSnapshot.getChildren()) {
                    List<Point> orderedPoints = extractOrderedPoints(pathSnapshot);
                    int[] indices = findPointIndices(orderedPoints, startPoint, endPoint);

                    if (indices != null) {
                        drawPathBetweenIndices(orderedPoints, indices[0], indices[1]);
                        pathFound = true;
                        break;
                    }
                }

                if (pathFound) {
                    if (callback != null) callback.onPathFound();
                } else {
                    if (callback != null) callback.onPathNotFound();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading paths: " + databaseError.getMessage());
                if (callback != null) callback.onPathNotFound();
            }
        });
    }

    // Extracts points in order (0, 1, 2...) from a path snapshot
    private List<Point> extractOrderedPoints(DataSnapshot pathSnapshot) {
        List<Point> points = new ArrayList<>();
        int index = 0;

        while (true) {
            DataSnapshot pointSnapshot = pathSnapshot.child(String.valueOf(index));
            if (!pointSnapshot.exists()) break;

            Point point = pointSnapshot.getValue(Point.class);
            points.add(point);
            index++;
        }
        return points;
    }

    // Finds if both points exist in the same path
    private int[] findPointIndices(List<Point> pathPoints, Point point1, Point point2) {
        int index1 = -1;
        int index2 = -1;

        for (int i = 0; i < pathPoints.size(); i++) {
            Point p = pathPoints.get(i);
            if (arePointsEqual(p, point1)) index1 = i;
            if (arePointsEqual(p, point2)) index2 = i;
        }

        return (index1 != -1 && index2 != -1) ? new int[]{index1, index2} : null;
    }

    // Compares two points with tolerance
    private boolean arePointsEqual(Point a, Point b) {
        return Math.abs(a.x - b.x) < 0.0001 &&
                Math.abs(a.y - b.y) < 0.0001 &&
                a.z == b.z;
    }

    // Draws the path segment between two indices
    private void drawPathBetweenIndices(List<Point> pathPoints, int startIdx, int endIdx) {
        int start = Math.min(startIdx, endIdx);
        int end = Math.max(startIdx, endIdx);

        PolylineOptions options = new PolylineOptions()
                .width(8)
                .color(Color.BLUE);

        for (int i = start; i <= end; i++) {
            Point p = pathPoints.get(i);
            options.add(new LatLng(p.x, p.y));
        }

        mMap.clear();
        mMap.addPolyline(options);
        addLocationMarkers(pathPoints.get(start), pathPoints.get(end));
    }

    // Adds start/end markers
    private void addLocationMarkers(Point startPoint, Point endPoint) {
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(startPoint.x, startPoint.y))
                .title("Start Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(endPoint.x, endPoint.y))
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(startPoint.x, startPoint.y));
        builder.include(new LatLng(endPoint.x, endPoint.y));
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
    }

    private interface PathCallback {
        void onPathFound();
        void onPathNotFound();
    }

    // Add this helper method (you'll need to implement the actual floor detection)
    private int getCurrentFloor() {
        // TODO: Implement your floor detection logic
        // For now, return 0 as default floor
        return 0;
    }
}