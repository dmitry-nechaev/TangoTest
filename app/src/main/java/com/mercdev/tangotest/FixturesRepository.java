package com.mercdev.tangotest;

import android.text.TextUtils;

import java.util.ArrayList;

/**
 * Created by nechaev on 21.02.2017.
 */

public class FixturesRepository {

    private static FixturesRepository instance;
    private ArrayList<Fixture> fixtures = new ArrayList<>();

    public static FixturesRepository getInstance() {
        if (instance == null) {
            instance = new FixturesRepository();
        }
        return instance;
    }

    public ArrayList<Fixture> getFixtures() {
        return fixtures;
    }

    public void setFixtures(ArrayList<Fixture> fixtures) {
        this.fixtures = fixtures;
    }

    public void addFixtures(ArrayList<Fixture> newFixtures) {
        fixtures.addAll(newFixtures);
    }

    public Fixture getFixture(int index) {
        return index < fixtures.size() ? fixtures.get(index) : null;
    }

    public Fixture getFixture(String name) {
        Fixture result = null;
        if (!TextUtils.isEmpty(name)) {
            for (Fixture fixture : fixtures) {
                if (name.equals(fixture.getName())){
                    result = fixture;
                    break;
                }
            }
        }
        return result;
    }

    public boolean removeFixture(int index) {
        Fixture removedFixture = index < fixtures.size() ? fixtures.remove(index) : null;
        return removedFixture != null;
    }

    public void clear() {
        fixtures.clear();
    }
}

