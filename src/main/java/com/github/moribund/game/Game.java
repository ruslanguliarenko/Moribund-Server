package com.github.moribund.game;

import com.github.moribund.GraphicalConstants;
import com.github.moribund.MoribundServer;
import com.github.moribund.net.packets.OutgoingPacket;
import com.github.moribund.objects.nonplayable.GroundItem;
import com.github.moribund.objects.nonplayable.ItemType;
import com.github.moribund.objects.playable.PlayableCharacter;
import com.github.moribund.objects.playable.Player;
import com.github.moribund.utils.ArtificialTime;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.*;
import org.quartz.*;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Game {
    @Getter
    private final int gameId;
    private final Int2ObjectMap<PlayableCharacter> players;
    @Getter(value = AccessLevel.PACKAGE)
    private final Queue<OutgoingPacket> outgoingPacketsQueue;
    @Getter
    private final ObjectSet<GroundItem> groundItems;
    @Getter @Setter(AccessLevel.PACKAGE)
    private boolean started;
    @Getter
    private boolean finished;

    Game(int gameId) {
        this.gameId = gameId;
        players = new Int2ObjectOpenHashMap<>();
        groundItems = new ObjectArraySet<>();
        outgoingPacketsQueue = new LinkedList<>();
        started = false;
    }


    void sendPacketToEveryoneUsingUDP(OutgoingPacket outgoingPacket) {
        players.forEach((playerId, player) -> player.getConnection().sendUDP(outgoingPacket));
    }

    /**
     * Sends an object, or a packet, to all the {@link Game#players}.
     * @param outgoingPacket The outgoing packet to send everyone.
     */
    void sendPacketToEveryoneUsingTCP(OutgoingPacket outgoingPacket) {
        players.forEach((playerId, player) -> player.getConnection().sendTCP(outgoingPacket));
    }

    public void queuePacket(OutgoingPacket outgoingPacket) {
        outgoingPacketsQueue.add(outgoingPacket);
    }

    void emptyQueue() {
        outgoingPacketsQueue.clear();
    }

    public void addPlayer(int playerId, Player player) {
        int countBefore = players.size();
        players.putIfAbsent(playerId, player);
        if (!started && countBefore == GameContainer.MINIMUM_PLAYERS - 1) {
            startCountdownForGame();
        }
    }

    private void startCountdownForGame() {
        try {
            val repetitionTime = 1;
            val scheduledTime = SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(repetitionTime)
                    .withRepeatCount(GameContainer.COUNTDOWN_TIME);
            val counterDataMap = new JobDataMap();
            counterDataMap.put("counter", new ArtificialTime(GameContainer.COUNTDOWN_TIME));
            val gameTimerJobDetail = JobBuilder.newJob(GameStartJob.class)
                    .withIdentity("gameStartJob" + gameId)
                    .usingJobData("gameId", gameId)
                    .usingJobData(counterDataMap)
                    .build();
            var trigger = TriggerBuilder.newTrigger().withIdentity("gameStart" + gameId).withSchedule(scheduledTime).build();

            MoribundServer.getInstance().getScheduler().start();
            MoribundServer.getInstance().getScheduler().scheduleJob(gameTimerJobDetail, trigger);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public void removePlayer(int playerId) {
        players.remove(playerId);
        if (players.size() == 1 && started) {
            sendVictoryRoyale();
            endGame();
        } else if (players.size() == 0 && started) {
            MoribundServer.getInstance().getGameContainer().removeGame(gameId);
        }
    }

    private void sendVictoryRoyale() {
        
    }

    public void forEachPlayer(Consumer<PlayableCharacter> playerConsumer) {
        players.values().forEach(playerConsumer);
    }

    public void forEachPlayer(BiConsumer<Integer, PlayableCharacter> playerConsumer) {
        players.forEach(playerConsumer);
    }

    public PlayableCharacter getPlayableCharacter(int playerId) {
        return players.get(playerId);
    }

    int getPlayerAmount() {
        return players.size();
    }

    boolean containsPlayer(int playerId) {
        return players.containsKey(playerId);
    }

    void setup() {
        val itemsOnGround = ThreadLocalRandom.current().nextInt(30, 40);
        for (int i = 0; i < itemsOnGround; i++) {
            val itemType = ItemType.random();
            val x = (float) ThreadLocalRandom.current().nextDouble(GraphicalConstants.MINIMUM_X, GraphicalConstants.MAXIMUM_X);
            val y = (float) ThreadLocalRandom.current().nextDouble(GraphicalConstants.MINIMUM_Y, GraphicalConstants.MAXIMUM_Y);
            val groundItem = new GroundItem(itemType, x, y);
            groundItems.add(groundItem);
        }
    }

    public GroundItem getGroundItem(int id, float x, float y) {
        for (GroundItem groundItem : groundItems) {
            if (groundItem.matches(id, x, y)) {
                return groundItem;
            }
        }
        return null;
    }

    public void removeGroundItem(GroundItem groundItem) {
        groundItems.remove(groundItem);
    }

    void endGame() {
        try {
            MoribundServer.getInstance().getScheduler().deleteJob(new JobKey("gameTimer" + gameId));
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        started = false;
        finished = true;
    }
}
