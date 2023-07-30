package mod.surviving_the_aftermath.event;


import com.google.common.collect.Maps;
import mod.surviving_the_aftermath.init.ModCapability;
import mod.surviving_the_aftermath.init.ModMobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.compress.utils.Lists;

import java.util.*;

public class PlayerBattleTrackerEventSubscriber {
    public static final String PLAYER_BATTLE_PERSONAL_FAIL = "message.surviving_the_aftermath.nether_raid.personal_fail";
    public static final String PLAYER_BATTLE_ESCAPE = "message.surviving_the_aftermath.nether_raid.escape";
    private static final int MAX_DEATH_COUNT = 3;
    private List<UUID> players = Lists.newArrayList();
    private Map<UUID,Integer> deathMap = Maps.newHashMap();
    private Map<UUID,Long> escapeMap = Maps.newHashMap();
    private Map<UUID, List<UUID>> spectatorMap = new HashMap<>();

    @SubscribeEvent
    public void updatePlayer(RaidEvent.Ongoing event) {
        ServerLevel level = event.getLevel();
        List<UUID> uuids = new ArrayList<>();
        List<UUID> eventPlayers = event.getPlayers();
        for (UUID uuid : players) {
            if (!eventPlayers.contains(uuid)) {
                uuids.add(uuid);
            }
        }
        for (UUID uuid : uuids) {
            players.remove(uuid);
            Player player = level.getPlayerByUUID(uuid);
            if (player != null){
                escapeMap.put(uuid,level.getGameTime());
            }else {
                deathMap.put(uuid, deathMap.getOrDefault(uuid,0) + 1);
            }
        }

        for (UUID uuid : eventPlayers) {
            if (!players.contains(uuid)) {
                Player player = level.getPlayerByUUID(uuid);
                if (player instanceof ServerPlayer serverPlayer && serverPlayer.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    if (escapeMap.containsKey(uuid)) {
                        escapeMap.remove(uuid);
                    }
                    players.add(uuid);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;
        if (player instanceof ServerPlayer serverPlayer) {
            if (deathMap.containsKey(serverPlayer.getUUID())) {
                if (players.size() > 0){
                    player.displayClientMessage(Component.translatable(PLAYER_BATTLE_PERSONAL_FAIL), true);
                    setSpectator(serverPlayer, level);
                    deathMap.remove(serverPlayer.getUUID());
                    return;
                }

                Integer deathCount = deathMap.get(serverPlayer.getUUID());
                if (deathCount < MAX_DEATH_COUNT && players.size() == 0) {
                    //TODO:游戏结束
                }
            }
        }
    }

    @SubscribeEvent
    public void onTickPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = player.level();
        UUID uuid = player.getUUID();
        if (level.isClientSide()) return;
        if (escapeMap.containsKey(uuid)){
            Long lastEscapeTime = escapeMap.get(uuid);
            long time = level.getGameTime() - lastEscapeTime;
            if (lastEscapeTime != 0L && time > 20 * 5) {
                player.addEffect(new MobEffectInstance(ModMobEffects.COWARDICE.get(), 45 * 60 * 20));
                escapeMap.remove(uuid);
            }else {
                player.displayClientMessage(Component.translatable(PLAYER_BATTLE_ESCAPE, 20 * 5 - time), true);
            }
        }
    }

    private void setSpectator(ServerPlayer player,Level level) {
        player.setGameMode(GameType.SPECTATOR);
        UUID uuid = players.get(level.random.nextInt(players.size()));
        Player target = level.getPlayerByUUID(uuid);
        if (!(target instanceof ServerPlayer serverTarget)) return;


        if (spectatorMap.containsKey(player.getUUID())){
            List<UUID> uuids = spectatorMap.get(serverTarget.getUUID());
            uuids.add(player.getUUID());
            uuids.forEach(uuid1 -> {
                Player player1 = level.getPlayerByUUID(uuid1);
                if (player1 instanceof ServerPlayer serverPlayer){
                    if (!spectatorMap.containsKey(serverTarget.getUUID())) {
                        spectatorMap.put(serverTarget.getUUID(), Lists.newArrayList());
                    }
                    spectatorMap.get(serverTarget.getUUID()).add(serverPlayer.getUUID());
                    serverPlayer.setCamera(serverTarget);
                }
            });
            spectatorMap.remove(player.getUUID());
        } else {
            spectatorMap.put(serverTarget.getUUID(), Lists.newArrayList());
            spectatorMap.get(serverTarget.getUUID()).add(player.getUUID());
            player.setCamera(serverTarget);
        }
    }
}
