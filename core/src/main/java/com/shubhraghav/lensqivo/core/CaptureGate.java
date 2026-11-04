package com.shubhraghav.lensqivo.core;

/** Main-thread-owned capture transaction. A stop request retains ownership until completion. */
public final class CaptureGate {
    public enum Phase { IDLE, PHOTO, STARTING, RECORDING, FINALIZING }

    private Phase phase = Phase.IDLE;
    private long sequence;
    private long activeId;

    public Phase getPhase() { return phase; }
    public boolean isIdle() { return phase == Phase.IDLE; }

    public long beginPhoto() { return begin(Phase.PHOTO); }
    public long beginVideo() { return begin(Phase.STARTING); }

    private long begin(Phase next) {
        if (!isIdle()) return 0;
        activeId = ++sequence;
        phase = next;
        return activeId;
    }

    public boolean started(long id) {
        if (id != activeId || phase != Phase.STARTING) return false;
        phase = Phase.RECORDING;
        return true;
    }

    public boolean requestStop() {
        if (phase != Phase.STARTING && phase != Phase.RECORDING) return false;
        phase = Phase.FINALIZING;
        return true;
    }

    public boolean complete(long id) {
        if (id == 0 || id != activeId || isIdle()) return false;
        phase = Phase.IDLE;
        activeId = 0;
        return true;
    }
}
