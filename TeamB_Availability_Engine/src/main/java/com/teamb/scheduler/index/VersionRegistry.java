package com.teamb.scheduler.index;

public class VersionRegistry {
    private int calendarVersion = 41;

    public int currentVersion() { return calendarVersion; }

    public void increment() { calendarVersion++; }
}
