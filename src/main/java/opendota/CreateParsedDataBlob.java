package opendota;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

class ParsedData {
    public Integer version = 22;
    public Long match_id = 0L;
    public List<Teamfight> teamfights = new ArrayList<>();
    public List<Pause> pauses = new ArrayList<>();
    public List<Entry> objectives = new ArrayList<>();
    public List<Entry> chat = new ArrayList<>();
    public List<Integer> radiant_gold_adv = new ArrayList<>();
    public List<Integer> radiant_xp_adv = new ArrayList<>();
    public Map<String, Integer> cosmetics = new HashMap<>();
    public List<Object> draft_timings = new ArrayList<>();
    public List<PlayerData> players = new ArrayList<>();

    public ParsedData() {
        for (int i = 0; i < 10; i++) {
            this.players.add(new PlayerData());
        }
    }
}

class PlayerData {
    public Integer player_slot = 0;
    public Integer obs_placed = 0;
    public Integer sen_placed = 0;
    public Integer creeps_stacked = 0;
    public Integer camps_stacked = 0;
    public Integer rune_pickups = 0;
    public Integer firstblood_claimed = 0;
    public Float teamfight_participation = 0.0f;
    public Integer towers_killed = 0;
    public Integer roshans_killed = 0;
    public Integer observers_placed = 0;
    public Float stuns = 0.0f;
    public Map<String, Integer> performance_others;
    public Entry max_hero_hit = new Entry();
    public List<Integer> times = new ArrayList<>();
    public List<Integer> gold_t = new ArrayList<>();
    public List<Integer> lh_t = new ArrayList<>();
    public List<Integer> dn_t = new ArrayList<>();
    public List<Integer> xp_t = new ArrayList<>();
    public List<Entry> obs_log = new ArrayList<>();
    public List<Entry> sen_log = new ArrayList<>();
    public List<Entry> obs_left_log = new ArrayList<>();
    public List<Entry> sen_left_log = new ArrayList<>();
    public List<Map<String, Object>> purchase_log = new ArrayList<>();
    public List<Map<String, Object>> kills_log = new ArrayList<>();
    public List<Entry> buyback_log = new ArrayList<>();
    public List<Map<String, Object>> runes_log = new ArrayList<>();
    public List<Entry> connection_log = new ArrayList<>();
    public Map<String, HashMap<String, Integer>> lane_pos = new HashMap<>();
    public Map<String, HashMap<String, Integer>> obs = new HashMap<>();
    public Map<String, HashMap<String, Integer>> sen = new HashMap<>();
    public Map<String, Integer> actions = new HashMap<>();
    public Map<String, Integer> pings = new HashMap<>();
    public Map<String, Integer> purchase = new HashMap<>();
    public Map<String, Integer> gold_reasons = new HashMap<>();
    public Map<String, Integer> xp_reasons = new HashMap<>();
    public Map<String, Integer> killed = new HashMap<>();
    public Map<String, Integer> item_uses = new HashMap<>();
    public Map<String, Integer> ability_uses = new HashMap<>();
    public Map<String, HashMap<String, Integer>> ability_targets = new HashMap<>();
    public Map<String, HashMap<String, Integer>> damage_targets = new HashMap<>();
    public Map<String, Integer> hero_hits = new HashMap<>();
    public Map<String, Integer> damage = new HashMap<>();
    public Map<String, Integer> damage_taken = new HashMap<>();
    public Map<String, Integer> damage_inflictor = new HashMap<>();
    public Map<String, Integer> runes = new HashMap<>();
    public Map<String, Integer> killed_by = new HashMap<>();
    public Map<String, Integer> kill_streaks = new HashMap<>();
    public Map<String, Integer> multi_kills = new HashMap<>();
    public Map<String, Integer> life_state = new HashMap<>();
    public Map<String, Integer> healing = new HashMap<>();
    public Map<String, Integer> damage_inflictor_received = new HashMap<>();
    public Boolean randomed = false;
    public Boolean repicked = false;
    public Boolean pred_vict = false;
    public List<Map<String, Object>> neutral_tokens_log = new ArrayList<>();
    public List<Map<String, Object>> neutral_item_history = new ArrayList<>();
}

class Metadata {
    public HashMap<String, Integer> hero_to_slot = new HashMap<>();
    public HashMap<Integer, Integer> slot_to_player_slot = new HashMap<Integer, Integer>();
    public HashMap<Integer, Integer> hero_id_to_slot = new HashMap<Integer, Integer>();
    public HashMap<String, HashMap<String, Integer>> ability_levels = new HashMap<String, HashMap<String, Integer>>();
}

class Pause {
    public Integer time;
    public Integer duration;
}

class AllPlayersResult {
    public List<Integer> radiantGoldAdv;
    public List<Integer> radiantXpAdv;
}

class Teamfight {
    public Integer start;
    public Integer end;
    public Integer last_death;
    public Integer deaths;
    public List<TeamfightPlayer> players = new ArrayList<TeamfightPlayer>();

    public Teamfight() {
        for (int i = 0; i < 10; i++) {
            this.players.add(new TeamfightPlayer());
        }
    }
}

class TeamfightPlayer {
    public HashMap<String, HashMap<String, Integer>> deaths_pos = new HashMap<String, HashMap<String, Integer>>();
    public HashMap<String, Integer> ability_uses = new HashMap<>();
    public HashMap<String, Integer> ability_targets = new HashMap<>();
    public HashMap<String, Integer> item_uses = new HashMap<>();
    public HashMap<String, Integer> killed = new HashMap<>();
    public Integer deaths = 0;
    public Integer buybacks = 0;
    public Integer damage = 0;
    public Integer healing = 0;
    public Integer gold_delta = 0;
    public Integer xp_delta = 0;
    public Integer xp_start;
    public Integer xp_end;
}

public class CreateParsedDataBlob {

    private Gson g = new Gson();

    public ParsedData createParsedDataBlob(List<Entry> entries) {
        long tStart = System.currentTimeMillis();
        Metadata meta = processMetadata(entries);
        long tEnd = System.currentTimeMillis();
        System.err.format("metadata: %sms\n", tEnd - tStart);

        tStart = System.currentTimeMillis();
        List<Entry> expanded = processExpand(entries, meta);
        tEnd = System.currentTimeMillis();
        System.err.format("expand: %sms\n", tEnd - tStart);

        tStart = System.currentTimeMillis();
        ParsedData parsedData = processParsedData(expanded, new ParsedData(), meta);
        tEnd = System.currentTimeMillis();
        System.err.format("populate: %sms\n", tEnd - tStart);

        tStart = System.currentTimeMillis();
        parsedData.teamfights = processTeamfights(expanded, meta);
        tEnd = System.currentTimeMillis();
        System.err.format("teamfights: %sms\n", tEnd - tStart);

        tStart = System.currentTimeMillis();
        parsedData.pauses = processPauses(entries);
        tEnd = System.currentTimeMillis();
        System.err.format("pauses: %sms\n", tEnd - tStart);

        tStart = System.currentTimeMillis();
        AllPlayersResult ap = processAllPlayers(entries, meta);
        parsedData.radiant_gold_adv = ap.radiantGoldAdv;
        parsedData.radiant_xp_adv = ap.radiantXpAdv;
        tEnd = System.currentTimeMillis();
        System.err.format("processAllPlayers: %sms\n", tEnd - tStart);

        return parsedData;
    }

    private void greevilsGreed(Entry e, ParsedData container, Metadata meta) {
        if ("killed".equals(e.type) && e.greevils_greed_stack != null) {
            String alchName = "npc_dota_hero_alchemist";
            Integer alchSlot = meta.hero_to_slot.get(alchName);
            if (alchSlot != null) {
                PlayerData alchPlayer = container.players.get(alchSlot);
                int goldBase = 3;
                int goldStack = e.greevils_greed_stack * 3;
                goldStack = Math.min(goldStack, 18);

                if (alchPlayer.performance_others == null) {
                    alchPlayer.performance_others = new HashMap<>();
                }
                alchPlayer.performance_others.put("greevils_greed_gold",
                        alchPlayer.performance_others.getOrDefault("greevils_greed_gold", 0) + goldBase + goldStack);
            }
        }
    }

    private void performanceOthers(Entry e, ParsedData container, Metadata meta) {
        greevilsGreed(e, container, meta);
    }

    private void populate(Entry e, ParsedData container, Metadata meta) {
        switch (e.type) {
            case "interval":
                break;
            case "player_slot":
                container.players.get(Integer.valueOf(e.key)).player_slot = e.value;
                break;
            case "chat":
            case "chatwheel":
                container.chat.add(deepCopy(e, Entry.class));
                break;
            case "cosmetics":
                Type mapType = new TypeToken<Map<String, Integer>>() {
                }.getType();
                container.cosmetics = g.fromJson(e.key, mapType);
                break;
            case "CHAT_MESSAGE_FIRSTBLOOD":
            case "CHAT_MESSAGE_COURIER_LOST":
            case "CHAT_MESSAGE_AEGIS":
            case "CHAT_MESSAGE_AEGIS_STOLEN":
            case "CHAT_MESSAGE_DENIED_AEGIS":
            case "CHAT_MESSAGE_ROSHAN_KILL":
            case "CHAT_MESSAGE_MINIBOSS_KILL":
            case "building_kill":
                container.objectives.add(deepCopy(e, Entry.class));
                break;
            case "ability_levels":
                if (!meta.ability_levels.containsKey(e.unit)) {
                    meta.ability_levels.put(e.unit, new HashMap<>());
                }
                meta.ability_levels.get(e.unit).put(e.key, e.level);
                break;
            default:
                if (e.slot == null || e.slot < 0 || e.slot >= container.players.size()) {
                    return;
                }
                PlayerData player = container.players.get(e.slot);

                if (e.posData != null && e.posData) {
                    handlePosData(e, player);
                } else if (e.max != null && e.max) {
                    handleMaxValue(e, player);
                } else if ("ability_targets".equals(e.type)) {
                    handleAbilityTargets(e, player);
                } else if ("damage_targets".equals(e.type)) {
                    handleDamageTargets(e, player);
                } else if ("stuns".equals(e.type)) {
                    player.stuns = e.floatValue;
                } else if ("obs_placed".equals(e.type)) {
                    player.obs_placed = e.value;
                } else if ("sen_placed".equals(e.type)) {
                    player.sen_placed = e.value;
                } else if ("creeps_stacked".equals(e.type)) {
                    player.creeps_stacked = e.value;
                } else if ("camps_stacked".equals(e.type)) {
                    player.camps_stacked = e.value;
                } else if ("rune_pickups".equals(e.type)) {
                    player.rune_pickups = e.value;
                } else if ("randomed".equals(e.type)) {
                    player.randomed = e.booleanValue;
                } else if ("repicked".equals(e.type)) {
                    player.repicked = e.booleanValue;
                } else if ("pred_vict".equals(e.type)) {
                    player.pred_vict = e.booleanValue;
                } else if ("firstblood_claimed".equals(e.type)) {
                    player.firstblood_claimed = e.value;
                } else if ("teamfight_participation".equals(e.type)) {
                    player.teamfight_participation = e.floatValue;
                } else if ("towers_killed".equals(e.type)) {
                    player.towers_killed = e.value;
                } else if ("roshans_killed".equals(e.type)) {
                    player.roshans_killed = e.value;
                } else if ("observers_placed".equals(e.type)) {
                    player.observers_placed = e.value;
                } else if (isArrayField(e.type)) {
                    handleArrayField(e, player);
                } else if (isMapField(e.type)) {
                    handleMapField(e, player, container, meta);
                } else {
                    throw new RuntimeException("populate: unhandled entry type " + e.type);
                }
                break;
        }
    }

    private void handlePosData(Entry e, PlayerData player) {
        Map<String, HashMap<String, Integer>> targetMap = getPlayerPosData(player, e.type);
        int[] coords = g.fromJson(e.key, int[].class);
        String x = String.valueOf(coords[0]);
        String y = String.valueOf(coords[1]);
        HashMap<String, Integer> targetX = targetMap.get(x);
        if (targetX == null) {
            targetMap.put(x, new HashMap<>());
        }
        targetMap.get(x).put(y, targetMap.get(x).getOrDefault(y, 0) + 1);
    }

    private void handleMaxValue(Entry e, PlayerData player) {
        if (player.max_hero_hit.value == null || e.value > player.max_hero_hit.value) {
            player.max_hero_hit = e;
        }
    }

    private void handleArrayField(Entry e, PlayerData player) {
        if ("neutral_item_history".equals(e.type)) {
            handleNeutralItemHistory(e, player.neutral_item_history);
        } else if (e.interval != null && e.interval) {
            List<Integer> targetList = getPlayerIntegerList(player, e.type);
            targetList.add(e.value);
        } else if (Arrays.asList("purchase_log", "kills_log", "runes_log", "neutral_tokens_log").contains(e.type)) {
            Map<String, Object> obj = new HashMap<>();
            obj.put("time", e.time);
            obj.put("key", e.key);

            if ("purchase_log".equals(e.type)) {
                int maxCharges = "tango".equals(e.key) ? 3 : 1;
                if (e.charges != null && e.charges > maxCharges) {
                    obj.put("charges", e.charges);
                }
            }

            if ("kills_log".equals(e.type) && e.tracked_death != null) {
                obj.put("tracked_death", e.tracked_death);
                obj.put("tracked_sourcename", e.tracked_sourcename);
            }

            if ("purchase_log".equals(e.type)) {
                player.purchase_log.add(obj);
            } else if ("kills_log".equals(e.type)) {
                player.kills_log.add(obj);
            } else if ("runes_log".equals(e.type)) {
                player.runes_log.add(obj);
            } else if ("neutral_tokens_log".equals(e.type)) {
                player.neutral_tokens_log.add(obj);
            }
        } else {
            List<Entry> targetList = getPlayerEntryList(player, e.type);
            targetList.add(shallowCopy(e));
        }
    }

    private void handleNeutralItemHistory(Entry e, List<Map<String, Object>> targetList) {
        String itemName = e.key.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("__", "_");
        if (itemName.startsWith("_")) {
            itemName = itemName.substring(1);
        }
        if ("enhancement_timelss".equals(itemName)) {
            itemName = "enhancement_timeless";
        }

        Map<String, Object> existing = null;
        for (Map<String, Object> obj : targetList) {
            if (e.time.equals(obj.get("time"))) {
                existing = obj;
                break;
            }
        }

        if (existing == null) {
            existing = new HashMap<>();
            existing.put("time", e.time);
            targetList.add(existing);
        }

        if (e.isNeutralActiveDrop != null && e.isNeutralActiveDrop) {
            existing.put("item_neutral", itemName);
        }
        if (e.isNeutralPassiveDrop != null && e.isNeutralPassiveDrop) {
            existing.put("item_neutral_enhancement", itemName);
        }
    }

    private void handleAbilityTargets(Entry e, PlayerData player) {
        String[] keyParts = g.fromJson(e.key, String[].class);
        String ability = keyParts[0];
        String target = keyParts[1];

        if (!player.ability_targets.containsKey(ability)) {
            player.ability_targets.put(ability, new HashMap<>());
        }
        Map<String, Integer> targets = player.ability_targets.get(ability);
        targets.put(target, targets.getOrDefault(target, 0) + 1);
    }

    private void handleDamageTargets(Entry e, PlayerData player) {
        String[] keyParts = g.fromJson(e.key, String[].class);
        String ability = keyParts[0];
        String target = keyParts[1];

        if (!player.damage_targets.containsKey(ability)) {
            player.damage_targets.put(ability, new HashMap<>());
        }
        Map<String, Integer> targets = player.damage_targets.get(ability);
        targets.put(target, targets.getOrDefault(target, 0) + e.value);
    }

    private void handleMapField(Entry e, PlayerData player, ParsedData container, Metadata meta) {
        Map<String, Integer> targetMap = getPlayerMap(player, e.type);
        int value = e.value != null ? e.value : 1;
        targetMap.put(e.key, targetMap.getOrDefault(e.key, 0) + value);
        performanceOthers(e, container, meta);
    }

    private AllPlayersResult processAllPlayers(List<Entry> entries, Metadata meta) {
        Map<Integer, Integer> goldAdvTime = new HashMap<>();
        Map<Integer, Integer> xpAdvTime = new HashMap<>();

        for (Entry e : entries) {
            if (e.time >= 0 && e.time % 60 == 0 && "interval".equals(e.type)) {
                Integer playerSlot = meta.slot_to_player_slot.get(e.slot);
                if (playerSlot != null) {
                    int g = isRadiant(playerSlot) ? e.gold : -e.gold;
                    int x = isRadiant(playerSlot) ? e.xp : -e.xp;

                    goldAdvTime.put(e.time, goldAdvTime.getOrDefault(e.time, 0) + g);
                    xpAdvTime.put(e.time, xpAdvTime.getOrDefault(e.time, 0) + x);
                }
            }
        }

        List<Integer> radiantGoldAdv = new ArrayList<>();
        List<Integer> radiantXpAdv = new ArrayList<>();

        List<Integer> sortedKeys = new ArrayList<>(goldAdvTime.keySet());
        Collections.sort(sortedKeys);

        for (Integer key : sortedKeys) {
            radiantGoldAdv.add(goldAdvTime.get(key));
            radiantXpAdv.add(xpAdvTime.get(key));
        }

        AllPlayersResult result = new AllPlayersResult();
        result.radiantGoldAdv = radiantGoldAdv;
        result.radiantXpAdv = radiantXpAdv;

        return result;
    }

    private boolean isRadiant(int playerSlot) {
        return playerSlot < 128;
    }

    private String translate(String input) {
        if ("dota_unknown".equals(input)) {
            return null;
        }
        if (input != null && input.startsWith("item_")) {
            return input.substring(5);
        }
        return input;
    }

    private String computeIllusionString(String input, Boolean isIllusion) {
        return (isIllusion != null && isIllusion ? "illusion_" : "") + input;
    }

    private List<Entry> processExpand(List<Entry> entries, Metadata meta) {
        List<Entry> output = new ArrayList<>();
        Integer aegisHolder = null;
        Integer aegisDeathTime = null;

        for (Entry e : entries) {
            String type = e.type;

            switch (type) {
                case "DOTA_COMBATLOG_DAMAGE":
                    handleDamageCombat(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_HEAL":
                    handleHeal(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_MODIFIER_ADD":
                    if ("modifier_aegis_regen".equals(e.inflictor)) {
                        aegisHolder = null;
                    }
                    break;
                case "DOTA_COMBATLOG_DEATH":
                    Integer[] result = handleDeathCombat(e, output, meta, aegisHolder, aegisDeathTime);
                    aegisHolder = result[0];
                    aegisDeathTime = result[1];
                    break;
                case "DOTA_COMBATLOG_ABILITY":
                    handleAbility(e, output, meta);
                    break;
                case "DOTA_ABILITY_LEVEL":
                    handleAbilityLevel(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_ITEM":
                    handleItemUse(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_GOLD":
                    handleGold(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_XP":
                    handleXp(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_PURCHASE":
                    handlePurchase(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_BUYBACK":
                    handleBuyback(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_MULTIKILL":
                    handleMultikill(e, output, meta);
                    break;
                case "DOTA_COMBATLOG_KILLSTREAK":
                    handleKillstreak(e, output, meta);
                    break;
                case "pings":
                    handlePings(e, output, meta);
                    break;
                case "actions":
                    handleActions(e, output, meta);
                    break;
                case "CHAT_MESSAGE_RUNE_PICKUP":
                    handleRunePickup(e, output, meta);
                    break;
                case "CHAT_MESSAGE_RECONNECT":
                    handleReconnect(e, output, meta);
                    break;
                case "CHAT_MESSAGE_DISCONNECT_WAIT_FOR_RECONNECT":
                    handleDisconnect(e, output, meta);
                    break;
                case "CHAT_MESSAGE_FIRSTBLOOD":
                    handleFirstblood(e, output, meta);
                    break;
                case "CHAT_MESSAGE_AEGIS":
                    aegisHolder = e.player1;
                    handleAegis(e, output, meta);
                    break;
                case "CHAT_MESSAGE_AEGIS_STOLEN":
                    aegisHolder = e.player1;
                    handleAegisStolen(e, output, meta);
                    break;
                case "CHAT_MESSAGE_DENIED_AEGIS":
                    handleAegisDenied(e, output, meta);
                    break;
                case "CHAT_MESSAGE_ROSHAN_KILL":
                    handleRoshanKill(e, output, meta);
                    break;
                case "CHAT_MESSAGE_MINIBOSS_KILL":
                    handleMinibossKill(e, output, meta);
                    break;
                case "CHAT_MESSAGE_COURIER_LOST":
                    handleCourierLost(e, output, meta);
                    break;
                case "chat":
                    expand(e, output, meta);
                    break;
                case "chatwheel":
                    expand(e, output, meta);
                    break;
                case "interval":
                    handleInterval(e, output, meta);
                    break;
                case "obs":
                    handleObs(e, output, meta);
                    break;
                case "sen":
                    handleSen(e, output, meta);
                    break;
                case "obs_left":
                    handleObsLeft(e, output, meta);
                    break;
                case "sen_left":
                    handleSenLeft(e, output, meta);
                    break;
                case "epilogue":
                case "player_slot":
                case "cosmetics":
                    expand(e, output, meta);
                    break;
                case "neutral_token":
                    handleNeutralToken(e, output, meta);
                    break;
                case "neutral_item_history":
                    expand(e, output, meta);
                    break;
                default:
                    // System.err.println("expand: (WARNING) new entry type " + e.type);
                    break;
            }
        }

        return output;
    }

    private void handleDamageCombat(Entry e, List<Entry> output, Metadata meta) {
        String unit = e.sourcename;
        String key = computeIllusionString(e.targetname, e.targetillusion);
        String inflictor = translate(e.inflictor);

        Entry damage = shallowCopy(e);
        damage.time = e.time;
        damage.value = e.value;
        damage.unit = unit;
        damage.key = key;
        damage.type = "damage";
        expand(damage, output, meta);

        if (e.targethero != null && e.targethero &&
                (e.targetillusion == null || !e.targetillusion)) {

            Entry damageTaken = new Entry();
            damageTaken.time = e.time;
            damageTaken.value = e.value;
            damageTaken.unit = key;
            damageTaken.key = unit;
            damageTaken.type = "damage_taken";
            expand(damageTaken, output, meta);

            Entry damageTargets = new Entry();
            damageTargets.value = e.value;
            damageTargets.unit = unit;
            damageTargets.key = "[\"" + inflictor + "\",\"" + translate(e.targetname) + "\"]";
            damageTargets.type = "damage_targets";
            expand(damageTargets, output, meta);

            Entry heroHits = new Entry();
            heroHits.time = e.time;
            heroHits.value = 1;
            heroHits.unit = unit;
            heroHits.key = inflictor;
            heroHits.type = "hero_hits";
            expand(heroHits, output, meta);

            if (!key.equals(unit)) {
                Entry damageInflictor = new Entry();
                damageInflictor.time = e.time;
                damageInflictor.value = e.value;
                damageInflictor.unit = unit;
                damageInflictor.key = inflictor;
                damageInflictor.type = "damage_inflictor";
                expand(damageInflictor, output, meta);

                Entry maxHeroHit = new Entry();
                maxHeroHit.type = "max_hero_hit";
                maxHeroHit.time = e.time;
                maxHeroHit.max = true;
                maxHeroHit.inflictor = inflictor;
                maxHeroHit.unit = unit;
                maxHeroHit.key = key;
                maxHeroHit.value = e.value;
                expand(maxHeroHit, output, meta);

                if (e.sourcename != null && e.sourcename.contains("npc_dota_hero_")) {
                    Entry damageInflictorReceived = new Entry();
                    damageInflictorReceived.time = e.time;
                    damageInflictorReceived.value = e.value;
                    damageInflictorReceived.type = "damage_inflictor_received";
                    damageInflictorReceived.unit = key;
                    damageInflictorReceived.key = inflictor;
                    expand(damageInflictorReceived, output, meta);
                }
            }
        }
    }

    private void handleHeal(Entry e, List<Entry> output, Metadata meta) {
        Entry heal = shallowCopy(e);
        heal.time = e.time;
        heal.value = e.value;
        heal.unit = e.sourcename;
        heal.key = computeIllusionString(e.targetname, e.targetillusion);
        heal.type = "healing";
        expand(heal, output, meta);
    }

    private Integer[] handleDeathCombat(Entry e, List<Entry> output, Metadata meta,
            Integer aegisHolder, Integer aegisDeathTime) {
        String unit = e.sourcename;
        String key = computeIllusionString(e.targetname, e.targetillusion);

        if (e.targetname != null && (e.targetname.contains("_tower") ||
                e.targetname.contains("_rax_") || e.targetname.contains("_healers") ||
                e.targetname.contains("_fort"))) {
            Entry buildingKill = new Entry();
            buildingKill.time = e.time;
            buildingKill.type = "building_kill";
            buildingKill.unit = unit;
            buildingKill.key = key;
            expand(buildingKill, output, meta);
        }

        Integer slotForKey = meta.hero_to_slot.get(key);
        if (slotForKey != null && slotForKey.equals(aegisHolder)) {
            if (aegisDeathTime == null) {
                aegisDeathTime = e.time;
                return new Integer[] { aegisHolder, aegisDeathTime };
            }
            if (!aegisDeathTime.equals(e.time)) {
                aegisDeathTime = null;
                aegisHolder = null;
            } else {
                return new Integer[] { aegisHolder, aegisDeathTime };
            }
        }

        if (e.attackername != null && e.attackername.equals(key)) {
            return new Integer[] { aegisHolder, aegisDeathTime };
        }

        if (e.targethero != null && e.targethero &&
                (e.targetillusion == null || !e.targetillusion)) {

            Entry killLog = new Entry();
            killLog.time = e.time;
            killLog.unit = unit;
            killLog.key = key;
            killLog.tracked_death = e.tracked_death;
            killLog.tracked_sourcename = e.tracked_sourcename;
            killLog.type = "kills_log";
            expand(killLog, output, meta);

            Entry killedBy = new Entry();
            killedBy.time = e.time;
            killedBy.unit = key;
            killedBy.key = unit;
            killedBy.type = "killed_by";
            expand(killedBy, output, meta);
        }

        Entry killed = shallowCopy(e);
        killed.time = e.time;
        killed.unit = unit;
        killed.key = key;
        killed.type = "killed";
        killed.value = 1;
        killed.tracked_death = e.tracked_death;
        killed.tracked_sourcename = e.tracked_sourcename;
        killed.greevils_greed_stack = e.greevils_greed_stack;
        expand(killed, output, meta);

        return new Integer[] { aegisHolder, aegisDeathTime };
    }

    private void handleAbility(Entry e, List<Entry> output, Metadata meta) {
        Entry abilityUse = new Entry();
        abilityUse.time = e.time;
        abilityUse.unit = e.attackername;
        abilityUse.key = translate(e.inflictor);
        abilityUse.type = "ability_uses";
        expand(abilityUse, output, meta);

        if (e.targethero != null && e.targethero &&
                (e.targetillusion == null || !e.targetillusion)) {
            Entry abilityTarget = new Entry();
            abilityTarget.time = e.time;
            abilityTarget.unit = e.attackername;
            abilityTarget.key = "[\"" + translate(e.inflictor) + "\",\"" + translate(e.targetname) + "\"]";
            abilityTarget.type = "ability_targets";
            expand(abilityTarget, output, meta);
        }
    }

    private void handleAbilityLevel(Entry e, List<Entry> output, Metadata meta) {
        Entry abilityLevel = new Entry();
        abilityLevel.time = e.time;
        abilityLevel.unit = e.targetname;
        abilityLevel.level = e.abilitylevel;
        abilityLevel.key = translate(e.valuename);
        abilityLevel.type = "ability_levels";
        expand(abilityLevel, output, meta);
    }

    private void handleItemUse(Entry e, List<Entry> output, Metadata meta) {
        Entry itemUse = new Entry();
        itemUse.time = e.time;
        itemUse.unit = e.attackername;
        itemUse.key = translate(e.inflictor);
        itemUse.type = "item_uses";
        expand(itemUse, output, meta);
    }

    private void handleGold(Entry e, List<Entry> output, Metadata meta) {
        Entry goldReason = new Entry();
        goldReason.time = e.time;
        goldReason.value = e.value;
        goldReason.unit = e.targetname;
        goldReason.key = String.valueOf(e.gold_reason);
        goldReason.type = "gold_reasons";
        expand(goldReason, output, meta);
    }

    private void handleXp(Entry e, List<Entry> output, Metadata meta) {
        Entry xpReason = new Entry();
        xpReason.time = e.time;
        xpReason.value = e.value;
        xpReason.unit = e.targetname;
        xpReason.key = String.valueOf(e.xp_reason);
        xpReason.type = "xp_reasons";
        expand(xpReason, output, meta);
    }

    private void handlePurchase(Entry e, List<Entry> output, Metadata meta) {
        String unit = e.targetname;
        String key = translate(e.valuename);

        Entry purchase = new Entry();
        purchase.time = e.time;
        purchase.value = 1;
        purchase.unit = unit;
        purchase.key = key;
        purchase.charges = e.charges;
        purchase.type = "purchase";
        expand(purchase, output, meta);

        if (key != null && !key.startsWith("recipe_")) {
            Entry purchaseLog = new Entry();
            purchaseLog.time = e.time;
            purchaseLog.value = 1;
            purchaseLog.unit = unit;
            purchaseLog.key = key;
            purchaseLog.charges = e.charges;
            purchaseLog.type = "purchase_log";
            expand(purchaseLog, output, meta);
        }
    }

    private void handleBuyback(Entry e, List<Entry> output, Metadata meta) {
        Entry buyback = new Entry();
        buyback.time = e.time;
        buyback.slot = e.value;
        buyback.type = "buyback_log";
        expand(buyback, output, meta);
    }

    private void handleMultikill(Entry e, List<Entry> output, Metadata meta) {
        Entry multikill = new Entry();
        multikill.time = e.time;
        multikill.value = 1;
        multikill.unit = e.attackername;
        multikill.key = String.valueOf(e.value);
        multikill.type = "multi_kills";
        expand(multikill, output, meta);
    }

    private void handleKillstreak(Entry e, List<Entry> output, Metadata meta) {
        Entry killstreak = new Entry();
        killstreak.time = e.time;
        killstreak.value = 1;
        killstreak.unit = e.attackername;
        killstreak.key = String.valueOf(e.value);
        killstreak.type = "kill_streaks";
        expand(killstreak, output, meta);
    }

    private void handlePings(Entry e, List<Entry> output, Metadata meta) {
        Entry ping = new Entry();
        ping.time = e.time;
        ping.type = "pings";
        ping.slot = e.slot;
        ping.key = "0";
        expand(ping, output, meta);
    }

    private void handleActions(Entry e, List<Entry> output, Metadata meta) {
        Entry action = new Entry();
        action.time = e.time;
        action.type = e.type;
        action.slot = e.slot;
        action.key = e.key;
        action.value = 1;
        expand(action, output, meta);
    }

    private void handleRunePickup(Entry e, List<Entry> output, Metadata meta) {
        Entry rune = new Entry();
        rune.time = e.time;
        rune.value = 1;
        rune.type = "runes";
        rune.slot = e.player1;
        rune.key = String.valueOf(e.value);
        expand(rune, output, meta);

        Entry runeLog = new Entry();
        runeLog.time = e.time;
        runeLog.key = String.valueOf(e.value);
        runeLog.slot = e.player1;
        runeLog.type = "runes_log";
        expand(runeLog, output, meta);
    }

    private void handleReconnect(Entry e, List<Entry> output, Metadata meta) {
        Entry reconnect = new Entry();
        reconnect.time = e.time;
        reconnect.type = "connection_log";
        reconnect.slot = e.player1;
        reconnect.event = "reconnect";
        expand(reconnect, output, meta);
    }

    private void handleDisconnect(Entry e, List<Entry> output, Metadata meta) {
        Entry disconnect = new Entry();
        disconnect.time = e.time;
        disconnect.type = "connection_log";
        disconnect.slot = e.player1;
        disconnect.event = "disconnect";
        expand(disconnect, output, meta);
    }

    private void handleFirstblood(Entry e, List<Entry> output, Metadata meta) {
        Entry firstblood = new Entry();
        firstblood.time = e.time;
        firstblood.type = e.type;
        firstblood.slot = e.player1;
        firstblood.key = String.valueOf(e.player2);
        expand(firstblood, output, meta);
    }

    private void handleAegis(Entry e, List<Entry> output, Metadata meta) {
        Entry aegis = new Entry();
        aegis.time = e.time;
        aegis.type = e.type;
        aegis.slot = e.player1;
        expand(aegis, output, meta);
    }

    private void handleAegisStolen(Entry e, List<Entry> output, Metadata meta) {
        Entry aegisStolen = new Entry();
        aegisStolen.time = e.time;
        aegisStolen.type = e.type;
        aegisStolen.slot = e.player1;
        expand(aegisStolen, output, meta);
    }

    private void handleAegisDenied(Entry e, List<Entry> output, Metadata meta) {
        Entry aegisDenied = new Entry();
        aegisDenied.time = e.time;
        aegisDenied.type = e.type;
        aegisDenied.slot = e.player1;
        expand(aegisDenied, output, meta);
    }

    private void handleRoshanKill(Entry e, List<Entry> output, Metadata meta) {
        Entry roshanKill = new Entry();
        roshanKill.time = e.time;
        roshanKill.type = e.type;
        roshanKill.team = e.player1;
        expand(roshanKill, output, meta);
    }

    private void handleMinibossKill(Entry e, List<Entry> output, Metadata meta) {
        Entry minibossKill = new Entry();
        minibossKill.time = e.time;
        minibossKill.type = e.type;
        minibossKill.slot = e.player1;
        minibossKill.team = e.value;
        expand(minibossKill, output, meta);
    }

    private void handleCourierLost(Entry e, List<Entry> output, Metadata meta) {
        Integer team;
        Integer killer;

        if (e.player2 == null) {
            team = e.player1;
            killer = null;
        } else {
            team = e.player2;
            if (e.player1 != null && e.player1 > -1) {
                killer = meta.slot_to_player_slot.get(e.player1);
            } else if (e.player1 != null && e.player1 == -1) {
                killer = -1;
            } else {
                killer = null;
            }
        }

        Entry courierLost = new Entry();
        courierLost.time = e.time;
        courierLost.type = e.type;
        courierLost.value = e.value;
        courierLost.killer = killer;
        courierLost.team = team;
        expand(courierLost, output, meta);
    }

    private void handleObs(Entry e, List<Entry> output, Metadata meta) {
        if (e.x != null && e.y != null) {
            Entry obs = shallowCopy(e);
            obs.time = e.time;
            obs.slot = e.slot;
            obs.key = e.key;
            obs.x = e.x;
            obs.y = e.y;
            obs.type = "obs";
            obs.posData = true;
            expand(obs, output, meta);
        }

        Entry obsLog = shallowCopy(e);
        obsLog.time = e.time;
        obsLog.slot = e.slot;
        obsLog.key = e.key;
        obsLog.type = "obs_log";
        expand(obsLog, output, meta);
    }

    private void handleSen(Entry e, List<Entry> output, Metadata meta) {
        if (e.x != null && e.y != null) {
            Entry sen = shallowCopy(e);
            sen.time = e.time;
            sen.slot = e.slot;
            sen.key = e.key;
            sen.x = e.x;
            sen.y = e.y;
            sen.type = "sen";
            sen.posData = true;
            expand(sen, output, meta);
        }

        Entry senLog = shallowCopy(e);
        senLog.time = e.time;
        senLog.slot = e.slot;
        senLog.key = e.key;
        senLog.type = "sen_log";
        expand(senLog, output, meta);
    }

    private void handleObsLeft(Entry e, List<Entry> output, Metadata meta) {
        Entry obsLeft = shallowCopy(e);
        obsLeft.time = e.time;
        obsLeft.slot = e.slot;
        obsLeft.key = e.key;
        obsLeft.type = "obs_left_log";
        expand(obsLeft, output, meta);
    }

    private void handleSenLeft(Entry e, List<Entry> output, Metadata meta) {
        Entry senLeft = shallowCopy(e);
        senLeft.time = e.time;
        senLeft.slot = e.slot;
        senLeft.key = e.key;
        senLeft.type = "sen_left_log";
        expand(senLeft, output, meta);
    }

    private void handleNeutralToken(Entry e, List<Entry> output, Metadata meta) {
        Entry neutralToken = shallowCopy(e);
        neutralToken.time = e.time;
        neutralToken.slot = e.slot;
        neutralToken.key = e.key;
        neutralToken.type = "neutral_tokens_log";
        expand(neutralToken, output, meta);
    }

    private void handleInterval(Entry e, List<Entry> output, Metadata meta) {
        if (e.time >= 0) {
            expand(e, output, meta);

            String[] fields = { "stuns", "life_state", "obs_placed", "sen_placed",
                    "creeps_stacked", "camps_stacked", "rune_pickups", "randomed",
                    "repicked", "pred_vict", "firstblood_claimed", "teamfight_participation",
                    "towers_killed", "roshans_killed", "observers_placed" };

            for (String field : fields) {
                Entry fieldEntry = new Entry();
                fieldEntry.time = e.time;
                fieldEntry.slot = e.slot;
                fieldEntry.type = field;
                fieldEntry.key = field;

                switch (field) {
                    case "life_state":
                        fieldEntry.key = String.valueOf(e.life_state);
                        fieldEntry.value = 1;
                        break;
                    case "stuns":
                        fieldEntry.floatValue = e.stuns;
                        break;
                    case "obs_placed":
                        fieldEntry.value = e.obs_placed;
                        break;
                    case "sen_placed":
                        fieldEntry.value = e.sen_placed;
                        break;
                    case "creeps_stacked":
                        fieldEntry.value = e.creeps_stacked;
                        break;
                    case "camps_stacked":
                        fieldEntry.value = e.camps_stacked;
                        break;
                    case "rune_pickups":
                        fieldEntry.value = e.rune_pickups;
                        break;
                    case "randomed":
                        fieldEntry.booleanValue = e.randomed;
                        break;
                    case "repicked":
                        fieldEntry.booleanValue = e.repicked;
                        break;
                    case "pred_vict":
                        fieldEntry.booleanValue = e.pred_vict;
                        break;
                    case "firstblood_claimed":
                        fieldEntry.value = e.firstblood_claimed;
                        break;
                    case "teamfight_participation":
                        fieldEntry.floatValue = e.teamfight_participation;
                        break;
                    case "towers_killed":
                        fieldEntry.value = e.towers_killed;
                        break;
                    case "roshans_killed":
                        fieldEntry.value = e.roshans_killed;
                        break;
                    case "observers_placed":
                        fieldEntry.value = e.observers_placed;
                        break;
                    default:
                        throw new RuntimeException("expand: unhandled interval field " + field);
                }

                expand(fieldEntry, output, meta);
            }

            if (e.time % 60 == 0) {
                addIntervalData(e, output, meta, "times", e.time);
                addIntervalData(e, output, meta, "gold_t", e.gold);
                addIntervalData(e, output, meta, "xp_t", e.xp);
                addIntervalData(e, output, meta, "lh_t", e.lh);
                addIntervalData(e, output, meta, "dn_t", e.denies);
            }
        }

        if (e.time <= 600 && e.x != null && e.y != null) {
            Entry lanePos = new Entry();
            lanePos.time = e.time;
            lanePos.slot = e.slot;
            lanePos.type = "lane_pos";
            lanePos.key = "[" + Math.round(e.x) + "," + Math.round(e.y) + "]";
            lanePos.posData = true;
            expand(lanePos, output, meta);
        }
    }

    private void addIntervalData(Entry e, List<Entry> output, Metadata meta,
            String type, Integer value) {
        Entry interval = new Entry();
        interval.time = e.time;
        interval.slot = e.slot;
        interval.interval = true;
        interval.type = type;
        interval.value = value;
        expand(interval, output, meta);
    }

    private void expand(Entry e, List<Entry> output, Metadata meta) {
        if (e.x != null) {
            e.x = Math.round(e.x * 10.0) / 10.0f;
        }
        if (e.y != null) {
            e.y = Math.round(e.y * 10.0) / 10.0f;
        }
        if (e.z != null) {
            e.z = Math.round(e.z * 10.0) / 10.0f;
        }

        if (e.key == null && e.x != null && e.y != null) {
            e.key = "[" + Math.round(e.x) + "," + Math.round(e.y) + "]";
        }

        Integer slot = e.slot != null ? e.slot : meta.hero_to_slot.get(e.unit);
        Integer playerSlot = slot != null ? meta.slot_to_player_slot.get(slot) : null;

        Entry expanded = shallowCopy(e);
        expanded.slot = slot;
        expanded.player_slot = playerSlot;
        output.add(expanded);
    }

    private Metadata processMetadata(List<Entry> entries) {
        Metadata meta = new Metadata();

        for (Entry e : entries) {
            if ("interval".equals(e.type)) {
                if (e.hero_id != null) {
                    String ending = e.unit.substring("CDOTA_Unit_Hero_".length());
                    String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                    String combatLogName2 = "npc_dota_hero" +
                            ending.replaceAll("([A-Z])", "_$1").toLowerCase();

                    meta.hero_to_slot.put(combatLogName, e.slot);
                    meta.hero_to_slot.put(combatLogName2, e.slot);
                    meta.hero_id_to_slot.put(e.hero_id, e.slot);
                }
            } else if ("player_slot".equals(e.type)) {
                meta.slot_to_player_slot.put(Integer.valueOf(e.key), e.value);
            }
        }

        return meta;
    }

    private ParsedData processParsedData(List<Entry> entries, ParsedData container, Metadata meta) {
        for (Entry e : entries) {
            populate(e, container, meta);
        }
        return container;
    }

    private List<Pause> processPauses(List<Entry> entries) {
        List<Pause> pauses = new ArrayList<>();

        for (Entry e : entries) {
            if ("game_paused".equals(e.type) &&
                    "pause_duration".equals(e.key) &&
                    e.value != null && e.value > 0) {

                Pause pause = new Pause();
                pause.time = e.time;
                pause.duration = e.value;
                pauses.add(pause);
            }
        }

        return pauses;
    }

    private List<Teamfight> processTeamfights(List<Entry> entries, Metadata meta) {
        Teamfight currTeamfight = null;
        List<Teamfight> teamfights = new ArrayList<>();
        Map<Integer, Map<Integer, Entry>> intervalState = new HashMap<>();
        int teamfightCooldown = 15;
        Map<String, Integer> heroToSlot = meta.hero_to_slot;

        for (Entry e : entries) {
            if ("killed".equals(e.type) && e.targethero != null && e.targethero && e.targetillusion != null
                    && !e.targetillusion) {
                if (currTeamfight == null) {
                    currTeamfight = new Teamfight();
                    currTeamfight.start = e.time - teamfightCooldown;
                    currTeamfight.end = null;
                    currTeamfight.last_death = e.time;
                    currTeamfight.deaths = 0;
                    currTeamfight.players = new ArrayList<>();
                    for (int i = 0; i < 10; i++) {
                        currTeamfight.players.add(new TeamfightPlayer());
                    }
                }

                currTeamfight.last_death = e.time;
                currTeamfight.deaths += 1;

            } else if ("interval".equals(e.type)) {
                if (!intervalState.containsKey(e.time)) {
                    intervalState.put(e.time, new HashMap<>());
                }
                intervalState.get(e.time).put(e.slot, e);

                if (currTeamfight != null && e.time - currTeamfight.last_death >= teamfightCooldown) {
                    currTeamfight.end = e.time;
                    teamfights.add(deepCopy(currTeamfight, Teamfight.class));
                    currTeamfight = null;
                }
            }
        }

        teamfights = teamfights.stream()
                .filter(tf -> tf.deaths >= 3)
                .collect(Collectors.toList());

        for (Teamfight tf : teamfights) {
            for (int ind = 0; ind < tf.players.size(); ind++) {
                if (intervalState.containsKey(tf.start) && intervalState.get(tf.start).containsKey(ind) &&
                        intervalState.containsKey(tf.end) && intervalState.get(tf.end).containsKey(ind)) {
                    tf.players.get(ind).xp_start = intervalState.get(tf.start).get(ind).xp;
                    tf.players.get(ind).xp_end = intervalState.get(tf.end).get(ind).xp;
                }
            }
        }

        for (Entry e : entries) {
            for (Teamfight tf : teamfights) {
                if (e.time >= tf.start && e.time <= tf.end) {
                    if ("killed".equals(e.type) && e.targethero != null && e.targethero &&
                            (e.targetillusion == null || !e.targetillusion)) {

                        populateTeamfight(e, tf, meta);

                        Integer slot = heroToSlot.get(e.key);
                        if (slot != null && intervalState.containsKey(e.time) &&
                                intervalState.get(e.time).containsKey(slot)) {

                            Entry intervalEntry = intervalState.get(e.time).get(slot);
                            if (intervalEntry.x != null && intervalEntry.y != null) {
                                Entry deathPos = new Entry();
                                deathPos.time = e.time;
                                deathPos.slot = slot;
                                deathPos.type = "deaths_pos";
                                deathPos.key = "[" + Math.round(intervalEntry.x) + "," + Math.round(intervalEntry.y)
                                        + "]";
                                deathPos.posData = true;
                                populateTeamfight(deathPos, tf, meta);

                                tf.players.get(slot).deaths += 1;
                            }
                        }

                    } else if ("buyback_log".equals(e.type)) {
                        if (e.slot != null && e.slot < tf.players.size()) {
                            tf.players.get(e.slot).buybacks += 1;
                        }

                    } else if ("damage".equals(e.type)) {
                        if (e.targethero != null && e.targethero &&
                                (e.targetillusion == null || !e.targetillusion)) {
                            if (e.slot != null && e.slot < tf.players.size()) {
                                tf.players.get(e.slot).damage += e.value;
                            }
                        }

                    } else if ("healing".equals(e.type)) {
                        if (e.targethero != null && e.targethero &&
                                (e.targetillusion == null || !e.targetillusion)) {
                            if (e.slot != null && e.slot < tf.players.size()) {
                                tf.players.get(e.slot).healing += e.value;
                            }
                        }

                    } else if ("gold_reasons".equals(e.type) || "xp_reasons".equals(e.type)) {
                        if (e.slot != null && e.slot < tf.players.size()) {
                            if ("gold_reasons".equals(e.type)) {
                                tf.players.get(e.slot).gold_delta += e.value;
                            } else {
                                tf.players.get(e.slot).xp_delta += e.value;
                            }
                        }

                    } else if ("ability_uses".equals(e.type) || "item_uses".equals(e.type)) {
                        if (e.player_slot != null) {
                            int computedSlot = e.player_slot % (128 - 5);
                            if (computedSlot < tf.players.size()) {
                                TeamfightPlayer tfPlayer = tf.players.get(computedSlot);
                                if ("ability_uses".equals(e.type)) {
                                    tfPlayer.ability_uses.put(e.key,
                                            tfPlayer.ability_uses.getOrDefault(e.key, 0) + 1);
                                } else {
                                    tfPlayer.item_uses.put(e.key,
                                            tfPlayer.item_uses.getOrDefault(e.key, 0) + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        return teamfights;
    }

    private void populateTeamfight(Entry e, Teamfight container, Metadata meta) {
        switch (e.type) {
            case "killed":
                if (e.slot != null && e.slot < container.players.size()) {
                    TeamfightPlayer player = container.players.get(e.slot);
                    player.killed.put(e.key, player.killed.getOrDefault(e.key, 0) + 1);
                }
                break;
            case "deaths_pos":
                if (e.slot != null && e.slot < container.players.size()) {
                    handlePosDataTeamfight(e, container.players.get(e.slot));
                }
                break;
            default:
                throw new RuntimeException("populateTeamfight: unhandled type " + e.type);
        }
    }

    private void handlePosDataTeamfight(Entry e, TeamfightPlayer player) {
        int[] coords = g.fromJson(e.key, int[].class);
        int x = coords[0];
        int y = coords[1];

        if (!player.deaths_pos.containsKey(String.valueOf(x))) {
            player.deaths_pos.put(String.valueOf(x), new HashMap<>());
        }
        Map<String, Integer> yMap = player.deaths_pos.get(String.valueOf(x));
        yMap.put(String.valueOf(y), yMap.getOrDefault(String.valueOf(y), 0) + 1);
    }

    // Helper methods
    private boolean isArrayField(String type) {
        return Arrays.asList("times", "gold_t", "lh_t", "dn_t", "xp_t", "obs_log",
                "sen_log", "obs_left_log", "sen_left_log", "purchase_log", "kills_log",
                "buyback_log", "runes_log", "connection_log", "neutral_tokens_log",
                "neutral_item_history").contains(type);
    }

    private boolean isMapField(String type) {
        return Arrays.asList("obs", "sen", "actions", "pings", "purchase",
                "gold_reasons", "xp_reasons", "killed", "item_uses", "ability_uses",
                "damage", "damage_taken", "damage_inflictor", "runes", "killed_by",
                "kill_streaks", "multi_kills", "life_state", "healing",
                "damage_inflictor_received", "hero_hits").contains(type);
    }

    private Map<String, HashMap<String, Integer>> getPlayerPosData(PlayerData player, String type) {
        switch (type) {
            case "lane_pos":
                return player.lane_pos;
            case "obs":
                return player.obs;
            case "sen":
                return player.sen;
            default:
                throw new RuntimeException("missing pos type " + type);
        }
    }

    private List<Integer> getPlayerIntegerList(PlayerData player, String type) {
        switch (type) {
            case "times":
                return player.times;
            case "gold_t":
                return player.gold_t;
            case "lh_t":
                return player.lh_t;
            case "dn_t":
                return player.dn_t;
            case "xp_t":
                return player.xp_t;
            default:
                throw new RuntimeException("missing list type " + type);
        }
    }

    private List<Entry> getPlayerEntryList(PlayerData player, String type) {
        switch (type) {
            case "obs_log":
                return player.obs_log;
            case "sen_log":
                return player.sen_log;
            case "obs_left_log":
                return player.obs_left_log;
            case "sen_left_log":
                return player.sen_left_log;
            case "buyback_log":
                return player.buyback_log;
            case "connection_log":
                return player.connection_log;
            default:
                throw new RuntimeException("missing list type " + type);
        }
    }

    private Map<String, Integer> getPlayerMap(PlayerData player, String type) {
        switch (type) {
            case "actions":
                return player.actions;
            case "pings":
                return player.pings;
            case "purchase":
                return player.purchase;
            case "gold_reasons":
                return player.gold_reasons;
            case "xp_reasons":
                return player.xp_reasons;
            case "killed":
                return player.killed;
            case "item_uses":
                return player.item_uses;
            case "ability_uses":
                return player.ability_uses;
            case "damage":
                return player.damage;
            case "damage_taken":
                return player.damage_taken;
            case "damage_inflictor":
                return player.damage_inflictor;
            case "runes":
                return player.runes;
            case "killed_by":
                return player.killed_by;
            case "kill_streaks":
                return player.kill_streaks;
            case "multi_kills":
                return player.multi_kills;
            case "life_state":
                return player.life_state;
            case "healing":
                return player.healing;
            case "damage_inflictor_received":
                return player.damage_inflictor_received;
            case "hero_hits":
                return player.hero_hits;
            default:
                throw new RuntimeException("missing map type " + type);
        }
    }

    public Entry shallowCopy(Entry entry) {
        try {
            return (Entry) entry.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public <T> T deepCopy(T anObject, Class<T> classInfo) {
        // try {
        String text = g.toJson(anObject);
        T newObject = g.fromJson(text, classInfo);
        return newObject;
        // } catch (IllegalArgumentException iex) {
        // System.err.println(iex);
        // GsonBuilder builder = new GsonBuilder();
        // builder.serializeSpecialFloatingPointValues();
        // Gson gson = builder.create();
        // String text = gson.toJson(anObject);
        // System.err.println(text);
        // T newObject = gson.fromJson(text, classInfo);
        // return newObject;
        // }
    }
}

// public UploadProps processUploadProps(List<Entry> entries) {
// UploadProps container = new UploadProps();
// container.playerMap = new HashMap<>();

// for (Entry e : entries) {
// switch (e.type) {
// case "epilogue":
// try {
// JsonNode json = mapper.readTree(e.key);
// JsonNode dota = json.get("gameInfo_").get("dota_");
// container.matchId = dota.get("matchId_").asLong();
// container.gameMode = dota.get("gameMode_").asInt();
// container.radiantWin = dota.get("gameWinner_").asInt() == 2;
// } catch (Exception ex) {
// ex.printStackTrace();
// }
// break;
// case "interval":
// if (!container.playerMap.containsKey(e.playerSlot)) {
// container.playerMap.put(e.playerSlot, new UploadPlayerData());
// }
// UploadPlayerData playerData = container.playerMap.get(e.playerSlot);
// playerData.heroId = e.heroId;
// playerData.level = e.level;
// playerData.kills = e.kills;
// playerData.deaths = e.deaths;
// playerData.assists = e.assists;
// playerData.denies = e.denies;
// playerData.lastHits = e.lh;
// playerData.gold = e.gold;
// playerData.xp = e.xp;
// break;
// }
// }

// return container;
// }