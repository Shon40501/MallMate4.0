package com.example.mallmate40;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point of Interest Manager - responsible for saving, loading, and deleting points of interest
 * in the Firebase database. Provides methods for managing user-defined locations within the application.
 *
 * @author Shon Aronov
 * @version 1.0
 * @since 1.0
 */
public class PointOfInterestManager {

    /** Tag used for logging purposes */
    private static final String TAG = "PointOfInterestManager";

    /** Database reference for storing points of interest */
    private DatabaseReference dbref;

    /**
     * Interface for receiving loading results from the database.
     * Provides callbacks for successful data loading and error handling.
     */
    public interface LocationsLoadCallback {
        /**
         * Called when locations are successfully loaded from the database.
         *
         * @param locations list of location data containing name and Point objects
         */
        void onLocationsLoaded(List<Map<String, Object>> locations);

        /**
         * Called when an error occurs during the loading process.
         *
         * @param error error message describing what went wrong
         */
        void onError(String error);
    }

    /**
     * Constructor - initializes the Firebase database connection.
     * Creates a reference to the "points_of_interest" branch in the database.
     */
    public PointOfInterestManager() {
        // Create reference to "points_of_interest" branch in the database
        this.dbref = FirebaseDatabase.getInstance().getReference("points_of_interest");
        Log.d(TAG, "Initialized with reference: " + dbref.toString());
    }

    /**
     * Saves a new point of interest to the database.
     * The point is stored using the provided name as the key.
     *
     * @param name the name of the point of interest (cannot be empty or null)
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @param z the z-coordinate of the point
     * @return true if the save operation was initiated successfully, false if the name is invalid
     */
    public boolean savePointOfInterest(String name, double x, double y, double z) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Create point object
        Point location = new Point(x, y, z);

        // Save to database
        dbref.child(name).setValue(location)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Point of interest saved successfully: " + name);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save point of interest: " + e.getMessage());
                });

        return true;
    }

    /**
     * Deletes a point of interest from the database by name.
     * If the name is null or empty, no action is taken.
     *
     * @param name the name of the point of interest to delete
     */
    public void deletePointOfInterest(String name) {
        if (name != null && !name.trim().isEmpty()) {
            dbref.child(name).removeValue();
        }
    }

    /**
     * Loads all saved points of interest from the database.
     * Performs a single read operation on the database and returns results through a callback.
     * Each location in the result contains both the name and the Point object.
     *
     * @param callback interface for receiving results - either a list of points of interest or an error message
     */
    public void loadSavedLocations(final LocationsLoadCallback callback) {
        Log.d(TAG, "Attempting to load points from Firebase");
        dbref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "DataSnapshot received, has children: " + dataSnapshot.hasChildren());

                List<Map<String, Object>> locations = new ArrayList<>();
                try {
                    for (DataSnapshot locationSnapshot : dataSnapshot.getChildren()) {
                        String locationName = locationSnapshot.getKey();
                        Log.d(TAG, "Found point: " + locationName);

                        Point point = locationSnapshot.getValue(Point.class);
                        if (point != null) {
                            Map<String, Object> locationData = new HashMap<>();
                            locationData.put("name", locationName);
                            locationData.put("location", point);
                            locations.add(locationData);
                        } else {
                            Log.e(TAG, "Point is null for: " + locationName);
                        }
                    }
                    Log.d(TAG, "Successfully loaded " + locations.size() + " points");
                    callback.onLocationsLoaded(locations);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing data: " + e.getMessage(), e);
                    callback.onError("Error parsing data: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage(), databaseError.toException());
                callback.onError("Database error: " + databaseError.getMessage());
            }
        });
    }
}