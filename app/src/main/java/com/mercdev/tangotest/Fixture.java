package com.mercdev.tangotest;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Fixture {

    private Point position;
    private int height, width, depth;
    private int color;

    public Fixture(Point position, int height, int width, int depth, int color) {
        this.position = position;
        this.height = height;
        this.width = width;
        this.depth = depth;
        this.color = color;
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

    public int getColor() {
        return color;
    }
}
