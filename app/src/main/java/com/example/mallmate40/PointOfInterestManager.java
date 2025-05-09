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
 * מחלקה לניהול נקודות עניין במבנה
 */
public class PointOfInterestManager {

    private static final String TAG = "PointOfInterestManager";

    // מסד נתונים לשמירת נקודות עניין
    private DatabaseReference dbref;

    /**
     * ממשק לקבלת תוצאות טעינה מהדאטאבייס
     */
    public interface LocationsLoadCallback {
        void onLocationsLoaded(List<Map<String, Object>> locations);
        void onError(String error);
    }

    /**
     * קונסטרקטור - מאתחל את החיבור לדאטאבייס
     */
    public PointOfInterestManager() {
        // יצירת הפניה לענף "points_of_interest" במסד הנתונים
        this.dbref = FirebaseDatabase.getInstance().getReference("points_of_interest");
        Log.d(TAG, "Initialized with reference: " + dbref.toString());
    }

    /**
     * שמירת נקודת עניין חדשה
     */
    public boolean savePointOfInterest(String name, double x, double y, double z) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // יצירת אובייקט נקודה
        Point location = new Point(x, y, z);

        // שמירה לדאטאבייס
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
     * מחיקת נקודת עניין*/
    public void deletePointOfInterest(String name) {
        if (name != null && !name.trim().isEmpty()) {
            dbref.child(name).removeValue();
        }
    }

    /**
     * טוען את כל נקודות העניין השמורות
     * @param callback ממשק לקבלת התוצאות
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