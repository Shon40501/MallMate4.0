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

/**
 * Main maps activity of the application that handles indoor navigation in shopping centers.
 * The class manages the map, points of interest, and navigation routes between different points.
 *
 * <p>The activity includes the following features:</p>
 * <ul>
 * <li>Display Google Map with points of interest</li>
 * <li>Select navigation destinations from a list of saved points</li>
 * <li>Calculate and display indoor navigation routes</li>
 * <li>Identify user location and display it on the map</li>
 * <li>Connect to Firebase database for loading data</li>
 * </ul>
 *
 * @author Shon Aronov
 * @version 1.0
 * @since API level 21
 */
public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    /** Tag for logging debug messages */
    private static final String TAG = "MapsActivity";

    /** Google Maps object */
    private GoogleMap mMap;

    /** Button for navigation to destination */
    private Button navigateToButton;

    /** Button for switching to learning screen */
    private Button goToLearningButton;

    /** Point of interest manager */
    private PointOfInterestManager poiManager;

    /**
     * Called when the activity is created. Initializes the map, buttons, and connects to the database.
     *
     * @param savedInstanceState previously saved state of the activity (if exists)
     */
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

        // Check Firebase database connection
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

    /**
     * Called when the map is ready for use. Sets up basic map settings.
     *
     * @param googleMap the map object ready for use
     */
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

    /**
     * Shows a dialog for selecting a saved point of interest for navigation.
     * The dialog loads the list of points from the database and allows selection.
     */
    private void showSavedPointsDialog() {
        Toast.makeText(this, "Loading points of interest...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Starting to load points");

        poiManager.loadSavedLocations(new PointOfInterestManager.LocationsLoadCallback() {
            @Override
            public void onLocationsLoaded(List<Map<String, Object>> locations) {
                Log.d(TAG, "Successfully loaded " + locations.size() + " points");
                if (locations.isEmpty()) {
                    Toast.makeText(MapsActivity.this,
                            "No saved points of interest found",
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
                        .setTitle("Select Navigation Destination")
                        .setItems(pointNames, (dialog, which) -> {
                            String selectedPointName = pointNames[which];
                            showSelectedPointOnMap(locations.get(which));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading points: " + error);
                Toast.makeText(MapsActivity.this,
                        "Error loading points: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Shows the selected point on the map and calculates a navigation route to it.
     *
     * @param pointData data of the selected point including location and name
     */
    private void showSelectedPointOnMap(Map<String, Object> pointData) {
        if (mMap == null) return;

        // Get point data
        Point selectedPoint = (Point) pointData.get("location");
        String pointName = (String) pointData.get("name");

        if (selectedPoint == null || pointName == null) {
            Toast.makeText(this, "Error loading point data", Toast.LENGTH_SHORT).show();
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
                            location.getAltitude()
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

        Toast.makeText(this, "Selected: " + pointName, Toast.LENGTH_SHORT).show();
    }

    /**
     * Displays a route between two points with fallback in case of failure.
     * If no route is found, tries again with rounded location.
     *
     * @param userLocation current user location
     * @param selectedPoint the selected destination point
     */
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

    /**
     * Searches for and displays a route between two points on the map.
     *
     * @param startPoint starting point
     * @param endPoint destination point
     * @param callback callback for receiving search results
     */
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

    /**
     * Extracts route points in numerical order from the database.
     *
     * @param pathSnapshot snapshot of a route from the database
     * @return ordered list of route points
     */
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

    /**
     * Finds the indices of the closest points in the route to the given points.
     *
     * @param pathPoints route points
     * @param point1 first point to search for
     * @param point2 second point to search for
     * @return array of two indices or null if no close points found
     */
    private int[] findPointIndices(List<Point> pathPoints, Point point1, Point point2) {
        int index1 = findClosestPointIndex(pathPoints, point1, 5.0); // 5 meters
        int index2 = findClosestPointIndex(pathPoints, point2, 5.0); // 5 meters
        return (index1 != -1 && index2 != -1) ? new int[]{index1, index2} : null;
    }

    /**
     * Finds the index of the closest point in the route within a given radius.
     *
     * @param pathPoints list of route points
     * @param target target point
     * @param thresholdMeters search radius in meters
     * @return index of the closest point or -1 if not found within radius
     */
    private int findClosestPointIndex(List<Point> pathPoints, Point target, double thresholdMeters) {
        int closestIndex = -1;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < pathPoints.size(); i++) {
            Point p = pathPoints.get(i);
            double dist = haversineDistanceMeters(p.x, p.y, target.x, target.y);
            if (dist < minDistance) {
                minDistance = dist;
                closestIndex = i;
            }
        }
        // Only accept if within threshold
        if (minDistance > thresholdMeters) return -1;
        return closestIndex;
    }

    /**
     * Calculates distance between two geographic points using the Haversine formula.
     *
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in meters between the two points
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

    /**
     * Displays on the map the route segment between two indices.
     * Adds markers for each point in the route and adjusts the map view.
     *
     * @param pathPoints list of route points
     * @param startIdx starting point index
     * @param endIdx ending point index
     */
    private void drawPathBetweenIndices(List<Point> pathPoints, int startIdx, int endIdx) {
        int start = Math.min(startIdx, endIdx);
        int end = Math.max(startIdx, endIdx);

        mMap.clear();

        // Add a marker for each point in the path segment
        for (int i = start; i <= end; i++) {
            Point p = pathPoints.get(i);
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(p.x, p.y))
                    .title("Path Point " + (i + 1))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        // Optionally, zoom to fit all points
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (int i = start; i <= end; i++) {
            Point p = pathPoints.get(i);
            builder.include(new LatLng(p.x, p.y));
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
    }

    /**
     * Adds markers for starting and destination locations on the map.
     *
     * @param startPoint starting point
     * @param endPoint destination point
     */
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

    /**
     * Callback interface for receiving route search results.
     */
    private interface PathCallback {
        /**
         * Called when a route is found between the points.
         */
        void onPathFound();

        /**
         * Called when no route is found between the points.
         */
        void onPathNotFound();
    }
}