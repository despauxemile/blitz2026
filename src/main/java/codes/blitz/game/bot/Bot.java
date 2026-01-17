package codes.blitz.game.bot;

import codes.blitz.game.generated.*;

import java.sql.Array;
import java.util.*;

enum SpawnerState {FewerStronger, MoreWeaker}

public class Bot {
    Random random = new Random();
    static List<PosNutrient> sortedNutrient = new ArrayList<>();
    static HashMap<String, List<PathFinder.State>> pathss = new HashMap<>();
    static SpawnerState spawnerState = SpawnerState.FewerStronger;
    static boolean weWinning = false;
    static boolean first = true;

    public Bot() {
        System.out.println("Initializing your super mega duper bot");
    }

    /*
     * Here is where the magic happens, for now the moves are not very good. I bet you can do better ;)
     */
    public List<Action> getActions(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();

        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        weWinning = weHaveAdvantage(gameMessage);

        if (first) {
            actions.addAll(conceiveSpawnerFromNotJoinable(gameMessage));
            first = false;
        }
        HashMap<String, List<PathFinder.State>> pathsnew = new HashMap<>();
        for (Spore spor : gameMessage.world().spores()) {
            if (pathss.containsKey(spor.id())) {
                pathsnew.put(spor.id(), pathss.get(spor.id()));
            }
        }

        if (spawnerState == SpawnerState.MoreWeaker) {
            System.out.println("We are bursting");
        }
        if (weWinning && spawnerState == SpawnerState.FewerStronger) {
            System.out.println("We have advantage");
            spawnerState = SpawnerState.MoreWeaker;
        }

        if (decideIfCreateSpawner(gameMessage)) {
            actions.add(new SporeCreateSpawnerAction(getIdSporeFurtherFromOtherTeam(gameMessage)));
        }
        for (int i = 0; i < myTeam.spawners().size(); i++) {
            if (spawnerState == SpawnerState.MoreWeaker || decideIfSpawnSpore(gameMessage)) {
                actions.add(new SpawnerProduceSporeAction(myTeam.spawners().get(i).id(), myTeam.nutrients() / myTeam.spawners().size()));
            }
        }

        actions.addAll(determineActionAllSpore(gameMessage));

        return actions;
    }

    private List<Action> conceiveSpawnerFromNotJoinable(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        for (Spore spore : gameMessage.world().spores()) {
            boolean canReachOne = false;
            for (Spawner spawner : gameMessage.world().spawners()) {
                if (shortestPathRealCost(gameMessage, spore.position(), spawner.position()).getLast().cost > spore.biomass()) {
                    canReachOne = true;
                }
            }
            if (!canReachOne) {
                actions.add(new SporeCreateSpawnerAction(spore.id()));
            }
        }
        return actions;
    }

    public boolean decideIfCreateSpawner(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.spawners().isEmpty() || (spawnerState == SpawnerState.MoreWeaker && myTeam.spawners().size() < 4 && gameMessage.tick() > 50)) {
            return true;
        }
        return false;
    }

    public boolean decideIfSpawnSpore(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.spores().isEmpty())
            return true;
        Spore strongest = getStrongestSpore(gameMessage);
        return myTeam.nutrients() > strongest.biomass();
    }


    public Spore getStrongestSpore(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        Optional<Spore> result = myTeam.spores().stream().max(Comparator.comparing(Spore::biomass));
        return result.orElse(null);
    }

    public String getIdSporeFurtherFromOtherTeam(TeamGameState gameState) {
        var ourTeam = gameState.yourTeamId();
        List<Spore> ourSpores = gameState.world().spores().stream().filter(s -> s.teamId().equals(ourTeam)).toList();
        List<Spore> enemySpores = gameState.world().spores().stream().filter(s -> !s.teamId().equals(ourTeam)).toList();

        return ourSpores.stream()
                .map(os -> {
                    var maxDist = enemySpores.stream().map(es -> {
                        var dist = distanceSporePosition(os.position(), es.position());
                        return dist;
                    }).max(Integer::compare).orElse(0);

                    return new Pair<>(os, maxDist);
                })
                .max(Comparator.comparingInt(Pair::second))
                .map(p -> p.first().id())
                .orElse(null);
    }

    public List<Action> determineActionAllSpore(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        for (Spore spore : myTeam.spores()) {
            actions.add(determineSporeAction(gameMessage, spore));
        }
        return actions;
    }

    //transformer ca en liste des plus nutrimenté
    // ensuite les spores décident s'ils sont capables de s'y rendre
    public List<PosNutrient> determineCellMostNutrient(TeamGameState gameMessage) {

        if (!sortedNutrient.isEmpty()) {
            return sortedNutrient;
        }

        List<PosNutrient> positions = new ArrayList<>();
        int width = gameMessage.world().map().width();
        int height = gameMessage.world().map().height();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                positions.add(new PosNutrient(new Position(i, j), gameMessage.world().map().nutrientGrid()[i][j]));
            }
        }
        positions.sort(Comparator.comparingInt(s -> s.nutrient));
        return positions.reversed();

    }

    /**
     * Assume que la liste de position passée est triée
     * en fonction que le premier est la position avec le plus de nutriments
     */
    public List<List<PathFinder.State>> determineMostNutrientAbleToGo(TeamGameState gameMessage, Spore spore, List<PosNutrient> sortedNutrientPosition) {
        List<List<PathFinder.State>> positions = new ArrayList<>();
        for (PosNutrient posNutrient : sortedNutrientPosition) {
            Position position = posNutrient.position;
            if (!Objects.equals(gameMessage.world().ownershipGrid()[position.x()][position.y()], gameMessage.yourTeamId())) {
                List<PathFinder.State> shortest = shortestPathRealCost(gameMessage, spore.position(), position);
                if (shortest.isEmpty()) {
                    continue;
                }
                int dist = shortest.getLast().cost;
                if (dist <= spore.biomass()) {
                    positions.add(shortest);
                }
            }
        }
        return positions;
    }

    public Action determineSporeAction(TeamGameState gameMessage, Spore spore) {
        if (gameMessage.world().map().nutrientGrid()[spore.position().x()][spore.position().y()] > 0) {
            if (noSpawnerAroundPoint(gameMessage, spore.position())) {
                TeamInfo ours = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
                if (ours.nutrients() > ours.nextSpawnerCost()) {
                    return new SporeCreateSpawnerAction(spore.id());
                }
            }
        }

        if (!pathss.containsKey(spore.id())) {
            List<PosNutrient> positionsSortedNutrient = determineCellMostNutrient(gameMessage);
            List<List<PathFinder.State>> ableToGo = determineMostNutrientAbleToGo(gameMessage, spore, positionsSortedNutrient);
            if (ableToGo.isEmpty()) {
                int ranx = random.nextInt(gameMessage.world().map().width());
                int rany = random.nextInt(gameMessage.world().map().height());
                return new SporeMoveToAction(spore.id(), new Position(ranx, rany));
            }
            for (List<PathFinder.State> states : ableToGo) {
                int already = 0;
                for (List<PathFinder.State> objective : pathss.values()) {
                    if (objective.getLast().equals(states.getLast())) {
                        already += 1;
                    }
                }
                if (already < 4) {
                    pathss.put(spore.id(), states);
                    break;
                }
            }
            if (!pathss.containsKey(spore.id())) {
                pathss.put(spore.id(), ableToGo.getFirst());
            }
        }
        PathFinder.State nextPos = pathss.get(spore.id()).getFirst();
        pathss.get(spore.id()).removeFirst();
        if (pathss.get(spore.id()).isEmpty()) {
            pathss.remove(spore.id());
        }
        return new SporeMoveToAction(spore.id(), new Position(nextPos.x, nextPos.y));
    }

    private boolean noSpawnerAroundPoint(TeamGameState gameMessage, Position position) {
        int x = position.x();
        int y = position.y();
        TeamInfo ours = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        for (Spawner spawner : ours.spawners()) {
            int dist = distanceSporePosition(position, spawner.position());
            if (dist < 4 && dist > 0) {
                return false;
            }
        }
        return true;
    }

    public int distanceSporePosition(Position spore, Position position) {
        return Math.abs(spore.x() - position.x()) + Math.abs(spore.y() - position.y());
    }

    public List<PathFinder.State> shortestPathRealCost(TeamGameState gameState, Position start, Position going) {
        return PathFinder.shortestPath(start, going, gameState);
    }

    public static int howMuchTeamProduce(TeamGameState gameMessage, String teamId) {
        int tot = 0;
        for (int i = 0; i < gameMessage.world().ownershipGrid().length; i++) {
            for (int j = 0; j < gameMessage.world().ownershipGrid()[0].length; j++) {
                if (Objects.equals(gameMessage.world().ownershipGrid()[i][j], teamId)) {
                    tot += gameMessage.world().map().nutrientGrid()[i][j];
                }
            }
        }
        return tot;
    }

    public static boolean weHaveAdvantage(TeamGameState gameState) {
        return Objects.equals(advantagedTeam(gameState), gameState.yourTeamId()) || gameState.tick() < 50;
    }

    public static String advantagedTeam(TeamGameState gameState) {
        String maxiS = gameState.yourTeamId();
        int maxi = 0;
        for (String teams : gameState.teamIds()) {
            if (teams.equals(gameState.constants().neutralTeamId())) {
                continue;
            }
            if (maxi < howMuchTeamProduce(gameState, teams)) {
                maxiS = teams;
            }
        }
        return maxiS;
    }
}
