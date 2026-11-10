package fi.vesas.wfc.gen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Post-generation pass: flood-fills from a start cell over a walkable
 * {@link BoolLayer}. Throws {@link GenerationFailedException} if any walkable
 * cell is unreachable — game code catches and retries with a new seed.
 *
 * 4-connected (N/E/S/W) by default. Cells where the walkable layer is
 * {@code false} are treated as blocked.
 */
public class ReachabilityCheckPass implements GenPass {

    private final String walkableLayerName;
    private final int startX;
    private final int startY;

    public ReachabilityCheckPass(String walkableLayerName, int startX, int startY) {
        this.walkableLayerName = walkableLayerName;
        this.startX = startX;
        this.startY = startY;
    }

    @Override
    public void apply(GenContext ctx) {

        BoolLayer walkable = ctx.boolLayer(walkableLayerName);
        int w = ctx.getWidth();
        int h = ctx.getHeight();

        if (startX < 0 || startX >= w || startY < 0 || startY >= h) {
            throw new GenerationFailedException(
                    "Reachability start " + startX + "," + startY + " out of bounds");
        }
        if (!walkable.get(startX, startY)) {
            throw new GenerationFailedException(
                    "Reachability start " + startX + "," + startY + " is not walkable");
        }

        boolean[][] visited = new boolean[w][h];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.push(new int[]{startX, startY});
        visited[startX][startY] = true;
        int reachable = 1;

        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int[] pos = queue.pop();
            for (int d = 0; d < 4; d++) {
                int nx = pos[0] + dx[d];
                int ny = pos[1] + dy[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[nx][ny]) continue;
                if (!walkable.get(nx, ny)) continue;
                visited[nx][ny] = true;
                reachable++;
                queue.push(new int[]{nx, ny});
            }
        }

        int totalWalkable = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (walkable.get(x, y)) totalWalkable++;
            }
        }

        if (reachable < totalWalkable) {
            throw new GenerationFailedException(
                    "Reachability: only " + reachable + "/" + totalWalkable
                    + " walkable cells reachable from " + startX + "," + startY);
        }
    }
}
