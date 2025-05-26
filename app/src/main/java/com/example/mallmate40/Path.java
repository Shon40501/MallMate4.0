package com.example.mallmate40;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Path represents a collection of location points (a route) collected during tracking. It provides methods to add points, save the path to Firebase, and clear the path.
 */
public class Path {
    private ArrayList<Point> points;
    private DatabaseReference dbref;

    // Constructor
    public Path() {
        this.points = new ArrayList<>();
        this.dbref = FirebaseDatabase.getInstance().getReference("paths");
    }

    // Add a new point every second while tracking
    public void addPoint(double x, double y, double z) {
        points.add(new Point(x, y, z));
    }

    // Save the entire path to Firebase when stop button is pressed
    public void updateDatabase() {
        if (!points.isEmpty()) {
            // יצירת אובייקט של תאריך ושעה נוכחיים
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String dateTime = sdf.format(new Date());
            // שימוש בתאריך ושעה כמפתח
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

    // Getter
    public List<Point> getPoints() {
        return points;
    }

    // Clear points if needed
    public void clearPath() {
        points.clear();
    }

}
