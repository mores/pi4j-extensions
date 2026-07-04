package com.mores.uncanny;

/**
 * Renders one pixel of the eye using the polar-coordinate iris technique from the Adafruit Uncanny Eyes Arduino source.
 * <p>
 * How it works:
 * <ol>
 * <li>Check both eyelid threshold maps; if covered, return lid colour.
 * <li>Map the screen pixel to sclera texture coordinates via the scroll offsets.
 * <li>Check whether the sclera-space coordinate falls inside the iris CIRCLE (a square region of size IRIS_CIRCLE_SIZE
 * × IRIS_CIRCLE_SIZE, centred in the sclera).
 * <li>Look up the packed polar value for that circle pixel: bits 6:0 = distEncoded (0 = outer edge, 127 =
 * centre/pupil), bits 15:7 = angleEncoded 0-511 (full circle).
 * <li>If distEncoded &gt; irisEdge (the central region) → pupil (black).
 * <li>Otherwise → look up iris texture row/col and return that colour. The iris texture is the rectangular polar strip
 * (iris.png, 256×64). Row 0 = outer limbal ring (dark), row 63 = inner edge (dark), middle rows = the bright colourful
 * iris pattern.
 * </ol>
 * <p>
 * Key dimension distinction:
 * <ul>
 * <li>{@code irisCircleSize} = IRIS_CIRCLE_SIZE — the square bounding box of the circular iris region in sclera pixel
 * space (e.g. 148×148). The polar table has these dimensions.
 * <li>iris texture = iris.png dimensions (IRIS_TEX_W × IRIS_TEX_H = 256×64). Addressed via the polar-table output.
 * </ul>
 * <p>
 * Thread safety: each eye should have its own IrisRenderer; the class is stateless between {@link #getPixel} calls.
 */
public class IrisRenderer {

    // Textures
    private final int[][] polar; // [IRIS_CIRCLE_SIZE][IRIS_CIRCLE_SIZE] packed polar coords
    private final int[][] iris; // [IRIS_TEX_H][IRIS_TEX_W] ARGB-32 texture strip
    private final int[][] sclera; // [SCLERA_H][SCLERA_W] ARGB-32 texture
    private final EyelidRenderer lids;

    // Cached dimensions
    private final int circleSize; // iris circle diameter in sclera pixels
    private final int scleraW, scleraH;
    private final int screenW, screenH;
    private final int irisTexW, irisTexH;

    /** Opaque black used for pupil and eyelid pixels. */
    public static final int LID_COLOR = 0xFF000000;

    /**
     * @param polar
     *            polar lookup table from {@link PolarTable#build(int,int)} with dimensions
     *            [IRIS_CIRCLE_SIZE][IRIS_CIRCLE_SIZE]
     * @param iris
     *            iris texture strip (rows = radius bands, cols = angles), ARGB-32; row 0 = outer limbal edge, last row
     *            = inner pupil edge
     * @param sclera
     *            sclera texture, ARGB-32
     * @param lids
     *            pre-constructed eyelid renderer for this eye
     */
    public IrisRenderer(int[][] polar, int[][] iris, int[][] sclera, EyelidRenderer lids) {
        this.polar = polar;
        this.iris = iris;
        this.sclera = sclera;
        this.lids = lids;

        this.circleSize = EyeConfig.IRIS_CIRCLE_SIZE;
        this.scleraW = EyeConfig.SCLERA_WIDTH;
        this.scleraH = EyeConfig.SCLERA_HEIGHT;
        this.screenW = EyeConfig.SCREEN_WIDTH;
        this.screenH = EyeConfig.SCREEN_HEIGHT;
        this.irisTexH = iris.length;
        this.irisTexW = iris[0].length;
    }

    /**
     * Returns the ARGB-32 colour for screen pixel (screenX, screenY).
     *
     * @param screenX
     *            0 .. SCREEN_WIDTH-1
     * @param screenY
     *            0 .. SCREEN_HEIGHT-1
     * @param scleraX
     *            sclera horizontal scroll offset (pixels)
     * @param scleraY
     *            sclera vertical scroll offset (pixels)
     * @param iScale
     *            iris scale IRIS_MIN..IRIS_MAX (higher = brighter room = smaller pupil = more iris visible)
     * @param uT
     *            upper-lid threshold from {@link EyelidRenderer#computeThresholds}
     * @param lT
     *            lower-lid threshold from {@link EyelidRenderer#computeThresholds}
     */
    public int getPixel(int screenX, int screenY, int scleraX, int scleraY, int iScale, int uT, int lT) {

        // ── 1. Eyelid test ────────────────────────────────────────────────────
        if (lids.isEyelid(screenX, screenY, uT, lT)) {
            return LID_COLOR;
        }

        // ── 2. Map screen pixel → sclera coordinates ──────────────────────────
        int sx = scleraX + screenX;
        int sy = scleraY + screenY;

        // ── 3. Is this pixel inside the iris CIRCLE bounding box? ─────────────
        // The iris circle is centred in the sclera texture.
        int irisLeft = (scleraW - circleSize) / 2;
        int irisTop = (scleraH - circleSize) / 2;
        int irisX = sx - irisLeft; // position within the circle bounding box
        int irisY = sy - irisTop;

        if (irisX < 0 || irisX >= circleSize || irisY < 0 || irisY >= circleSize) {
            return safeSclera(sx, sy);
        }

        // ── 4. Polar lookup for this circle pixel ─────────────────────────────
        int packed = polar[irisY][irisX] & 0xFFFF;
        int distEncoded = packed & 0x7F; // 0 = outer edge, 127 = centre
        int angleEncoded = packed >> 7; // 0-511 full circle

        // distEncoded == 0 means the pixel is in the corner of the bounding square
        // but outside the actual circle — render as sclera.
        if (distEncoded == 0) {
            return safeSclera(sx, sy);
        }

        // ── 5. Pupil / iris boundary ──────────────────────────────────────────
        // distEncoded: 0 = outer iris edge (limbal ring), 127 = centre (pupil).
        // irisEdge = the distEncoded threshold that separates iris from pupil.
        // Pixels with distEncoded <= irisEdge → iris ring (show texture).
        // Pixels with distEncoded > irisEdge → pupil (black).
        //
        // Larger iScale (bright room) → smaller pupil → larger irisEdge.
        // irisEdge range: ~86 (IRIS_MIN, large pupil) .. ~95 (IRIS_MAX, small pupil).
        // Pupil radius in pixels = (127 - irisEdge) / 127 * 74:
        // At irisEdge=95 (bright) → ~19px pupil radius ✓
        // At irisEdge=86 (dark) → ~24px pupil radius ✓
        int irisEdge = 85 + iScale * 15 / 1023;

        if (distEncoded > irisEdge) {
            return LID_COLOR; // pupil
        }

        // ── 6. Iris texture lookup ────────────────────────────────────────────
        // iris.png polar strip: row 0 = outer limbal (dark), row H-1 = inner (dark).
        // distEncoded rises from 0 (outer edge) to irisEdge (inner edge of iris ring),
        // so we map distEncoded directly to texRow: low→row 0, high→row H-1.
        int texRow = distEncoded * irisTexH / Math.max(1, irisEdge);
        // angleEncoded 0-511 → column 0..irisTexW-1
        int texCol = angleEncoded * irisTexW / 512;

        texRow = Math.max(0, Math.min(texRow, irisTexH - 1));
        texCol = Math.max(0, Math.min(texCol, irisTexW - 1));

        return iris[texRow][texCol];
    }

    /**
     * Fill an entire frame into a pre-allocated flat ARGB-32 buffer. Row-major: index = y * screenW + x.
     */
    public void fillFrame(int[] frameBuf, int scleraX, int scleraY, int iScale, int uT, int lT) {
        for (int y = 0; y < screenH; y++) {
            for (int x = 0; x < screenW; x++) {
                frameBuf[y * screenW + x] = getPixel(x, y, scleraX, scleraY, iScale, uT, lT);
            }
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Clamp-safe sclera lookup. */
    private int safeSclera(int sx, int sy) {
        sx = Math.max(0, Math.min(sx, scleraW - 1));
        sy = Math.max(0, Math.min(sy, scleraH - 1));
        return sclera[sy][sx];
    }
}
