package com.mercdev.tangotest;

import android.graphics.Point;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Fixture implements Cloneable {

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

    public void setPosition(Point position) {
        this.position = position;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setRotationAngle(double rotationAngle) {
        this.rotationAngle = rotationAngle;
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

    @Override
    public Object clone() {
        Object result;
        try {
            result = super.clone();
        } catch (CloneNotSupportedException e) {
            result = new Fixture(name, position, height, width, depth, rotationAngle, color);
        }
        return result;
    }
}
