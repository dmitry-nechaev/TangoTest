package com.mercdev.tangotest;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Fixture {

    private Point position;
    private String name;
    private int height, width, depth;
    private int color;
    private double rotationAngle;

    public Fixture(String name, Point position, int height, int width, int depth, double rotateAngle, int color) {
        this.name = name;
        this.position = position;
        this.height = height;
        this.width = width;
        this.depth = depth;
        this.rotationAngle = rotationAngle;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public Point getPosition() {
        return position;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public double getRotationAngle() {
        return rotationAngle;
    }

    public int getColor() {
        return color;
    }
}
