package codes.blitz.game.bot;

import codes.blitz.game.generated.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Bot {
    Random random = new Random();

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
            actions.add(new SporeCreateSpawnerAction(myTeam.spores().getFirst().id()));
        } else if (myTeam.spores().isEmpty()) {
            actions.add(new SpawnerProduceSporeAction(myTeam.spawners().getFirst().id(), myTeam.nutrients()));
        }

        actions.addAll(determineActionAllSpore(gameMessage));
        return actions;
    }

    public boolean decideIfCreateSpawner(TeamGameState gameMessage) {
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        if (myTeam.nextSpawnerCost() <= myTeam.nutrients()) {
            return true;
        }
        ;
        return false;
    }

    public String getIdSporeFurtherFromOtherTeam() {
        return null;
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
    public List<Position> determineCellMostNutrient(TeamGameState gameMessage) {
        List<Position> positions = new ArrayList<>();
        int width = gameMessage.world().map().width();
        int height = gameMessage.world().map().height();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int liveValue = gameMessage.world().map().nutrientGrid()[i][j];
                if (positions.isEmpty()) {
                    positions.addFirst(new Position(i, j));
                } else {
                    for (int k = 0; k < positions.size(); k++) {
                        Position position = positions.get(k);
                        if (liveValue < gameMessage.world().map().nutrientGrid()[position.x()][position.y()]) {
                            positions.add(k, new Position(i, j));
                        }
                    }
                }
            }
        }
        return positions;
    }

    /**
     * Assume que la liste de position passée est triée
     * en fonction que le premier est la position avec le plus de nutriments
     */
    public List<Position> determineMostNutrientAbleToGo(Spore spore, List<Position> sortedNutrientPosition) {
        List<Position> positions = new ArrayList<>();
        for (Position position : sortedNutrientPosition) {
            int dist = distanceSporePosition(spore, position);
            if (dist <= spore.biomass()) {
                positions.add(position);
            }
        }
        return positions;
    }

    public Action determineSporeAction(TeamGameState gameMessage, Spore spore) {
        List<Position> positionsSortedNutrient = determineCellMostNutrient(gameMessage);
        List<Position> ableToGo = determineMostNutrientAbleToGo(spore, positionsSortedNutrient);
        return new SporeMoveToAction(spore.id(), ableToGo.getFirst());
    }

    public int distanceSporePosition(Spore spore, Position position) {
        return Math.abs(spore.position().x() - position.x()) + Math.abs(spore.position().y() - position.y());
    }
}
