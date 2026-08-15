package org.sobadfish.bedwar.listener;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.item.Item;
import me.coolmagic233.parties.Parties;
import me.coolmagic233.parties.api.PartiesAPI;
import me.coolmagic233.parties.event.PartyJoinRoomEvent;
import org.sobadfish.bedwar.BedWarMain;
import org.sobadfish.bedwar.item.button.TeamChoseItem;
import org.sobadfish.bedwar.item.button.RoomQuitItem;
import org.sobadfish.bedwar.manager.RoomManager;
import org.sobadfish.bedwar.player.PlayerInfo;
import org.sobadfish.bedwar.player.team.TeamInfo;
import org.sobadfish.bedwar.room.GameRoom;
import org.sobadfish.bedwar.room.GameRoom.GameType;
import org.sobadfish.bedwar.room.config.GameRoomConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyListener implements Listener {

    private static final ConcurrentHashMap<UUID, Boolean> processing = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, PendingParty> pendingParties = new ConcurrentHashMap<>();

    private static class PendingParty {
        final String roomId;
        final UUID partyId;
        final UUID leaderUuid;
        final String serverName;

        PendingParty(String roomId, UUID partyId, UUID leaderUuid, String serverName) {
            this.roomId = roomId;
            this.partyId = partyId;
            this.leaderUuid = leaderUuid;
            this.serverName = serverName;
        }
    }

    @EventHandler
    public void onPartyJoinRoom(PartyJoinRoomEvent event) {
        UUID partyId = event.getParty().getId();
        if (processing.putIfAbsent(partyId, Boolean.TRUE) != null) {
            BedWarMain.sendMessageToConsole("&e[PartyDebug] duplicate event partyId=" + partyId + ", skipping");
            return;
        }
        try {
            onPartyJoinRoom0(event, partyId);
        } finally {
            processing.remove(partyId);
        }
    }

    private void onPartyJoinRoom0(PartyJoinRoomEvent event, UUID partyId) {
        String roomId = event.getRoomId();
        RoomManager rm = BedWarMain.getRoomManager();
        BedWarMain.sendMessageToConsole("&b[PartyDebug] partyId=" + partyId + " roomId=" + roomId + " serverName=" + event.getServerName());

        if (pendingParties.containsKey(partyId)) {
            BedWarMain.sendMessageToConsole("&a[PartyDebug] party already pending, skipping duplicate event");
            return;
        }

        if (!rm.hasRoom(roomId)) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] hasRoom=false, roomId=" + roomId);
            transferPartyAway(partyId, event.getServerName());
            return;
        }
        GameRoom room = rm.getRoom(roomId);
        if (room == null) {
            BedWarMain.sendMessageToConsole("&e[PartyDebug] room=null, trying enableRoom...");
            if (!rm.enableRoom(rm.getRoomConfig(roomId))) {
                BedWarMain.sendMessageToConsole("&c[PartyDebug] enableRoom failed for roomId=" + roomId);
                transferPartyAway(partyId, event.getServerName());
                return;
            }
            room = rm.getRoom(roomId);
            if (room == null) {
                BedWarMain.sendMessageToConsole("&c[PartyDebug] room still null after enable for roomId=" + roomId);
                transferPartyAway(partyId, event.getServerName());
                return;
            }
        }

        List<UUID> allUuids = new ArrayList<>(new HashSet<>(event.getMembers()));
        allUuids.add(event.getLeader());
        allUuids = new ArrayList<>(new HashSet<>(allUuids));

        BedWarMain.sendMessageToConsole("&b[PartyDebug] uniquePartySize=" + allUuids.size() + " members=" + allUuids);

        if (allUuids.isEmpty()) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] allUuids is empty");
            return;
        }

        if (room.getType() != GameType.WAIT) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] room type=" + room.getType() + " is not WAIT");
            transferPartyAway(partyId, event.getServerName());
            return;
        }

        List<UUID> needJoin = new ArrayList<>();
        for (UUID uuid : allUuids) {
            Player onlinePlayer = Server.getInstance().getPlayer(uuid).orElse(null);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                PlayerInfo existingInfo = rm.getPlayerInfo(onlinePlayer);
                if (existingInfo != null && existingInfo.getGameRoom() != null) {
                    if (existingInfo.getGameRoom().getRoomConfig().name.equals(roomId)) {
                        BedWarMain.sendMessageToConsole("&a[PartyDebug] player " + onlinePlayer.getName() + " already in target room, skipping");
                        continue;
                    } else {
                        BedWarMain.sendMessageToConsole("&c[PartyDebug] player " + onlinePlayer.getName() + " already in different room: " + existingInfo.getGameRoom().getRoomConfig().name);
                        transferPartyAway(partyId, event.getServerName());
                        return;
                    }
                }
            }
            needJoin.add(uuid);
        }

        BedWarMain.sendMessageToConsole("&b[PartyDebug] needJoin=" + needJoin.size() + " players need to join");

        if (needJoin.isEmpty()) {
            BedWarMain.sendMessageToConsole("&a[PartyDebug] all players already in room, nothing to do");
            return;
        }

        int maxPerTeam = room.getRoomConfig().getMaxPlayerSize() / room.getTeamInfos().size();

        int availableSlots = room.getRoomConfig().getMaxPlayerSize() - room.getPlayerInfos().size();
        BedWarMain.sendMessageToConsole("&b[PartyDebug] maxPerTeam=" + maxPerTeam + " needJoinSize=" + needJoin.size() + " availableSlots=" + availableSlots + " currentPlayers=" + room.getPlayerInfos().size() + " maxPlayers=" + room.getRoomConfig().getMaxPlayerSize());
        if (availableSlots < needJoin.size()) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] not enough slots: available=" + availableSlots + " needed=" + needJoin.size());
            transferPartyAway(partyId, event.getServerName());
            return;
        }

        TeamInfo targetTeam = null;
        for (TeamInfo team : room.getTeamInfos()) {
            BedWarMain.sendMessageToConsole("&b[PartyDebug] team=" + team.getTeamConfig().getName() + " currentSize=" + team.getTeamPlayers().size() + " canFit=" + (maxPerTeam - team.getTeamPlayers().size()));
            if (team.getTeamPlayers().size() + needJoin.size() <= maxPerTeam) {
                targetTeam = team;
                break;
            }
        }

        if (targetTeam == null) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] no team can fit needJoinSize=" + needJoin.size() + " maxPerTeam=" + maxPerTeam);
            transferPartyAway(partyId, event.getServerName());
            return;
        }
        BedWarMain.sendMessageToConsole("&a[PartyDebug] selected team=" + targetTeam.getTeamConfig().getName());

        pendingParties.put(partyId, new PendingParty(roomId, partyId, event.getLeader(), event.getServerName()));
        for (UUID uuid : needJoin) {
            BedWarMain.sendMessageToConsole("&a[PartyDebug] stored pending join for " + uuid + " -> room=" + roomId);
        }
        BedWarMain.sendMessageToConsole("&a[PartyDebug] party validated, waiting for " + needJoin.size() + " players to connect");
    }

    public static boolean tryJoinPendingParty(Player player) {
        PartiesAPI api = Parties.getInstance().getApi();
        me.coolmagic233.parties.model.Party party = api.getPartyForPlayer(player.getUniqueId());
        if (party == null) {
            return false;
        }
        UUID partyId = party.getId();
        PendingParty pending = pendingParties.get(partyId);
        if (pending == null) {
            return false;
        }

        RoomManager rm = BedWarMain.getRoomManager();
        GameRoom room = rm.getRoom(pending.roomId);
        if (room == null) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] room gone on player join for " + player.getName());
            pendingParties.remove(partyId);
            return false;
        }

        if (room.getType() != GameType.WAIT) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] room no longer WAIT on player join for " + player.getName());
            pendingParties.remove(partyId);
            return false;
        }

        PlayerInfo existingInfo = rm.getPlayerInfo(player);
        if (existingInfo != null && existingInfo.getGameRoom() != null) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] player already in room on join: " + player.getName());
            return false;
        }

        PlayerInfo info = new PlayerInfo(player);
        boolean joined = rm.joinRoom(info, pending.roomId);
        if (!joined) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] joinRoom failed on player join for " + player.getName());
            return false;
        }

        PlayerInfo roomInfo = room.getPlayerInfo(player);
        if (roomInfo == null) {
            BedWarMain.sendMessageToConsole("&c[PartyDebug] roomInfo null after joinRoom for " + player.getName());
            return false;
        }

        int maxPerTeam = room.getRoomConfig().getMaxPlayerSize() / room.getTeamInfos().size();
        TeamInfo targetTeam = null;
        for (TeamInfo team : room.getTeamInfos()) {
            if (team.getTeamPlayers().size() < maxPerTeam) {
                targetTeam = team;
                break;
            }
        }
        if (targetTeam != null) {
            targetTeam.mjoin(roomInfo);
        }
        room.addPartyPlayer(roomInfo.getName());

        boolean isLeader = pending.leaderUuid.equals(player.getUniqueId());
        Server.getInstance().getScheduler().scheduleDelayedTask(BedWarMain.getBedWarMain(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (isLeader) {
                player.getInventory().setItem(TeamChoseItem.getIndex(), Item.get(0));
                player.getInventory().setItem(RoomQuitItem.getIndex(), RoomQuitItem.get());
            } else {
                player.getInventory().clearAll();
            }
        }, 5);
        BedWarMain.sendMessageToConsole("&a[PartyDebug] player " + player.getName() + " auto-joined room " + pending.roomId);
        return true;
    }

    public static void removePendingParty(UUID partyId) {
        pendingParties.remove(partyId);
    }

    private void transferPartyAway(UUID partyId, String serverName) {
        PartiesAPI api = Parties.getInstance().getApi();
        api.joinRoom(partyId, "server-1", serverName);
    }
}