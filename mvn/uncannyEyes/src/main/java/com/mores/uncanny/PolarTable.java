package com.mores.uncanny;

/**
 * Precomputes the polar-coordinate lookup table used by {@link IrisRenderer}.
 * <p>
 * For every pixel (x, y) within the square bounding box the table stores a packed int:
 * <ul>
 * <li>bits 6:0 – distEncoded: 0 = on/outside the iris circle edge, 127 = centre. Pixels outside the inscribed circle
 * all get 0 so that {@link IrisRenderer} renders them as sclera, producing a true circular iris instead of a square
 * one.
 * <li>bits 15:7 – angleEncoded: 0-511 covering the full 360° circle.
 * </ul>
 * <p>
 * Critical: distance is normalised by the CIRCLE RADIUS (half the shorter dimension), <em>not</em> the diagonal of the
 * bounding box. Normalising by the diagonal would give circle-edge pixels a distEncoded of ~37 instead of 0, causing
 * the iris to render as a filled square rather than a circle.
 */
public final class PolarTable {

    private PolarTable() {
    }

    /**
     * Build and return the polar table for a square iris region.
     *
     * @param irisWidth
     *            iris circle bounding-box width (use IRIS_CIRCLE_SIZE)
     * @param irisHeight
     *            iris circle bounding-box height (use IRIS_CIRCLE_SIZE)
     *
     * @return packed int[irisHeight][irisWidth]
     */
    public static int[][] build(int irisWidth, int irisHeight) {
        int[][] table = new int[irisHeight][irisWidth];

        double cx = (irisWidth - 1) / 2.0;
        double cy = (irisHeight - 1) / 2.0;

        // Normalise by the circle RADIUS (not the diagonal) so that pixels
        // exactly on the circle boundary get dist == 1.0 → distEncoded == 0,
        // and pixels outside the circle also clamp to distEncoded == 0.
        // This is what makes the iris render as a circle, not a square.
        double radius = Math.min(cx, cy);

        for (int y = 0; y < irisHeight; y++) {
            for (int x = 0; x < irisWidth; x++) {
                double dx = x - cx;
                double dy = y - cy;

                // Normalised distance: 0.0 at centre, 1.0 at circle edge, >1.0 in corners
                double dist = Math.sqrt(dx * dx + dy * dy) / radius;

                // distEncoded: 127 = centre, 0 = circle edge OR outside circle.
                // max(0, ...) ensures corner pixels (dist > 1) clamp to 0.
                int distEncoded = (int) Math.max(0, Math.min(127, (1.0 - dist) * 128.0));

                // Angle: atan2 → -π..+π → normalised to 0-511
                double angle = Math.atan2(dy, dx);
                if (angle < 0)
                    angle += 2.0 * Math.PI;
                int angleEncoded = (int) (angle / (2.0 * Math.PI) * 512.0) & 0x1FF;

                // Pack: angle in bits 15:7, distance in bits 6:0
                table[y][x] = (angleEncoded << 7) | distEncoded;
            }
        }
        return table;
    }
}
