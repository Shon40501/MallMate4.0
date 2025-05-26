package com.example.mallmate40;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Represents a collection of location points (path) that are gathered during tracking.
 * Provides methods for adding points, saving the path to Firebase, and clearing the path.
 *
 * @author Shon Aronov
 * @version 1.0
 * @since 1.0
 */
public class Path {
    /** List of points that make up the path */
    private ArrayList<Point> points;

    /** Firebase database reference for storing paths */
    private DatabaseReference dbref;

    /**
     * Creates a new path and initializes the Firebase connection.
     * The path starts empty and is ready to collect location points.
     */
    public Path() {
        this.points = new ArrayList<>();
        this.dbref = FirebaseDatabase.getInstance().getReference("paths");
    }

    /**
     * Adds a new point to the path every second during tracking.
     * Each point represents a location at a specific moment in time.
     *
     * @param x the x-coordinate of the location
     * @param y the y-coordinate of the location
     * @param z the z-coordinate of the location (typically altitude)
     */
    public void addPoint(double x, double y, double z) {
        points.add(new Point(x, y, z));
    }

    /**
     * Saves the complete path to Firebase when the stop button is pressed.
     * Creates a unique identifier for the path based on the current date and time.
     * The path is only saved if it contains at least one point.
     */
    public void updateDatabase() {
        if (!points.isEmpty()) {
            // Create current date and time object
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String dateTime = sdf.format(new Date());
            // Use date and time as key
            String pathId = dateTime;
            dbref.child(pathId).setValue(points)
                    .addOnSuccessListener(aVoid -> {
                        System.out.println("Path saved successfully!");
                    })
                    .addOnFailureListener(e -> {
                        System.err.println("Failed to save path: " + e.getMessage());
                    });
        }
    }

    /**
     * Returns the list of points that make up this path.
     *
     * @return an immutable view of the points in this path
     */
    public List<Point> getPoints() {
        return points;
    }
}
