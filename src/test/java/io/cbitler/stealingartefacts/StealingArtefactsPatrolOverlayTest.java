package io.cbitler.stealingartefacts;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import net.runelite.api.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StealingArtefactsPatrolOverlayTest {
    private static final double TOLERANCE = 0.0001;

    static Stream<Arguments> orientations() {
        double diagonal = Math.sqrt(0.5);
        return Stream.of(
                Arguments.of(0, 0.0, -1.0),
                Arguments.of(512, -1.0, 0.0),
                Arguments.of(1024, 0.0, 1.0),
                Arguments.of(1536, 1.0, 0.0),
                Arguments.of(256, -diagonal, -diagonal),
                Arguments.of(1280, diagonal, diagonal)
        );
    }

    @ParameterizedTest
    @MethodSource("orientations")
    void directionVectorUsesRawRuneLiteOrientation(int orientation, double expectedX, double expectedY) {
        double[] direction = StealingArtefactsPatrolOverlay.directionVector(orientation);

        assertEquals(expectedX, direction[0], TOLERANCE);
        assertEquals(expectedY, direction[1], TOLERANCE);
    }

    @ParameterizedTest
    @MethodSource("orientations")
    void arrowTipPointsInFacingDirection(int orientation, double expectedX, double expectedY) {
        double[][] offsets = StealingArtefactsPatrolOverlay.arrowOffsets(orientation);
        double[] tip = offsets[3];
        double tipLength = Math.hypot(tip[0], tip[1]);

        assertEquals(expectedX, tip[0] / tipLength, TOLERANCE);
        assertEquals(expectedY, tip[1] / tipLength, TOLERANCE);

        for (double[] offset : offsets) {
            assertTrue(Math.hypot(offset[0], offset[1]) < 0.5 * net.runelite.api.Perspective.LOCAL_TILE_SIZE);
        }
    }

    static Stream<Arguments> cardinalVisionPatterns() {
        return Stream.of(
                Arguments.of(0, 0, -1),
                Arguments.of(512, -1, 0),
                Arguments.of(1024, 0, 1),
                Arguments.of(1536, 1, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("cardinalVisionPatterns")
    void visionPatternRotatesToCardinalDirection(int orientation, int forwardX, int forwardY) {
        List<Point> offsets = StealingArtefactsPatrolOverlay.visionTileOffsets(orientation);
        Set<String> actual = asCoordinateSet(offsets);
        int lateralX = -forwardY;
        int lateralY = forwardX;

        assertEquals(11, offsets.size());
        assertEquals(11, actual.size());
        assertFalse(actual.contains("0,0"));

        for (int forward = 1; forward <= 3; forward++) {
            int lateralRange = forward == 3 ? 2 : 1;
            for (int lateral = -lateralRange; lateral <= lateralRange; lateral++) {
                int expectedX = forwardX * forward + lateralX * lateral;
                int expectedY = forwardY * forward + lateralY * lateral;
                assertTrue(actual.contains(expectedX + "," + expectedY));
            }
        }

        for (Point offset : offsets) {
            assertTrue(offset.getX() * forwardX + offset.getY() * forwardY > 0);
        }
    }

    @ParameterizedTest
    @MethodSource("cardinalVisionPatterns")
    void nearbyOrientationsSnapToNearestCardinal(int orientation, int forwardX, int forwardY) {
        Set<String> expected = asCoordinateSet(StealingArtefactsPatrolOverlay.visionTileOffsets(orientation));
        Set<String> below = asCoordinateSet(StealingArtefactsPatrolOverlay.visionTileOffsets(orientation - 255));
        Set<String> above = asCoordinateSet(StealingArtefactsPatrolOverlay.visionTileOffsets(orientation + 255));

        assertEquals(expected, below);
        assertEquals(expected, above);
    }

    private static Set<String> asCoordinateSet(List<Point> points) {
        Set<String> coordinates = new HashSet<>();
        for (Point point : points) {
            coordinates.add(point.getX() + "," + point.getY());
        }
        return coordinates;
    }
}
