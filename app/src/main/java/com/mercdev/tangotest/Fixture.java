package com.mercdev.tangotest;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Fixture {

    private Rect rect;
    private float height;
    private int color;

    public Fixture(Rect rect, float height, int color) {
        this.rect = rect;
        this.height = height;
        this.color = color;
    }

    public Rect getRect() {
        return rect;
    }

    public float getHeight() {
        return height;
    }

    public int getColor() {
        return color;
    }
}
