package com.foukas.dropbox2d.progression;

import com.badlogic.gdx.Preferences;

import java.util.HashMap;
import java.util.Map;

/** In-memory Preferences double so PlayerProgress can be tested without a
 * real libGDX Application context. Not a mocking-framework mock -- this
 * project has no mocking library dependency, and a ~15-line hand-rolled
 * fake is simpler than adding one for a single interface. */
class FakePreferences implements Preferences {
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public Preferences putBoolean(String key, boolean val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putInteger(String key, int val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putLong(String key, long val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putFloat(String key, float val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putString(String key, String val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences put(Map<String, ?> vals) {
        values.putAll(vals);
        return this;
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public int getInteger(String key) {
        return getInteger(key, 0);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    @Override
    public float getFloat(String key) {
        return getFloat(key, 0f);
    }

    @Override
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
    }

    @Override
    public int getInteger(String key, int defValue) {
        return values.containsKey(key) ? (Integer) values.get(key) : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        return values.containsKey(key) ? (Long) values.get(key) : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        return values.containsKey(key) ? (Float) values.get(key) : defValue;
    }

    @Override
    public String getString(String key, String defValue) {
        return values.containsKey(key) ? (String) values.get(key) : defValue;
    }

    @Override
    public Map<String, ?> get() {
        return new HashMap<>(values);
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void flush() {
        // no-op -- already "persisted" in the in-memory map
    }
}
