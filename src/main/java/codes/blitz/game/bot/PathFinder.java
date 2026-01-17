package codes.blitz.game.bot;

import codes.blitz.game.generated.Position;
import codes.blitz.game.generated.TeamGameState;

import java.util.*;

import static codes.blitz.game.bot.Bot.weHaveAdvantage;
import static codes.blitz.game.bot.Bot.weWinning;

public class PathFinder {

    public static class State {
        int x, y, cost;

        State(int x, int y, int cost) {
            this.x = x;
            this.y = y;
            this.cost = cost;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof State state)) return false;
            return x == state.x && y == state.y;
        }

        @Override
        public String toString() {
            return "State{" +
                    "x=" + x +
                    ", y=" + y +
                    ", cost=" + cost +
                    '}';
        }
    }

    public static List<State> shortestPath(
            Position startingPos, Position goal,
            TeamGameState gameMessage
    ) {
        int startX = startingPos.x();
        int startY = startingPos.y();
        int goalX = goal.x();
        int goalY = goal.y();

        int[][] grid = gameMessage.world().map().nutrientGrid();

        int oursWinningValue = weWinning && gameMessage.tick() > 800 ? 1 : 0;
        int enemyEmptyValue = weWinning && gameMessage.tick() > 8000 ? 0 : 1;

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                boolean isOurs = Objects.equals(gameMessage.world().ownershipGrid()[i][j], gameMessage.yourTeamId());
                if (isOurs) {
                    grid[i][j] = oursWinningValue;
                } else if (gameMessage.world().biomassGrid()[i][j] != 0) {
                    grid[i][j] = gameMessage.world().biomassGrid()[i][j];
                } else {
                    grid[i][j] = enemyEmptyValue;
                }
            }
        }

        int rows = grid.length;
        int cols = grid[0].length;

        int[][] dist = new int[rows][cols];
        State[][] parent = new State[rows][cols];

        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);

        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingInt(s -> s.cost));

        pq.add(new State(startX, startY, 0));
        dist[startX][startY] = 0;

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!pq.isEmpty()) {
            State current = pq.poll();

            if (current.cost > dist[current.x][current.y]) continue;

            if (current.x == goalX && current.y == goalY) {
                return reconstructPath(parent, startX, startY, goalX, goalY, dist[goalX][goalY]);
            }

            for (int i = 0; i < 4; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];

                if (nx < 0 || ny < 0 || nx >= rows || ny >= cols) continue;
                if (grid[nx][ny] < 0) continue; // case bloquée

                int newCost = current.cost + grid[nx][ny];

                if (newCost < dist[nx][ny]) {
                    dist[nx][ny] = newCost;
                    parent[nx][ny] = new State(current.x, current.y, current.cost);
                    pq.add(new State(nx, ny, newCost));
                }
            }
        }

        return Collections.emptyList(); // pas de chemin
    }

    private static List<State> reconstructPath(
            State[][] parent,
            int startX, int startY,
            int goalX, int goalY,
            int totalCost
    ) {
        LinkedList<State> path = new LinkedList<>();
        State current = new State(goalX, goalY, totalCost);

        while (current != null) {
            path.addFirst(current);
            if (current.x == startX && current.y == startY) break;
            current = parent[current.x][current.y];
        }

        path.removeFirst();
        return path;
    }

}
