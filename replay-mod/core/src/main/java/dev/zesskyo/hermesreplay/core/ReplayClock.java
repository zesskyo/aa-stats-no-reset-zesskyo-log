package dev.zesskyo.hermesreplay.core;

/** Play position, speed and pause state of a replay. Times are play time in ms. */
public final class ReplayClock {
    public static final double MIN_SPEED = 0.25, MAX_SPEED = 64;

    private final long duration;
    private long position;
    private double speed = 1;
    private double carry;
    private boolean paused;

    public ReplayClock(long duration) {
        this.duration = duration;
    }

    /** Moves the clock forward by {@code realMs} of real time at the current speed. */
    public void advance(long realMs) {
        if (paused) return;
        carry += realMs * speed;
        long whole = (long) carry;
        carry -= whole;
        seek(position + whole);
        if (position >= duration) paused = true;
    }

    public void seek(long play) {
        position = Math.max(0, Math.min(duration, play));
    }

    public long position() {
        return position;
    }

    public long duration() {
        return duration;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused && position >= duration) position = 0;
    }
}
