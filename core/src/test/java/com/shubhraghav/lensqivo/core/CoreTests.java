package com.shubhraghav.lensqivo.core;

public final class CoreTests {
    private static int assertions;

    public static void main(String[] args) {
        captureTransactions();
        stopBeforeStart();
        staleCallbacks();
        colorTransforms();
        System.out.println("PASS: " + assertions + " core assertions");
    }

    private static void captureTransactions() {
        CaptureGate gate = new CaptureGate();
        check(gate.isIdle(), "initial idle");
        long photo = gate.beginPhoto();
        check(photo > 0 && !gate.isIdle(), "photo reserves session");
        check(gate.beginPhoto() == 0 && gate.beginVideo() == 0, "double tap blocked");
        check(!gate.requestStop(), "video stop does not finish photo");
        check(gate.complete(photo), "photo completes");
        long video = gate.beginVideo();
        check(video > photo, "unique transaction");
        check(gate.started(video), "recorder Start accepted");
        check(gate.getPhase() == CaptureGate.Phase.RECORDING, "recording phase");
        check(gate.requestStop(), "first stop accepted");
        check(!gate.requestStop(), "second stop ignored");
        check(gate.beginPhoto() == 0 && gate.beginVideo() == 0, "finalization stays locked");
        check(gate.complete(video) && gate.isIdle(), "Finalize unlocks");
    }

    private static void stopBeforeStart() {
        CaptureGate gate = new CaptureGate();
        long id = gate.beginVideo();
        check(gate.requestStop(), "background while starting stops");
        check(!gate.started(id), "late Start cannot resurrect stopped recorder");
        check(gate.getPhase() == CaptureGate.Phase.FINALIZING, "retains finalizing phase");
        check(gate.complete(id), "failed or successful startup still finalizes");
    }

    private static void staleCallbacks() {
        CaptureGate gate = new CaptureGate();
        check(!gate.complete(0), "zero id rejected");
        long old = gate.beginVideo();
        gate.complete(old);
        long current = gate.beginPhoto();
        check(!gate.complete(old), "stale Finalize cannot unlock new photo");
        check(!gate.started(current), "Start cannot turn photo into video");
        check(!gate.isIdle(), "new transaction preserved");
        check(gate.complete(current), "current photo completes");
        check(!gate.complete(current), "duplicate completion rejected");
    }

    private static void colorTransforms() {
        for (Look look : Look.values()) {
            float[] identity = look.matrix(0);
            for (int i = 0; i < 16; i++) near(identity[i], i % 5 == 0 ? 1 : 0, "zero intensity");
            float[] full = look.matrix(1);
            float[] half = look.matrix(.5f);
            for (int i = 0; i < 16; i++) {
                check(Float.isFinite(full[i]), "finite matrix");
                near(half[i], (identity[i] + full[i]) / 2, "linear strength blend");
                near(look.matrix(-1)[i], identity[i], "lower clamp");
                near(look.matrix(2)[i], full[i], "upper clamp");
            }
            float[] poisoned = look.matrix(Float.NaN);
            for (int i = 0; i < 16; i++) near(poisoned[i], identity[i], "NaN is safe neutral");
            full[0] = 99;
            check(look.matrix(1)[0] != 99, "returned matrices cannot poison preset");
        }
        float[] silver = Look.SILVER.matrix(1);
        float[] rgb = { .7f, .4f, .15f };
        float r = transform(silver, rgb, 0);
        near(r, transform(silver, rgb, 1), "mono equal green");
        near(r, transform(silver, rgb, 2), "mono equal blue");
        for (float strength : new float[]{0, .3f, 1}) {
            float[] matrix = Look.ORIGINAL.matrix(strength);
            for (int channel = 0; channel < 3; channel++) near(transform(matrix, rgb, channel), rgb[channel], "original preserves color");
        }
    }

    private static float transform(float[] matrix, float[] rgb, int row) {
        return matrix[row] * rgb[0] + matrix[row + 4] * rgb[1] + matrix[row + 8] * rgb[2] + matrix[row + 12];
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < .00001f, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
