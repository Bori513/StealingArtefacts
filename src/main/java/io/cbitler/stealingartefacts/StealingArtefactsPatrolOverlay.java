package io.cbitler.stealingartefacts;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Overlay to highlight the patrol-people in port pisc
 */
public class StealingArtefactsPatrolOverlay extends Overlay {
    public static final Color CLICKBOX_BORDER = Color.YELLOW;
    public static final Color CLICKBOX_FILL_COLOR = new Color(255, 0, 0, 50);

    public static final Color CLICKBOX_FILL_COLOR_LURED = new Color(0, 255, 0, 50);

    private static final Color DIRECTION_ARROW_COLOR = Color.YELLOW;
    private static final Color DIRECTION_ARROW_OUTLINE_COLOR = new Color(20, 20, 20);
    private static final Stroke DIRECTION_ARROW_OUTLINE_STROKE = new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private static final Color VISION_TILE_BORDER_COLOR = new Color(180, 0, 0, 180);
    private static final Color VISION_TILE_FILL_COLOR = new Color(255, 0, 0, 35);
    private static final Stroke VISION_TILE_BORDER_STROKE = new BasicStroke(1.0f);

    private static final double ARROW_START = -0.1 * Perspective.LOCAL_TILE_SIZE;
    private static final double ARROW_SHAFT_END = 0.18 * Perspective.LOCAL_TILE_SIZE;
    private static final double ARROW_TIP = 0.42 * Perspective.LOCAL_TILE_SIZE;
    private static final double ARROW_SHAFT_HALF_WIDTH = 0.05 * Perspective.LOCAL_TILE_SIZE;
    private static final double ARROW_HEAD_HALF_WIDTH = 0.17 * Perspective.LOCAL_TILE_SIZE;

    // Each entry is {forward distance, lateral distance} from the patrol's tile.
    private static final int[][] VISION_PATTERN = {
            {1, -1}, {1, 0}, {1, 1},
            {2, -1}, {2, 0}, {2, 1},
            {3, -2}, {3, -1}, {3, 0}, {3, 1}, {3, 2}
    };

    // RuneLite cardinal orientation order: south, west, north, east.
    private static final int[][] CARDINAL_DIRECTIONS = {
            {0, -1}, {-1, 0}, {0, 1}, {1, 0}
    };

    private final StealingArtefactsPlugin plugin;
    private final StealingArtefactsConfig config;
    private final Client client;

    @Inject
    StealingArtefactsPatrolOverlay(Client client, StealingArtefactsPlugin plugin, StealingArtefactsConfig config) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Overlay the patrol-people on the same plane as the player
     * @param graphics The graphics to draw the overlay with
     * @return null, use OverlayUtil to draw overlay
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        Point mousePosition = client.getMouseCanvasPosition();
        for (NPC actor : plugin.markedNPCs) {
            if (actor.getWorldLocation().getPlane() != client.getPlane()) {
                continue;
            }

            if (config.highlightPatrols()) {
                if ((actor.getId() == Constants.PATROL_ID_MAX) && plugin.isGuardLured(actor) && config.highlightGuardLures()) {
                    OverlayUtil.renderHoverableArea(graphics, actor.getConvexHull(),
                            mousePosition, CLICKBOX_FILL_COLOR_LURED, CLICKBOX_BORDER, CLICKBOX_BORDER);
                } else {
                    OverlayUtil.renderHoverableArea(graphics, actor.getConvexHull(),
                            mousePosition, CLICKBOX_FILL_COLOR, CLICKBOX_BORDER, CLICKBOX_BORDER);
                }
            }

            if (config.showPatrolFacingDirection()) {
                renderFacingDirection(graphics, actor);
            }

            if (config.showPatrolVisionTiles()) {
                renderVisionTiles(graphics, actor);
            }
        }

        return null;
    }

    private void renderFacingDirection(Graphics2D graphics, NPC actor) {
        LocalPoint actorLocation = actor.getLocalLocation();
        if (actorLocation == null) {
            return;
        }

        double[][] offsets = arrowOffsets(actor.getCurrentOrientation());
        Polygon arrow = new Polygon();
        for (double[] offset : offsets) {
            LocalPoint arrowPoint = actorLocation.plus((int) Math.round(offset[0]), (int) Math.round(offset[1]));
            Point canvasPoint = Perspective.localToCanvas(client, arrowPoint, client.getPlane());
            if (canvasPoint == null) {
                return;
            }
            arrow.addPoint(canvasPoint.getX(), canvasPoint.getY());
        }

        Color originalColor = graphics.getColor();
        Stroke originalStroke = graphics.getStroke();
        try {
            graphics.setColor(DIRECTION_ARROW_COLOR);
            graphics.fill(arrow);
            graphics.setColor(DIRECTION_ARROW_OUTLINE_COLOR);
            graphics.setStroke(DIRECTION_ARROW_OUTLINE_STROKE);
            graphics.draw(arrow);
        } finally {
            graphics.setColor(originalColor);
            graphics.setStroke(originalStroke);
        }
    }

    private void renderVisionTiles(Graphics2D graphics, NPC actor) {
        WorldPoint origin = actor.getWorldLocation();
        for (Point offset : visionTileOffsets(actor.getCurrentOrientation())) {
            WorldPoint visionTile = new WorldPoint(
                    origin.getX() + offset.getX(),
                    origin.getY() + offset.getY(),
                    origin.getPlane());

            if (!actor.getWorldArea().hasLineOfSightTo(actor.getWorldView(), visionTile)) {
                continue;
            }

            LocalPoint localTile = LocalPoint.fromWorld(actor.getWorldView(), visionTile);
            if (localTile == null) {
                continue;
            }

            Polygon tilePolygon = Perspective.getCanvasTilePoly(client, localTile);
            if (tilePolygon == null) {
                continue;
            }

            Color originalColor = graphics.getColor();
            Stroke originalStroke = graphics.getStroke();
            try {
                graphics.setColor(VISION_TILE_FILL_COLOR);
                graphics.fill(tilePolygon);
                graphics.setColor(VISION_TILE_BORDER_COLOR);
                graphics.setStroke(VISION_TILE_BORDER_STROKE);
                graphics.draw(tilePolygon);
            } finally {
                graphics.setColor(originalColor);
                graphics.setStroke(originalStroke);
            }
        }
    }

    static double[] directionVector(int orientation) {
        double angle = (orientation & 2047) * Perspective.UNIT;
        return new double[] {-Math.sin(angle), -Math.cos(angle)};
    }

    static double[][] arrowOffsets(int orientation) {
        double[] forward = directionVector(orientation);
        double rightX = -forward[1];
        double rightY = forward[0];

        return new double[][] {
                pointAlongArrow(forward, rightX, rightY, ARROW_START, ARROW_SHAFT_HALF_WIDTH),
                pointAlongArrow(forward, rightX, rightY, ARROW_SHAFT_END, ARROW_SHAFT_HALF_WIDTH),
                pointAlongArrow(forward, rightX, rightY, ARROW_SHAFT_END, ARROW_HEAD_HALF_WIDTH),
                pointAlongArrow(forward, rightX, rightY, ARROW_TIP, 0),
                pointAlongArrow(forward, rightX, rightY, ARROW_SHAFT_END, -ARROW_HEAD_HALF_WIDTH),
                pointAlongArrow(forward, rightX, rightY, ARROW_SHAFT_END, -ARROW_SHAFT_HALF_WIDTH),
                pointAlongArrow(forward, rightX, rightY, ARROW_START, -ARROW_SHAFT_HALF_WIDTH)
        };
    }

    static List<Point> visionTileOffsets(int orientation) {
        int cardinalIndex = ((orientation & 2047) + 256) / 512 & 3;
        int forwardX = CARDINAL_DIRECTIONS[cardinalIndex][0];
        int forwardY = CARDINAL_DIRECTIONS[cardinalIndex][1];
        int lateralX = -forwardY;
        int lateralY = forwardX;

        List<Point> offsets = new ArrayList<>(VISION_PATTERN.length);
        for (int[] patternPoint : VISION_PATTERN) {
            int forward = patternPoint[0];
            int lateral = patternPoint[1];
            offsets.add(new Point(
                    forwardX * forward + lateralX * lateral,
                    forwardY * forward + lateralY * lateral));
        }
        return offsets;
    }

    private static double[] pointAlongArrow(double[] forward, double rightX, double rightY, double distance, double width) {
        return new double[] {
                forward[0] * distance + rightX * width,
                forward[1] * distance + rightY * width
        };
    }
}
