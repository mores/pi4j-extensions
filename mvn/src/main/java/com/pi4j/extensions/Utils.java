package com.pi4j.extensions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.numbers.core.Precision;
import org.apache.commons.geometry.euclidean.twod.Lines;
import org.apache.commons.geometry.euclidean.twod.Segment;
import org.apache.commons.geometry.euclidean.twod.Vector2D;

public class Utils {

    public static void delay(Duration duration) {

        try {
            long nanos = duration.toNanos();
            long millis = nanos / 1_000_000;
            int remainingNanos = (int) (nanos % 1_000_000);
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static List<Point2D> pointsOnLine(Line2D line, int numSegments) {

        List<Point2D> points = new ArrayList<>();

        if (numSegments == 1) {
            points.add(line.getP1());
            return points;
        }

        Precision.DoubleEquivalence precision = Precision.doubleEquivalenceOfEpsilon(1e-6);
        Segment segment = Lines.segmentFromPoints(Vector2D.of(line.getX1(), line.getY1()),
                Vector2D.of(line.getX2(), line.getY2()), precision);

        Vector2D start = segment.getStartPoint();
        Vector2D end = segment.getEndPoint();
        Vector2D direction = end.subtract(start);

        double segmentLength = direction.norm() / numSegments;

        for (int i = 0; i < numSegments; i++) {

            Vector2D newEnd = start.add(direction.normalize().multiply(segmentLength * (i + 1)));

            points.add(new Point2D.Double(newEnd.getX(), newEnd.getY()));
        }

        return points;
    }
}
