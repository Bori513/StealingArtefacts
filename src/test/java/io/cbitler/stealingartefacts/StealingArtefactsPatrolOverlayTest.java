package io.cbitler.stealingartefacts;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
