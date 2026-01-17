package codes.blitz.game.bot;

import codes.blitz.game.generated.*;

import java.util.*;

enum SpawnerState {
    FEWER_STRONGER,
    MORE_WEAKER
}

public class Bot {
    static private final Random random = new Random();
    static private final List<PosNutrient> sortedNutrient = new ArrayList<>();
    static private final HashMap<String, List<PathFinder.State>> paths = new HashMap<>();

    static private SpawnerState spawnerState = SpawnerState.FEWER_STRONGER;
    static public boolean weWinning = false;
    static private int bank = 0;
    static private boolean firstTick = true;
    static private int minv = 0;

    public Bot() {
        System.out.println("Initializing your super mega duper bot");
    }

    public List<Action> getActions(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        weWinning = weHaveAdvantage(gameMessage);

        if (firstTick) {
            actions.addAll(createSpawnersForIsolatedSpores(gameMessage));
             minv = Arrays.stream(gameMessage.world().map().nutrientGrid())
                    .flatMapToInt(Arrays::stream)
                    .filter(v -> v > 0)
                    .min()
                    .orElse(0);
            firstTick = false;
        }

        cleanupDeadSporePaths(gameMessage);

        if (weWinning && spawnerState == SpawnerState.FEWER_STRONGER) {
            System.out.println("We have advantage - switching to burst mode");
            spawnerState = SpawnerState.MORE_WEAKER;
        }

        if (spawnerState == SpawnerState.MORE_WEAKER) {
            System.out.println("Burst mode active");
        }

        if (shouldCreateSpawner(gameMessage)) {
            String sporeId = getIdSporeFurthestFromEnemies(gameMessage);
            if (sporeId != null) {
                bank = Math.max(0, bank - myTeam.nextSpawnerCost());
                actions.add(new SporeCreateSpawnerAction(sporeId));
            }
        }

        for (Spawner spawner : myTeam.spawners()) {
            if (spawnerState == SpawnerState.MORE_WEAKER || shouldSpawnSpore(gameMessage)) {
                if (gameMessage.tick() > 25 && !myTeam.spores().isEmpty()) {
                    bank += 1;
                }
                if (myTeam.spawners().size() >= 5) {
                    bank = 0;
                }
                int biomassPerSpawner = Math.max(0, (myTeam.nutrients() - bank) / myTeam.spawners().size());
                actions.add(new SpawnerProduceSporeAction(spawner.id(), biomassPerSpawner));
            }
        }

        actions.addAll(determineActionsForAllSpores(gameMessage));

        return actions;
    }

    private List<Action> createSpawnersForIsolatedSpores(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());

        for (Spore spore : gameMessage.world().spores()) {
            if (!spore.teamId().equals(gameMessage.yourTeamId())) {
                continue;
            }

            boolean canReachAnySpawner = false;
            for (Spawner spawner : gameMessage.world().spawners()) {
                List<PathFinder.State> path = shortestPathRealCost(gameMessage, spore.position(), spawner.position());
                if (!path.isEmpty() && path.getLast().cost <= spore.biomass()) {
                    canReachAnySpawner = true;
                    break;
                }
            }

            if (!canReachAnySpawner) {
                bank = Math.max(0, bank - myTeam.nextSpawnerCost());
                actions.add(new SporeCreateSpawnerAction(spore.id()));
            }
        }
        return actions;
    }

    private void cleanupDeadSporePaths(TeamGameState gameMessage) {
        Set<String> activeSporeIds = new HashSet<>();
        for (Spore spore : gameMessage.world().spores()) {
            activeSporeIds.add(spore.id());
        }
        paths.keySet().retainAll(activeSporeIds);
    }

    private boolean shouldCreateSpawner(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        return myTeam.spawners().isEmpty();
    }

    private boolean shouldSpawnSpore(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.spores().isEmpty()) {
            return true;
        }
        Spore strongest = getStrongestSpore(gameMessage);
        return strongest != null && myTeam.nutrients() > strongest.biomass();
    }

    private Spore getStrongestSpore(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        return myTeam.spores().stream()
                .max(Comparator.comparing(Spore::biomass))
                .orElse(null);
    }

    private String getIdSporeFurthestFromEnemies(TeamGameState gameState) {
        String ourTeam = gameState.yourTeamId();
        List<Spore> ourSpores = gameState.world().spores().stream()
                .filter(s -> s.teamId().equals(ourTeam))
                .toList();
        List<Spore> enemySpores = gameState.world().spores().stream()
                .filter(s -> !s.teamId().equals(ourTeam))
                .toList();

        if (ourSpores.isEmpty()) {
            return null;
        }

        return ourSpores.stream()
                .map(os -> {
                    int minDistToEnemy = enemySpores.stream()
                            .mapToInt(es -> distanceBetweenPositions(os.position(), es.position()))
                            .min()
                            .orElse(Integer.MAX_VALUE);
                    return new Pair<>(os, minDistToEnemy);
                })
                .max(Comparator.comparingInt(Pair::second))
                .map(p -> p.first().id())
                .orElse(null);
    }

    private List<Action> determineActionsForAllSpores(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        for (Spore spore : myTeam.spores()) {
            actions.add(determineSporeAction(gameMessage, spore));
        }
        return actions;
    }

    private List<PosNutrient> getCellsSortedByNutrient(TeamGameState gameMessage) {
        if (!sortedNutrient.isEmpty()) {
            return sortedNutrient;
        }

        List<PosNutrient> positions = new ArrayList<>();
        int width = gameMessage.world().map().width();
        int height = gameMessage.world().map().height();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                positions.add(new PosNutrient(
                        new Position(i, j),
                        gameMessage.world().map().nutrientGrid()[i][j]
                ));
            }
        }

        positions.sort(Comparator.comparingInt(p -> -p.nutrient));
        sortedNutrient.addAll(positions);
        return sortedNutrient;
    }

    private List<List<PathFinder.State>> findReachableNutrientCells(
            TeamGameState gameMessage,
            Spore spore,
            List<PosNutrient> sortedNutrientPositions) {

        List<List<PathFinder.State>> reachablePaths = new ArrayList<>();
        String ourTeamId = gameMessage.yourTeamId();

        for (PosNutrient posNutrient : sortedNutrientPositions) {
            Position position = posNutrient.position;
            String owner = gameMessage.world().ownershipGrid()[position.x()][position.y()];

            if (!Objects.equals(owner, ourTeamId)) {
                List<PathFinder.State> path = shortestPathRealCost(gameMessage, spore.position(), position);
                if (!path.isEmpty()) {
                    reachablePaths.add(path);
                }
            }
        }
        return reachablePaths;
    }

    private Action determineSporeAction(TeamGameState gameMessage, Spore spore) {
        Position sporePos = spore.position();
        int nutrientAtPosition = gameMessage.world().map().nutrientGrid()[sporePos.x()][sporePos.y()];

        if (nutrientAtPosition > 0 && isGoodSpawnerLocation(gameMessage, sporePos)) {
            TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
            if (myTeam.nutrients() > myTeam.nextSpawnerCost()) {
                bank = Math.max(0, bank - myTeam.nextSpawnerCost());
                return new SporeCreateSpawnerAction(spore.id());
            }
        }
        List<PosNutrient> p = getCellsSortedByNutrient(gameMessage);
        p.sort(Comparator.comparingInt(pp -> pp.nutrient == 0 ? 10000 : distanceBetweenPositions(sporePos, pp.position)));
        for (PosNutrient posNutrient : p) {
            if (!Objects.equals(gameMessage.world().ownershipGrid()[posNutrient.position.x()][posNutrient.position.y()], gameMessage.yourTeamId())) {
                return new SporeMoveToAction(spore.id(), posNutrient.position);
            }
        }
        return new SporeMoveToAction(spore.id(), getCellsSortedByNutrient(gameMessage).getFirst().position);
//        if (!paths.containsKey(spore.id())) {
//            assignPathToSpore(gameMessage, spore);
//        }
//
//        if (!paths.containsKey(spore.id())) {
//            return moveToRandomPosition(gameMessage, spore);
//        }
//
//        PathFinder.State nextPos = paths.get(spore.id()).removeFirst();
//        if (paths.get(spore.id()).isEmpty()) {
//            paths.remove(spore.id());
//        }
//
//        return new SporeMoveToAction(spore.id(), new Position(nextPos.x, nextPos.y));
    }

    private void assignPathToSpore(TeamGameState gameMessage, Spore spore) {
        List<PosNutrient> sortedPositions = getCellsSortedByNutrient(gameMessage);
        List<List<PathFinder.State>> reachablePaths = findReachableNutrientCells(gameMessage, spore, sortedPositions);


        if (reachablePaths.isEmpty()) {
            return;
        }

        if (!weWinning) {
            for (List<PathFinder.State> candidatePath : reachablePaths) {
                int sporesTargetingSameGoal = countSporesWithSameGoal(candidatePath);
                if (sporesTargetingSameGoal < 15) {
                    paths.put(spore.id(), new ArrayList<>(candidatePath));
                    return;
                }
            }
        }

        paths.put(spore.id(), new ArrayList<>(reachablePaths.getFirst()));
    }

    private int countSporesWithSameGoal(List<PathFinder.State> candidatePath) {
        PathFinder.State candidateGoal = candidatePath.getLast();
        int count = 0;
        for (List<PathFinder.State> existingPath : paths.values()) {
            if (existingPath.getLast().equals(candidateGoal)) {
                count++;
            }
        }
        return count;
    }

    private Action moveToRandomPosition(TeamGameState gameMessage, Spore spore) {
        int randomX = random.nextInt(gameMessage.world().map().width());
        int randomY = random.nextInt(gameMessage.world().map().height());
        return new SporeMoveToAction(spore.id(), new Position(randomX, randomY));
    }

    private boolean isGoodSpawnerLocation(TeamGameState gameMessage, Position position) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        for (Spawner spawner : myTeam.spawners()) {
            if (distanceBetweenPositions(position, spawner.position()) < 5) {
                return false;
            }
        }
        return true;
    }

    private int distanceBetweenPositions(Position pos1, Position pos2) {
        return Math.abs(pos1.x() - pos2.x()) + Math.abs(pos1.y() - pos2.y());
    }

    private List<PathFinder.State> shortestPathRealCost(TeamGameState gameState, Position start, Position goal) {
        return PathFinder.shortestPath(start, goal, gameState);
    }

    private static int calculateTeamProduction(TeamGameState gameMessage, String teamId) {
        int totalProduction = 0;
        String[][] ownershipGrid = gameMessage.world().ownershipGrid();
        int[][] nutrientGrid = gameMessage.world().map().nutrientGrid();

        for (int i = 0; i < ownershipGrid.length; i++) {
            for (int j = 0; j < ownershipGrid[0].length; j++) {
                if (Objects.equals(ownershipGrid[i][j], teamId)) {
                    totalProduction += nutrientGrid[i][j];
                }
            }
        }
        return totalProduction;
    }

    private static boolean weHaveAdvantage(TeamGameState gameState) {
        return Objects.equals(getLeadingTeam(gameState), gameState.yourTeamId())
                || gameState.tick() < 50;
    }

    private static String getLeadingTeam(TeamGameState gameState) {
        String leadingTeam = gameState.yourTeamId();
        int maxProduction = 0;

        for (String teamId : gameState.teamIds()) {
            if (teamId.equals(gameState.constants().neutralTeamId())) {
                continue;
            }
            int production = calculateTeamProduction(gameState, teamId);
            if (production > maxProduction) {
                maxProduction = production;
                leadingTeam = teamId;
            }
        }
        return leadingTeam;
    }
}