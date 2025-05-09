package com.example.mallmate40;

public class Point {
    public double x;
    public double y;
    public double z;

    // Required empty constructor for Firebase
    public Point() {}

    // Constructor
    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    //  getters and setters 
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

}
