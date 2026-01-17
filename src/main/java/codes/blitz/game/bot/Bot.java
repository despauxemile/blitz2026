package codes.blitz.game.bot;

import codes.blitz.game.generated.*;

import java.util.*;

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
        };
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

    public String getIdSporeFurtherFromOtherTeam() {
        return null;
    }

    public List<Action> determineActionAllSpore(TeamGameState gameMessage) {
        List<Action> actions = new ArrayList<>();
        TeamInfo myTeam = gameMessage.world().teamInfos().get(gameMessage.yourTeamId());
        for (Spore spore : myTeam.spores()) {
          actions.add(determineSporeAction(spore));
        }
        return actions;
    }

    public Action determineSporeAction(Spore spore) {
        return new SporeMoveToAction(spore.id(), new Position(0, 0));
    }
}
