package com.shubhraghav.lensqivo.core;

/** Original restrained transforms for Media3's linear BT.709 RGB working space. */
public enum Look {
    ORIGINAL("Original", "True to the light", 1f, 1f, 1f, 1f, 0f),
    SOL("Sol", "Warm light, soft color", .91f, 1.05f, 1f, .91f, .008f),
    DUSK("Dusk", "Cool air, quiet color", .78f, .94f, 1.01f, 1.06f, .009f),
    SILVER("Silver", "Light and shadow", 0f, 1f, 1f, 1f, .006f);

    private final String label;
    private final String description;
    private final float[] full;

    Look(String label, String description, float saturation,
            float redGain, float greenGain, float blueGain, float lift) {
        this.label = label;
        this.description = description;
        float[] luma = { .2126f, .7152f, .0722f };
        float[] gains = { redGain, greenGain, blueGain };
        full = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                full[row + column * 4] = gains[row]
                        * ((1 - saturation) * luma[column] + (row == column ? saturation : 0));
            }
            full[row + 12] = lift;
        }
        full[15] = 1;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    public float[] matrix(float strength) {
        float amount = Float.isFinite(strength) ? Math.max(0, Math.min(1, strength)) : 0;
        float[] result = new float[16];
        for (int i = 0; i < result.length; i++) {
            float neutral = i % 5 == 0 ? 1 : 0;
            result[i] = neutral + (full[i] - neutral) * amount;
        }
        return result;
    }
}
