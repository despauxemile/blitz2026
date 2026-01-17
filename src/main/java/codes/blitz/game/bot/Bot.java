package codes.blitz.game.bot;

import codes.blitz.game.generated.*;

import java.sql.Array;
import java.util.*;

public class Bot {
    Random random = new Random();
    static List<PosNutrient> sortedNutrient = new ArrayList<>();

    public Bot() {
        System.out.println("Initializing your super mega duper bot");
    }

    /*
     * Here is where the magic happens, for now the moves are not very good. I bet you can do better ;)
     */
    public List<Action> getActions(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();

        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());

        if (decideIfCreateSpawner(gameMessage)) {
            actions.add(new SporeCreateSpawnerAction(getIdSporeFurtherFromOtherTeam(gameMessage)));
        } else if (decideIfSpawnSpore(gameMessage)) {
            actions.add(new SpawnerProduceSporeAction(myTeam.spawners().getFirst().id(), myTeam.nutrients()));
        }

        actions.addAll(determineActionAllSpore(gameMessage));

        return actions;
    }

    public boolean decideIfCreateSpawner(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.spawners().isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean decideIfSpawnSpore(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.spores().isEmpty())
            return true;
        Spore strongest = getStrongestSpore(gameMessage);
        if (myTeam.nutrients() > 20)
            return true;
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
                    var dist = distanceSporePosition(os, es.position());
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
                if (shortest.isEmpty()){
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
        List<PosNutrient> positionsSortedNutrient = determineCellMostNutrient(gameMessage);
        List<List<PathFinder.State>> ableToGo = determineMostNutrientAbleToGo(gameMessage, spore, positionsSortedNutrient);
        if (ableToGo.isEmpty()) {
            System.out.println("Defaulting to highest value : " + positionsSortedNutrient.getFirst().position.toString());
            return new SporeMoveToAction(spore.id(), positionsSortedNutrient.getFirst().position);
        }
        PathFinder.State nextPos = ableToGo.getFirst().getFirst();
        System.out.println("Going to highest reachable value : " + nextPos.toString());
        return new SporeMoveToAction(spore.id(), new Position(nextPos.x, nextPos.y));
    }

    public int distanceSporePosition(Spore spore, Position position) {
        return Math.abs(spore.position().x() - position.x()) + Math.abs(spore.position().y() - position.y());
    }

    public List<PathFinder.State> shortestPathRealCost(TeamGameState gameState, Position start, Position going) {
        return PathFinder.shortestPath(start, going, gameState);
    }
}
