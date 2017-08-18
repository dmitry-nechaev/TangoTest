package com.mercdev.tangotest;

import android.content.res.Resources;
import android.graphics.Point;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Fixture implements Cloneable {

    public enum Type {Wall, Fixture, Couch}

    private Point position;
    private String name;
    private int height, width, depth;
    private int color;
    private double rotationAngle;
    private final Type type;

    public Fixture(String name, Point position, int height, int width, int depth, double rotationAngle, int color, Type type) {
        this.name = name;
        this.position = new Point(position.x, position.y);
        this.height = height;
        this.width = width;
        this.depth = depth;
        this.rotationAngle = rotationAngle;
        this.color = color;
        this.type = type;
    }

    public void setPosition(Point position) {
        this.position = position;
    }

    public void setX(int x) {
        if (position != null) {
            position.x = x;
        }
    }

    public void setY(int y) {
        if (position != null) {
            position.y = y;
        }
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

    public Fixture.Type getType() {
        return type;
    }

    public String toString(Resources resources) {
        String typeString;
        switch (getType()) {
            case Couch:
                typeString = resources.getString(R.string.object_type_couch);
                break;
            case Fixture:
                typeString = resources.getString(R.string.object_type_fixture);
                break;
            case Wall:
                typeString = resources.getString(R.string.object_type_wall);
                break;
            default:
                typeString = "";
        }
        return typeString + " #" + getName();
    }

    @Override
    public Object clone() {
        Object result = new Fixture(name, position, height, width, depth, rotationAngle, color, type);
        return result;
    }
}
