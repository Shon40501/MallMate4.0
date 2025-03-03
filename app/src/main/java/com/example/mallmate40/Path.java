package com.example.mallmate40;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.List;

public class Path {
    private ArrayList<Point> points;
    private DatabaseReference databaseRef;

    // Constructor
    public Path() {
        this.points = new ArrayList<>();
        this.databaseRef = FirebaseDatabase.getInstance().getReference("paths");
    }

    // Add a new point every second while tracking
    public void addPoint(double x, double y, double z) {
        points.add(new Point(x, y, z));
    }

    // Save the entire path to Firebase when stop button is pressed
    public void updateDatabase() {
        if (!points.isEmpty()) {
            String pathId = databaseRef.push().getKey(); // Generate a unique ID for this path
            databaseRef.child(pathId).setValue(points)
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
