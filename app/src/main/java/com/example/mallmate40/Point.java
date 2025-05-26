package com.example.mallmate40;

/**
 * Represents a point in three-dimensional space with x, y, and z coordinates.
 * This class is designed to be compatible with Firebase serialization.
 *
 * @author Shon Aronov
 * @version 1.0
 * @since 1.0
 */
public class Point {

    /** The x-coordinate of the point */
    public double x;

    /** The y-coordinate of the point */
    public double y;

    /** The z-coordinate of the point */
    public double z;

    /**
     * Default constructor required for Firebase serialization.
     * Creates a point at the origin (0, 0, 0).
     */
    public Point() {}

    /**
     * Creates a point with the given coordinates.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param z the z-coordinate
     */
    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Returns the x-coordinate of the point.
     *
     * @return the x-coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of the point.
     *
     * @param x the new x-coordinate
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Returns the y-coordinate of the point.
     *
     * @return the y-coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of the point.
     *
     * @param y the new y-coordinate
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Returns the z-coordinate of the point.
     *
     * @return the z-coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     * Sets the z-coordinate of the point.
     *
     * @param z the new z-coordinate
     */
    public void setZ(double z) {
        this.z = z;
    }
}
