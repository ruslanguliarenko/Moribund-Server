package com.github.moribund.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.github.moribund.MoribundServer;
import com.github.moribund.entity.PlayableCharacter;
import com.github.moribund.entity.Player;
import com.github.moribund.net.packets.account.DrawNewPlayerPacket;
import com.github.moribund.net.packets.account.LoginPacket;
import com.github.moribund.net.packets.account.LoginRequestPacket;
import javafx.util.Pair;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The {@code AccountListener} listens to all packets relating
 * to accounts (account creation, etc).
 */
class AccountListener extends Listener {
    @Override
    public void received(Connection connection, Object object) {
        if (object instanceof LoginRequestPacket) {
            createAccount(connection);
        }
    }

    /**
     * Creates a new account when a {@code LoginRequestPacket} is received. A new
     * {@link Player} is made, then a {@link LoginPacket} is sent to that newly
     * made player and a {@link DrawNewPlayerPacket} is sent to all existing players.
     * @param connection The connection of the new account requesting to be
     *                   made.
     */
    private void createAccount(Connection connection) {
        val playerId = connection.getID();
        val player = createNewPlayer(playerId, connection);

        sendNewPlayerPacket(player);
        sendPlayersToNewPlayer(player);
    }

    /**
     * Sends the {@link LoginPacket} to the newly made player. An important thing
     * to note is that this sends a list of players that includes the newly made player
     * him/her self.
     * @param player The newly made {@link Player}.
     */
    private void sendPlayersToNewPlayer(PlayableCharacter player) {
        // note this includes the newly made player
        val playersMap = MoribundServer.getInstance().getPlayers();
        List<Pair<Integer, Pair<Float, Float>>> playerTiles = new ArrayList<>();
        List<Pair<Integer, Float>> playerRotations = new ArrayList<>();
        playersMap.forEach((playerId, aPlayer) -> {
            playerTiles.add(new Pair<>(playerId, new Pair<>(aPlayer.getX(), aPlayer.getY())));
            playerRotations.add(new Pair<>(playerId, aPlayer.getRotation()));
        });

        val loginPacket = new LoginPacket(player.getPlayerId(), playerTiles, playerRotations);
        player.getConnection().sendUDP(loginPacket);
    }

    /**
     * Sends a {@link DrawNewPlayerPacket} to all the existing {@link Player}s in the game.
     * @param newPlayer The newly made {@link Player}.
     */
    private void sendNewPlayerPacket(PlayableCharacter newPlayer) {
        val playersMap = MoribundServer.getInstance().getPlayers();
        val newPlayerLoginPacket = new DrawNewPlayerPacket(newPlayer.getPlayerId(), newPlayer.getX(), newPlayer.getY(), newPlayer.getRotation());
        playersMap.forEach((playerId, player) -> player.getConnection().sendUDP(newPlayerLoginPacket));
    }

    /**
     * Makes a new {@link Player} using the player ID that is generated by the
     * {@link Connection} and the {@link Connection} itself.
     * @param playerId The player ID of the newly made player.
     * @param connection The connection of the newly made player.
     * @return The newly made {@link Player}.
     */
    private Player createNewPlayer(int playerId, Connection connection) {
        val player = new Player(playerId, ThreadLocalRandom.current().nextInt(0, 100),
                ThreadLocalRandom.current().nextInt(0, 100));
        player.setConnection(connection);
        val playersMap = MoribundServer.getInstance().getPlayers();
        playersMap.putIfAbsent(playerId, player);
        return player;
    }
}