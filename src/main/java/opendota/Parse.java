package opendota;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessage;
import skadistats.clarity.io.Util;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityEntered;
import skadistats.clarity.processor.entities.OnEntityLeft;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnMessage;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.InputStreamSource;
import skadistats.clarity.wire.shared.common.proto.CommonNetworkBaseTypes.CNETMsg_Tick;
import skadistats.clarity.wire.shared.demo.proto.Demo.CDemoFileInfo;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatEvent;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatMessage;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatWheel;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_LocationPing;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_SpectatorPlayerUnitOrders;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.DOTA_COMBATLOG_TYPES;
import skadistats.clarity.wire.dota.s2.proto.DOTAS2GcMessagesCommon.CMsgDOTAMatch;
import skadistats.clarity.wire.shared.s1.proto.S1UserMessages.CUserMsg_SayText2;
import skadistats.clarity.wire.shared.s2.proto.S2UserMessages.CUserMessageSayText2;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import opendota.combatlogvisitors.TrackVisitor;
import opendota.combatlogvisitors.GreevilsGreedVisitor;
import opendota.combatlogvisitors.TrackVisitor.TrackStatus;
import opendota.processors.warding.OnWardExpired;
import opendota.processors.warding.OnWardKilled;
import opendota.processors.warding.OnWardPlaced;

public class Parse {

    public class Entry {
        public Integer time = 0;
        public Integer ticks = 0;
        public Integer paused = 0;
        public Float oldTime;
        public String type;
        public Integer team;
        public String unit;
        public String key;
        public Integer value;
        public Integer slot;
        public Integer player_slot;
        // chat event fields
        public Integer player1;
        public Integer player2;
        // combat log fields
        public String attackername;
        public String targetname;
        public String sourcename;
        public String targetsourcename;
        public Boolean attackerhero;
        public Boolean targethero;
        public Boolean attackerillusion;
        public Boolean targetillusion;
        public Integer abilitylevel;
        public String inflictor;
        public Integer gold_reason;
        public Integer xp_reason;
        public String valuename;
        // public Float stun_duration;
        // public Float slow_duration;
        // entity fields
        public Integer gold;
        public Integer lh;
        public Integer xp;
        public Float x;
        public Float y;
        public Float z;
        public Float stuns;
        public Integer hero_id;
        public Integer variant;
        public transient List<Item> hero_inventory;
        public Integer itemslot;
        public Integer charges;
        public Integer secondary_charges;
        public Integer life_state;
        public Integer hp;
        public Integer level;
        public Integer kills;
        public Integer deaths;
        public Integer assists;
        public Integer denies;
        public Boolean entityleft;
        public Integer ehandle;
        public Integer obs_placed;
        public Integer sen_placed;
        public Integer creeps_stacked;
        public Integer camps_stacked;
        public Integer rune_pickups;
        public Boolean repicked;
        public Boolean randomed;
        public Boolean pred_vict;
        public Float stun_duration;
        public Float slow_duration;
        public Boolean tracked_death;
        public Integer greevils_greed_stack;
        public String tracked_sourcename;
        public Integer firstblood_claimed;
        public Float teamfight_participation;
        public Integer towers_killed;
        public Integer roshans_killed;
        public Integer observers_placed;
        public Integer draft_order;
        public Boolean pick;
        public Integer draft_active_team;
        public Integer draft_extime0;
        public Integer draft_extime1;
        public Integer networth;
        public Integer stage;

        public Entry() {
        }

        public Entry(Integer time) {
            this.time = time;
        }
    }

    private Float getPreciseLocation (Integer cell, Float vec) {
      return (cell*128.0f+vec)/128;
    }

    private class Item {
        String id;
        // Charges can be used to determine how many items are stacked together on
        // stackable items
        Integer slot;
        Integer num_charges;
        // item_ward_dispenser uses num_charges for observer wards
        // and num_secondary_charges for sentry wards count
        // and is considered not stackable
        Integer num_secondary_charges;
    }

    private class Ability {
        String id;
        Integer abilityLevel;

    }

    private class UnknownItemFoundException extends RuntimeException {
        public UnknownItemFoundException(String message) {
            super(message);
        }
    }

    private class UnknownAbilityFoundException extends RuntimeException {
        public UnknownAbilityFoundException(String message) {
            super(message);
        }
    }

    float INTERVAL = 1;
    float nextInterval = 0;
    Integer time = 0;
    Integer pause = 0;
    int numPlayers = 10;
    int[] validIndices = new int[numPlayers];
    boolean init = false;
    int gameStartTime = 0;
    boolean postGame = false; // true when ancient destroyed
    boolean epilogue = false;
    private Gson g = new Gson();
    HashMap<String, Integer> name_to_slot = new HashMap<String, Integer>();
    HashMap<String, Integer> abilities_tracking = new HashMap<String, Integer>();
    List<Ability> abilities;
    HashMap<Integer, Integer> slot_to_playerslot = new HashMap<Integer, Integer>();
    HashMap<Long, Integer> steamid_to_playerslot = new HashMap<Long, Integer>();
    HashMap<Integer, Integer> cosmeticsMap = new HashMap<Integer, Integer>();
    HashMap<Integer, Integer> dotaplusxpMap = new HashMap<Integer, Integer>(); // playerslot, xp
    HashMap<Integer, Integer> ward_ehandle_to_slot = new HashMap<Integer, Integer>();
    InputStream is = null;
    OutputStream os = null;
    private GreevilsGreedVisitor greevilsGreedVisitor;
    private TrackVisitor trackVisitor;
    private ArrayList<Boolean> isPlayerStartingItemsWritten;
    int pingCount = 0;
    private ArrayList<Entry> logBuffer = new ArrayList<Entry>();
    int serverTick = 0;
    int start = 0;

    // Draft stage variable
    boolean[] draftOrderProcessed = new boolean[24];
    int order = 1;
    boolean isDraftStartTimeProcessed = false; // flag to know if draft start time is already handled

    boolean isDotaPlusProcessed = false;

    public Parse(InputStream input, OutputStream output) throws IOException {
        greevilsGreedVisitor = new GreevilsGreedVisitor(name_to_slot);
        trackVisitor = new TrackVisitor();

        is = input;
        os = output;
        isPlayerStartingItemsWritten = new ArrayList<>(Arrays.asList(new Boolean[numPlayers]));
        Collections.fill(isPlayerStartingItemsWritten, Boolean.FALSE);
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new InputStreamSource(is)).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;
        System.err.format("total time taken: %s\n", (tMatch) / 1000.0);
    }

    public void output(Entry e) {
        try {
            if (!epilogue && gameStartTime == 0 && logBuffer != null) {
                logBuffer.add(e);
            } else {
                e.time -= gameStartTime;
                this.os.write((g.toJson(e) + "\n").getBytes());
            }
        } catch (IOException ex) {
            // System.err.println(ex);
        } catch (IllegalArgumentException iex) {
            System.err.println(iex);
        }
    }

    public void flushLogBuffer() {
        if (logBuffer == null) {
            return;
        }
        for (Entry e : logBuffer) {
            output(e);
        }
        logBuffer = null;
    }

    // @OnMessage(GeneratedMessage.class)
    public void onMessage(Context ctx, GeneratedMessage message) {
        System.err.println(message.getClass().getName());
        System.out.println(message.toString());
    }

    /*
     * @OnMessage(DotaUserMessages.CDOTAUserMsg_SpectatorPlayerClick.class)
     * public void onSpectatorPlayerClick(Context ctx,
     * DotaUserMessages.CDOTAUserMsg_SpectatorPlayerClick message){
     * Entry entry = new Entry(time);
     * entry.type = "clicks";
     * //need to get the entity by index
     * entry.key = String.valueOf(message.getOrderType());
     * 
     * Entity e =
     * ctx.getProcessor(Entities.class).getByIndex(message.getEntindex());
     * entry.x = getEntityProperty(e, "m_iCursor.0000", null);
     * entry.y = getEntityProperty(e, "m_iCursor.0001", null);
     * entry.slot = getEntityProperty(e, "m_iPlayerID", null);
     * //theres also target_index
     * output(entry);
     * }
     */

    @OnMessage(CMsgDOTAMatch.class)
    public void onDotaMatch(Context ctx, CMsgDOTAMatch message) {
        // TODO could use this for match overview data for uploads
        // System.err.println(message);
    }

    public Integer getPlayerSlotFromEntity(Context ctx, Entity e) {
        if (e == null)
            return null;
        Integer slot = getEntityProperty(e, "m_nPlayerID", null);
        // Sentry wards still use pre 7.31 property for storing new ID
        if (slot == null) {
            slot = getEntityProperty(e, "m_iPlayerID", null);
        }
        if (slot == null) {
            slot = getEntityProperty(e, "m_iPlayerOwnerID", null);
        }
        if (slot != null) {
            slot /= 2;
        }
        return slot;
    }

    @OnMessage(CDOTAUserMsg_SpectatorPlayerUnitOrders.class)
    public void onSpectatorPlayerUnitOrders(Context ctx, CDOTAUserMsg_SpectatorPlayerUnitOrders message) {
        Entry entry = new Entry(time);
        entry.type = "actions";
        // the entindex points to a CDOTAPlayer. This is probably the player that gave
        // the order.
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntindex());
        entry.slot = getPlayerSlotFromEntity(ctx, e);
        // Integer handle = (Integer)getEntityProperty(e, "m_hAssignedHero", null);
        // Entity h = ctx.getProcessor(Entities.class).getByHandle(handle);
        // System.err.println(h.getDtClass().getDtName());
        // break actions into types?
        entry.key = String.valueOf(message.getOrderType());
        // System.err.println(message);
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_LocationPing.class)
    public void onPlayerPing(Context ctx, CDOTAUserMsg_LocationPing message) {
        pingCount += 1;
        if (pingCount > 10000) {
            return;
        }

        Entry entry = new Entry(time);
        entry.type = "pings";
        entry.slot = message.getPlayerId();
        /*
         * System.err.println(message);
         * player_id: 7
         * location_ping {
         * x: 5871
         * y: 6508
         * target: -1
         * direct_ping: false
         * type: 0
         * }
         */
        // we could get the ping coordinates/type if we cared
        // entry.key = String.valueOf(message.getOrderType());
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_ChatEvent.class)
    public void onChatEvent(Context ctx, CDOTAUserMsg_ChatEvent message) {
        Integer player1 = message.getPlayerid1();
        Integer player2 = message.getPlayerid2();
        Integer value = message.getValue();
        String type = String.valueOf(message.getType());
        Entry entry = new Entry(time);
        entry.type = type;
        entry.player1 = player1;
        entry.player2 = player2;
        entry.value = value;
        output(entry);
    }

    // New chat event
    @OnMessage(CDOTAUserMsg_ChatMessage.class)
    public void onAllChatMessage(Context ctx, CDOTAUserMsg_ChatMessage message) {
        int type = message.getChannelType();
        Entry entry = new Entry(time);
        entry.slot = message.getSourcePlayerId();
        entry.type = (type == 11) ? "chat" : String.valueOf(type);
        entry.key = message.getMessageText();
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_ChatWheel.class)
    public void onChatWheel(Context ctx, CDOTAUserMsg_ChatWheel message) {
        Entry entry = new Entry(time);
        entry.type = "chatwheel";
        entry.slot = message.getPlayerId();
        entry.key = String.valueOf(message.getChatMessageId());
        output(entry);
    }

    @OnMessage(CUserMsg_SayText2.class)
    public void onAllChatS1(Context ctx, CUserMsg_SayText2 message) {
        Entry entry = new Entry(time);
        entry.unit = String.valueOf(message.getPrefix());
        entry.key = String.valueOf(message.getText());
        entry.type = "chat";
        output(entry);
    }

    @OnMessage(CUserMessageSayText2.class)
    public void onAllChatS2(Context ctx, CUserMessageSayText2 message) {
        Entry entry = new Entry(time);
        entry.unit = String.valueOf(message.getParam1());
        entry.key = String.valueOf(message.getParam2());
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntityindex());
        entry.slot = getPlayerSlotFromEntity(ctx, e);
        entry.type = "chat";
        output(entry);
    }

    @OnMessage(CDemoFileInfo.class)
    public void onFileInfo(Context ctx, CDemoFileInfo message) {
        // beware of 4.2b limit! we don't currently do anything with this, so we might
        // be able to just remove this
        // we can't use the value field since it takes Integers
        // Entry matchIdEntry = new Entry();
        // matchIdEntry.type = "match_id";
        // matchIdEntry.value = message.getGameInfo().getDota().getMatchId();
        // output(matchIdEntry);

        // Extracted cosmetics data from CDOTAWearableItem entities
        Entry cosmeticsEntry = new Entry();
        cosmeticsEntry.type = "cosmetics";
        cosmeticsEntry.key = new Gson().toJson(cosmeticsMap);
        output(cosmeticsEntry);

        // Dota plus hero levels
        Entry dotaPlusEntry = new Entry();
        dotaPlusEntry.type = "dotaplus";
        dotaPlusEntry.key = new Gson().toJson(dotaplusxpMap);
        output(dotaPlusEntry);

        // emit epilogue event to mark finish
        Entry epilogueEntry = new Entry();
        epilogueEntry.type = "epilogue";
        epilogueEntry.key = new Gson().toJson(message);
        output(epilogueEntry);
        epilogue = true;
        // Some replays don't have a game start time and we never flush, so just do it now
        flushLogBuffer();
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
        try {
            time = Math.round(cle.getTimestamp());
            // create a new entry
            Entry combatLogEntry = new Entry(time);
            if(start == 0) 
            {
            	start = serverTick;
            }
            else
            {
                combatLogEntry.ticks = serverTick-start;
                combatLogEntry.paused = pause;
            }
            combatLogEntry.type = cle.getType().name();
            // translate the fields using string tables if necessary (get*Name methods)
            combatLogEntry.attackername = cle.getAttackerName();
            combatLogEntry.targetname = cle.getTargetName();
            combatLogEntry.sourcename = cle.getDamageSourceName();
            combatLogEntry.targetsourcename = cle.getTargetSourceName();
            combatLogEntry.inflictor = cle.getInflictorName();
            combatLogEntry.attackerhero = cle.isAttackerHero();
            combatLogEntry.targethero = cle.isTargetHero();
            combatLogEntry.attackerillusion = cle.isAttackerIllusion();
            combatLogEntry.targetillusion = cle.isTargetIllusion();
            combatLogEntry.value = cle.getValue();
            float stunDuration = cle.getStunDuration();
            if (stunDuration > 0) {
                combatLogEntry.stun_duration = stunDuration;
            }
            float slowDuration = cle.getSlowDuration();
            if (slowDuration > 0) {
                combatLogEntry.slow_duration = slowDuration;
            }
            // value may be out of bounds in string table, we can only get valuename if a
            // purchase (type 11)
            if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_PURCHASE) {
                combatLogEntry.valuename = cle.getValueName();
            } else if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_GOLD) {
                combatLogEntry.gold_reason = cle.getGoldReason();
            } else if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_XP) {
                combatLogEntry.xp_reason = cle.getXpReason();
            }

            combatLogEntry.greevils_greed_stack = greevilsGreedVisitor.visit(time, cle);
            TrackStatus trackStatus = trackVisitor.visit(time, cle);
            if (trackStatus != null) {
                combatLogEntry.tracked_death = trackStatus.tracked;
                combatLogEntry.tracked_sourcename = trackStatus.inflictor;
            }
            if (combatLogEntry.type.equals("DOTA_COMBATLOG_GAME_STATE") && combatLogEntry.value == 6) {
                postGame = true;
            }
            if (combatLogEntry.type.equals("DOTA_COMBATLOG_GAME_STATE") && combatLogEntry.value == 5) {
                // See alternative gameStartTime from grp
                if (gameStartTime == 0) {
                    gameStartTime = combatLogEntry.time;
                    flushLogBuffer();
                }
            }
            if (cle.getType().ordinal() <= 19) {
                output(combatLogEntry);
            }
        } catch (Exception e) {
            System.err.println(e);
            System.err.println(cle);
        }
    }

    @OnEntityEntered
    public void onEntityEntered(Context ctx, Entity e) {
        String entityName = e.getDtClass().getDtName();

        if (entityName.equals("CDOTAWearableItem")) {
            Integer accountId = getEntityProperty(e, "m_iAccountID", null);
            Integer itemDefinitionIndex = getEntityProperty(e, "m_iItemDefinitionIndex", null);
            // System.err.format("%s,%s\n", accountId, itemDefinitionIndex);
            if (accountId > 0) {
                // Get the owner (a hero entity)
                Long accountId64 = 76561197960265728L + accountId;
                Integer playerSlot = steamid_to_playerslot.get(accountId64);
                cosmeticsMap.put(itemDefinitionIndex, playerSlot);
            }
        } else if (entityName.startsWith("CDOTA_Item_Tier") && entityName.endsWith("Token")) {
            Entry entry = new Entry(time);
            entry.type = "neutral_token";
            entry.slot = getPlayerSlotFromEntity(ctx, e);
            entry.key = entityName.substring("CDOTA_Item_".length()); // Tier1Token
            output(entry);
        }
    }

    @OnMessage(CNETMsg_Tick.class)
    public void onMessage(CNETMsg_Tick message) {
        serverTick = message.getTick();
    }

    @UsesStringTable("EntityNames")
    @UsesEntities
    @OnTickStart
    public void onTickStart(Context ctx, boolean synthetic) {
        /*
         * Iterator<Entity> cosmetics =
         * ctx.getProcessor(Entities.class).getAllByDtName("CDOTAWearableItem");
         * while ( cosmetics.hasNext() )
         * {
         * Entity e = cosmetics.next();
         * Integer accountId = getEntityProperty(e, "m_iAccountID", null);
         * Integer itemDefinitionIndex = getEntityProperty(e, "m_iItemDefinitionIndex",
         * null);
         * if (itemDefinitionIndex == 7559)
         * {
         * System.err.format("%s,%s\n", accountId, itemDefinitionIndex);
         * }
         * }
         */

        // TODO check engine to decide whether to use s1 or s2 entities
        // ctx.getEngineType()

        // s1 DT_DOTAGameRulesProxy
        Entity grp = ctx.getProcessor(Entities.class).getByDtName("CDOTAGamerulesProxy");
        Entity pr = ctx.getProcessor(Entities.class).getByDtName("CDOTA_PlayerResource");
        Entity dData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataDire");
        Entity rData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataRadiant");

        // Create draftStage variable
        Integer draftStage = getEntityProperty(grp, "m_pGameRules.m_nGameState", null);

        if (grp != null) {
            // System.err.println(grp);
            // dota_gamerules_data.m_iGameMode = 22
            // dota_gamerules_data.m_unMatchID64 = 1193091757
            Float oldTime = getEntityProperty(grp, "m_pGameRules.m_fGameTime", null);
            if (oldTime == null) {
                // 7.32e on, need to calculate time manually
                boolean isPaused = getEntityProperty(grp, "m_pGameRules.m_bGamePaused", null);
                int timeTick = isPaused ? getEntityProperty(grp, "m_pGameRules.m_nPauseStartTick", null) : serverTick;
                int pausedTicks = getEntityProperty(grp, "m_pGameRules.m_nTotalPausedTicks", null);
                pause = pausedTicks;
                time = Math.round((float) (timeTick - pausedTicks) / 30);
            } else {
                time = Math.round(oldTime);
            }
            // alternate to combat log for getting game zero time (looks like this is set at
            // the same time as the game start, so it's not any better for streaming)
            // Some replays don't have the combat log event for some reason so also do this
            // here
            int currGameStartTime = Math.round((float) grp.getProperty("m_pGameRules.m_flGameStartTime"));
            if (gameStartTime == 0 && currGameStartTime != 0) {
                gameStartTime = currGameStartTime;
                flushLogBuffer();
            }
            if (draftStage == 2) {

                // determine the time the draftings start
                if (!isDraftStartTimeProcessed) {
                    Long iPlayerIDsInControl = getEntityProperty(grp, "m_pGameRules.m_iPlayerIDsInControl", null);
                    boolean isDraftStarted = iPlayerIDsInControl.compareTo(Long.valueOf(0)) != 0;
                    if (isDraftStarted) {
                        Entry draftStartEntry = new Entry(time);
                        draftStartEntry.ticks = serverTick;
                        draftStartEntry.paused = pause;
                        draftStartEntry.type = "draft_start";
                        output(draftStartEntry);
                        isDraftStartTimeProcessed = true;
                    }
                }

                // Picks and ban are not in order due to draft change rules changes between
                // patches
                // Need to listen for the picks and ban to change
                int[] draftHeroes = new int[24];
                draftHeroes[0] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0000", null);
                draftHeroes[1] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0001", null);
                draftHeroes[2] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0002", null);
                draftHeroes[3] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0003", null);
                draftHeroes[4] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0004", null);
                draftHeroes[5] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0005", null);
                draftHeroes[6] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0006", null);
                draftHeroes[7] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0007", null);
                draftHeroes[8] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0008", null);
                draftHeroes[9] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0009", null);
                // Apparently Drafts go to 6 bans now, but have returns of null
                draftHeroes[10] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0010", null) == null ? 0
                        : getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0010", null);
                draftHeroes[11] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0011", null) == null ? 0
                        : getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0011", null);
                draftHeroes[12] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0012", null) == null ? 0
                        : getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0012", null);
                draftHeroes[13] = getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0013", null) == null ? 0
                        : getEntityProperty(grp, "m_pGameRules.m_BannedHeroes.0013", null);
                draftHeroes[14] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0000", null);
                draftHeroes[15] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0001", null);
                draftHeroes[16] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0002", null);
                draftHeroes[17] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0003", null);
                draftHeroes[18] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0004", null);
                draftHeroes[19] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0005", null);
                draftHeroes[20] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0006", null);
                draftHeroes[21] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0007", null);
                draftHeroes[22] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0008", null);
                draftHeroes[23] = getEntityProperty(grp, "m_pGameRules.m_SelectedHeroes.0009", null);
                // Once a pick or ban happens grab the time and extra time remaining for both
                // teams
                for (int i = 0; i < draftHeroes.length; i++) {
                    if (draftHeroes[i] > 0 && draftOrderProcessed[i] == false) {
                        // used to check for new bans and picks
                        draftOrderProcessed[i] = true;
                        Entry draftTimingsEntry = new Entry(time);
                        draftTimingsEntry.type = "draft_timings";
                        draftTimingsEntry.draft_order = order;
                        order = order + 1;
                        draftTimingsEntry.pick = i >= 14;
                        draftTimingsEntry.hero_id = draftHeroes[i];
                        draftTimingsEntry.draft_active_team = getEntityProperty(grp, "m_pGameRules.m_iActiveTeam",
                                null);
                        draftTimingsEntry.draft_extime0 = Math
                                .round((float) getEntityProperty(grp, "m_pGameRules.m_fExtraTimeRemaining.0000", null));
                        draftTimingsEntry.draft_extime1 = Math
                                .round((float) getEntityProperty(grp, "m_pGameRules.m_fExtraTimeRemaining.0001", null));
                        output(draftTimingsEntry);
                    }
                }
            }
            // initialize nextInterval value
            if (nextInterval == 0) {
                nextInterval = time;
            }
        }
        if (pr != null) {
            // Radiant coach shows up in vecPlayerTeamData as position 5
            // all the remaining dire entities are offset by 1 and so we miss reading the
            // last one and don't get data for the first dire player
            // coaches appear to be on team 1, radiant is 2 and dire is 3?
            // construct an array of valid indices to get vecPlayerTeamData from
            if (!init) {
                int added = 0;
                int i = 0;
                boolean hasWaitingForDraftPlayers = false;
                ArrayList<Entry> playerEntries = new ArrayList<Entry>();
                // according to @Decoud Valve seems to have fixed this issue and players should
                // be in first 10 slots again
                // sanity check of i to prevent infinite loop when <10 players?
                while (added < numPlayers && i < 30) {
                    try {
                        // check each m_vecPlayerData to ensure the player's team is radiant or dire
                        int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", i);
                        int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", i);
                        Long steamid = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerSteamID", i);
                        // System.err.format("%s %s %s: %s\n", i, playerTeam, teamSlot, steamid);
                        if (playerTeam == 2 || playerTeam == 3) {
                            // output the player_slot based on team and teamslot
                            Entry entry = new Entry(time);
                            entry.type = "player_slot";
                            entry.ticks = serverTick;
                            entry.paused = pause;
                            entry.key = String.valueOf(added);
                            entry.value = (playerTeam == 2 ? 0 : 128) + teamSlot;
                            playerEntries.add(entry);
                            if( start == 0) start = serverTick;
                            // add it to validIndices, add 1 to added
                            validIndices[added] = i;
                            added += 1;
                            slot_to_playerslot.put(added, entry.value);
                            steamid_to_playerslot.put(steamid, entry.value);
                        } else if (playerTeam == 14) {
                            // 7.33 player waiting to be drafted onto a team
                            hasWaitingForDraftPlayers = true;
                            break;
                        }
                    } catch (Exception e) {
                        // swallow the exception when an unexpected number of players (!=10)
                        // System.err.println(e);
                    }

                    i += 1;
                }
                if (!hasWaitingForDraftPlayers) {
                    for (int j = 0; j < playerEntries.size(); j++) {
                        output(playerEntries.get(j));
                    }
                    init = true;
                }
            }

            if (init && !postGame && time >= nextInterval) {
                // System.err.println(pr);
                for (int i = 0; i < numPlayers; i++) {
                    Integer hero = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", validIndices[i]);
                    Integer variant = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroVariant", validIndices[i]);
                    int handle = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_hSelectedHero", validIndices[i]);
                    int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", validIndices[i]);
                    int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", validIndices[i]);

                    // 2 is radiant, 3 is dire, 1 is other?
                    Entity dataTeam = playerTeam == 2 ? rData : dData;

                    Entry entry = new Entry(time);
                    entry.type = "interval";
                    entry.slot = i;
                    entry.repicked = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_bHasRepicked", validIndices[i]);
                    entry.randomed = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_bHasRandomed", validIndices[i]);
                    entry.pred_vict = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_bHasPredictedVictory",
                            validIndices[i]);
                    entry.firstblood_claimed = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iFirstBloodClaimed",
                            validIndices[i]);
                    entry.teamfight_participation = getEntityProperty(pr,
                            "m_vecPlayerTeamData.%i.m_flTeamFightParticipation", validIndices[i]);
                    ;
                    entry.level = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iLevel", validIndices[i]);
                    entry.kills = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iKills", validIndices[i]);
                    entry.deaths = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iDeaths", validIndices[i]);
                    entry.assists = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iAssists", validIndices[i]);
                    entry.denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
                    entry.obs_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iObserverWardsPlaced", teamSlot);
                    entry.sen_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iSentryWardsPlaced", teamSlot);
                    entry.creeps_stacked = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iCreepsStacked", teamSlot);
                    entry.camps_stacked = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iCampsStacked", teamSlot);
                    entry.rune_pickups = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iRunePickups", teamSlot);
                    entry.towers_killed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTowerKills", teamSlot);
                    entry.roshans_killed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iRoshanKills", teamSlot);
                    entry.observers_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iObserverWardsPlaced",
                            teamSlot);
                    entry.networth = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iNetWorth", teamSlot);
                    entry.stage = draftStage;

                    if (teamSlot >= 0) {
                        entry.gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                        entry.lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                        entry.xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);
                        entry.stuns = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_fStuns", teamSlot);
                    }

                    // TODO: gem, rapier time?
                    // need to dump inventory items for each player and possibly keep track of item
                    // entity handles

                    // get the player's hero entity
                    Entity e = ctx.getProcessor(Entities.class).getByHandle(handle);
                    // get the hero's coordinates
                    if (e != null) {
                        // System.err.println(e);
                        //CBodyComponent.m_cell[XY] * 128 + CBodyComponent.m_vec[XY]
                        Integer cx = getEntityProperty(e, "CBodyComponent.m_cellX", null);
                        Integer cy = getEntityProperty(e, "CBodyComponent.m_cellY", null);

                        Float vx = getEntityProperty(e, "CBodyComponent.m_vecX", null);
                        Float vy = getEntityProperty(e, "CBodyComponent.m_vecY", null);

                        entry.x = getPreciseLocation(cx,vx);
                        entry.y = getPreciseLocation(cy,vy);
                        // System.err.format("%s, %s\n", entry.x, entry.y);
                        // get the hero's entity name, ex: CDOTA_Hero_Zuus
                        entry.unit = e.getDtClass().getDtName();
                        entry.hero_id = hero;
                        entry.variant = variant;
                        entry.life_state = getEntityProperty(e, "m_lifeState", null);
                        entry.hp = getEntityProperty(e, "m_iHealth", null);
                        entry.ticks = serverTick-start;
                        entry.paused = pause;
                        entry.oldTime = getEntityProperty(grp, "m_pGameRules.m_fGameTime", null);



                        // check if hero has been assigned to entity
                        if (hero > 0) {
                            // get the hero's entity name, ex: CDOTA_Hero_Zuus
                            String unit = e.getDtClass().getDtName();
                            // grab the end of the name, lowercase it
                            String ending = unit.substring("CDOTA_Unit_Hero_".length());
                            // valve is bad at consistency and the combat log name could involve replacing
                            // camelCase with _ or not!
                            // double map it so we can look up both cases
                            String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                            // don't include final underscore here since the first letter is always
                            // capitalized and will be converted to underscore
                            String combatLogName2 = "npc_dota_hero" + ending.replaceAll("([A-Z])", "_$1").toLowerCase();
                            // System.err.format("%s, %s, %s\n", unit, combatLogName, combatLogName2);
                            // populate for combat log mapping
                            name_to_slot.put(combatLogName, entry.slot);
                            name_to_slot.put(combatLogName2, entry.slot);

                            abilities = getHeroAbilities(ctx, e);
                            for (Ability ability : abilities) {
                                // Only push ability updates when the level changes
                                if (abilities_tracking.get(combatLogName + ability.id) != ability.abilityLevel
                                        || abilities_tracking.isEmpty()) {
                                    Entry abilitiesEntry = new Entry(time);
                                    abilitiesEntry.type = "DOTA_ABILITY_LEVEL";
                                    abilitiesEntry.targetname = combatLogName;
                                    abilitiesEntry.valuename = ability.id;
                                    abilitiesEntry.abilitylevel = ability.abilityLevel;
                                    // We use the combatLogName & the ability id as some ability IDs are the same
                                    abilities_tracking.put(combatLogName + ability.id, ability.abilityLevel);
                                    output(abilitiesEntry);
                                }
                            }

                            entry.hero_inventory = getHeroInventory(ctx, e);
                            if (time - gameStartTime - 1 == 0) {
                                for (Item item : entry.hero_inventory) {
                                    Entry startingItems = new Entry(time);
                                    startingItems.type = "STARTING_ITEM";
                                    startingItems.targetname = combatLogName;
                                    startingItems.valuename = item.id;
                                    startingItems.slot = entry.slot;
                                    startingItems.value = (entry.slot < 5 ? 0 : 123) + entry.slot;
                                    startingItems.itemslot = item.slot;
                                    startingItems.charges = item.num_charges;
                                    startingItems.secondary_charges = item.num_secondary_charges;
                                    output(startingItems);
                                }
                            }
                            if (!isPlayerStartingItemsWritten.get(entry.slot) && entry.hero_inventory != null) {
                                // Making something similar to DOTA_COMBATLOG_PURCHASE for each item in the
                                // beginning of the game
                                isPlayerStartingItemsWritten.set(entry.slot, true);
                                for (Item item : entry.hero_inventory) {
                                    Entry startingItemsEntry = new Entry(time);
                                    startingItemsEntry.type = "DOTA_COMBATLOG_PURCHASE";
                                    startingItemsEntry.slot = entry.slot;
                                    startingItemsEntry.value = (entry.slot < 5 ? 0 : 123) + entry.slot;
                                    startingItemsEntry.valuename = item.id;
                                    startingItemsEntry.targetname = combatLogName;
                                    startingItemsEntry.charges = item.num_charges;
                                    output(startingItemsEntry);
                                }
                            }
                        }
                    }
                    output(entry);
                }
                nextInterval += INTERVAL;
            }

            // When the game is over, get dota plus levels
            if (postGame && !isDotaPlusProcessed) {
                for (int i = 0; i < numPlayers; i++) {
                    int xp = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_unSelectedHeroBadgeXP", i) == null ? 0
                            : getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_unSelectedHeroBadgeXP", i);
                    Long steamid = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerSteamID", i);
                    if (steamid_to_playerslot.containsKey(steamid)) {
                        int playerslot = steamid_to_playerslot.get(steamid);
                        dotaplusxpMap.put(playerslot, xp);
                    }
                }
                isDotaPlusProcessed = true;
            }
        }
    }

    private List<Item> getHeroInventory(Context ctx, Entity eHero) {
        List<Item> inventoryList = new ArrayList<>(6);

        for (int i = 0; i < 8; i++) {
            try {
                Item item = getHeroItem(ctx, eHero, i);
                if (item != null) {
                    inventoryList.add(item);
                }
            } catch (Exception e) {
                // System.err.println(e);
            }
        }

        return inventoryList;
    }

    private List<Ability> getHeroAbilities(Context ctx, Entity eHero) {
        List<Ability> abilityList = new ArrayList<>(32);
        for (int i = 0; i < 32; i++) {
            try {
                Ability ability = getHeroAbilities(ctx, eHero, i);
                if (ability != null) {
                    abilityList.add(ability);

                }
            } catch (Exception e) {
                // System.err.println(e);
            }
        }
        return abilityList;
    }

    /**
     * Uses "EntityNames" string table and Entities processor
     * 
     * @param ctx   Context
     * @param eHero Hero entity
     * @param idx   0-5 - inventory, 6-8 - backpack, 9-16 - stash
     * @return {@code null} - empty slot. Throws @{@link UnknownItemFoundException}
     *         if item information can't be extracted
     */
    private Item getHeroItem(Context ctx, Entity eHero, int idx) throws UnknownItemFoundException {
        StringTable stEntityNames = ctx.getProcessor(StringTables.class).forName("EntityNames");
        Entities entities = ctx.getProcessor(Entities.class);

        Integer hItem = eHero.getProperty("m_hItems." + Util.arrayIdxToString(idx));
        if (hItem == 0xFFFFFF) {
            return null;
        }
        Entity eItem = entities.getByHandle(hItem);
        if (eItem == null) {
            throw new UnknownItemFoundException(String.format("Can't find item by its handle (%d)", hItem));
        }
        String itemName = stEntityNames.getNameByIndex(eItem.getProperty("m_pEntity.m_nameStringableIndex"));
        if (itemName == null) {
            throw new UnknownItemFoundException("Can't get item name from EntityName string table");
        }

        Item item = new Item();
        item.id = itemName;
        item.slot = idx;
        int numCharges = eItem.getProperty("m_iCurrentCharges");
        if (numCharges != 0) {
            item.num_charges = numCharges;
        }
        int numSecondaryCharges = eItem.getProperty("m_iSecondaryCharges");
        if (numSecondaryCharges != 0) {
            item.num_secondary_charges = numSecondaryCharges;
        }
        return item;
    }

    /**
     * Uses "EntityNames" string table and Entities processor
     * 
     * @param ctx   Context
     * @param eHero Hero entity
     * @param idx   0-31 = Hero abilities including talents and special event items
     * @return {@code null} - empty slot. Throws @{@link UnknownItemFoundException}
     *         if item information can't be extracted
     */
    private Ability getHeroAbilities(Context ctx, Entity eHero, int idx) throws UnknownAbilityFoundException {
        StringTable stEntityNames = ctx.getProcessor(StringTables.class).forName("EntityNames");
        Entities entities = ctx.getProcessor(Entities.class);

        Integer hAbility = eHero.getProperty("m_hAbilities." + Util.arrayIdxToString(idx));
        if (hAbility == 0xFFFFFF) {
            return null;
        }
        Entity eAbility = entities.getByHandle(hAbility);
        if (eAbility == null) {
            throw new UnknownAbilityFoundException(String.format("Can't find ability by its handle (%d)", hAbility));
        }
        String abilityName = stEntityNames.getNameByIndex(eAbility.getProperty("m_pEntity.m_nameStringableIndex"));
        if (abilityName == null) {
            throw new UnknownAbilityFoundException("Can't get ability name from EntityName string table");
        }

        Ability ability = new Ability();
        ability.id = abilityName;
        ability.abilityLevel = eAbility.getProperty("m_iLevel");

        return ability;
    }

    public <T> T getEntityProperty(Entity e, String property, Integer idx) {
        try {
            if (e == null) {
                return null;
            }
            if (idx != null) {
                property = property.replace("%i", Util.arrayIdxToString(idx));
            }
            FieldPath fp = e.getDtClass().getFieldPathForName(property);
            return e.getPropertyForFieldPath(fp);
        } catch (Exception ex) {
            return null;
        }
    }

    @OnWardKilled
    public void onWardKilled(Context ctx, Entity e, String killerHeroName) {
        Entry wardEntry = buildWardEntry(ctx, e);
        wardEntry.attackername = killerHeroName;
        output(wardEntry);
    }

    @OnWardExpired
    @OnWardPlaced
    public void onWardExistenceChanged(Context ctx, Entity e) {
        output(buildWardEntry(ctx, e));
    }

    private Entry buildWardEntry(Context ctx, Entity e) {
        Entry entry = new Entry(time);
        boolean isObserver = !e.getDtClass().getDtName().contains("TrueSight");

        Integer cx = getEntityProperty(e, "CBodyComponent.m_cellX", null);
        Integer cy = getEntityProperty(e, "CBodyComponent.m_cellY", null);
        Integer cz = getEntityProperty(e, "CBodyComponent.m_cellZ", null);

        Float vx = getEntityProperty(e, "CBodyComponent.m_vecX", null);
        Float vy = getEntityProperty(e, "CBodyComponent.m_vecY", null);
        Float vz = getEntityProperty(e, "CBodyComponent.m_vecZ", null);

        Integer life_state = getEntityProperty(e, "m_lifeState", null);

        entry.x = getPreciseLocation(cx,vx);
        entry.y = getPreciseLocation(cy,vy);
        entry.z = getPreciseLocation(cz,vz);

        entry.type = isObserver ? "obs" : "sen";
        entry.entityleft = life_state == 1;
        entry.ehandle = e.getHandle();

        if (entry.entityleft) {
            entry.type += "_left";
        }

        Integer owner = getEntityProperty(e, "m_hOwnerEntity", null);
        Entity ownerEntity = ctx.getProcessor(Entities.class).getByHandle(owner);
        entry.slot = getPlayerSlotFromEntity(ctx, ownerEntity);

        return entry;
    }
}
