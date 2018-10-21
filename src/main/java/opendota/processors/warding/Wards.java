package opendota.processors.warding;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import skadistats.clarity.event.Event;
import skadistats.clarity.event.EventListener;
import skadistats.clarity.event.Initializer;
import skadistats.clarity.event.Provides;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityDeleted;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickEnd;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.wire.common.proto.DotaUserMessages;

/**
 * @author micaelbergeron
 */
@UsesEntities
@Provides({ OnWardKilled.class, OnWardExpired.class, OnWardPlaced.class })
public class Wards {
    
    private static final Map<String, String> WARDS_TARGET_NAME_BY_DT_CLASS;   
    private static final Set<String> WARDS_DT_CLASSES;
    private static final Set<String> WARDS_TARGET_NAMES;
    
    static {
        final String TARGET_NAME_OBSERVER = "npc_dota_observer_wards";
        final String TARGET_NAME_SENTRY = "npc_dota_sentry_wards";
        HashMap<String, String> target_by_dtclass = new HashMap<>(4);
        
        target_by_dtclass.put("DT_DOTA_NPC_Observer_Ward", TARGET_NAME_OBSERVER);
        target_by_dtclass.put("CDOTA_NPC_Observer_Ward", TARGET_NAME_OBSERVER);
        target_by_dtclass.put("DT_DOTA_NPC_Observer_Ward_TrueSight", TARGET_NAME_SENTRY);
        target_by_dtclass.put("CDOTA_NPC_Observer_Ward_TrueSight", TARGET_NAME_SENTRY);
        
        WARDS_TARGET_NAME_BY_DT_CLASS = Collections.unmodifiableMap(target_by_dtclass);
        WARDS_DT_CLASSES = Collections.unmodifiableSet(target_by_dtclass.keySet());
        WARDS_TARGET_NAMES = Collections.unmodifiableSet(new HashSet<>(target_by_dtclass.values()));
    }
    
    private final Map<Integer, FieldPath> lifeStatePaths = new HashMap<>();
    private final Map<Integer, Integer> currentLifeState = new HashMap<>();

    private final Map<String, Queue<String>> wardKillersByWardClass = new HashMap<>();
    private Queue<ProcessEntityCommand> toProcess = new ArrayDeque<>();

    private Event<OnWardKilled> evKilled;
    private Event<OnWardExpired> evExpired;
    private Event<OnWardPlaced> evPlaced;
    
    private class ProcessEntityCommand {

        private final Entity entity;
        private final FieldPath fieldPath;
        
        public ProcessEntityCommand(Entity e, FieldPath p) {
            entity = e;
            fieldPath = p;
        }
    }

    @Initializer(OnWardKilled.class)
    public void initOnWardKilled(final Context ctx, final EventListener<OnWardKilled> listener) {
        evKilled = ctx.createEvent(OnWardKilled.class, Entity.class, String.class);
    }
    
    @Initializer(OnWardExpired.class)
    public void initOnWardExpired(final Context ctx, final EventListener<OnWardExpired> listener) {
        evExpired = ctx.createEvent(OnWardExpired.class, Entity.class);
    }
        
    @Initializer(OnWardPlaced.class)
    public void initOnWardPlaced(final Context ctx, final EventListener<OnWardPlaced> listener) {
        evPlaced = ctx.createEvent(OnWardPlaced.class, Entity.class);
    }

    public Wards() {
        WARDS_TARGET_NAMES.forEach((cls) -> {
            wardKillersByWardClass.put(cls, new ArrayDeque<>());
        });
    }   
        
    @OnEntityCreated
    public void onCreated(Context ctx, Entity e) {      
        if (!isWard(e)) return;
        
        FieldPath lifeStatePath;
        
        clearCachedState(e);
        ensureFieldPathForEntityInitialized(e);
        if ((lifeStatePath = getFieldPathForEntity(e)) != null) {
            processLifeStateChange(e, lifeStatePath);
        }
    }
        
    @OnEntityUpdated
    public void onUpdated(Context ctx, Entity e, FieldPath[] fieldPaths, int num) {
        FieldPath p;
        if ((p = getFieldPathForEntity(e)) != null) {
            for (int i = 0; i < num; i++) {
                if (fieldPaths[i].equals(p)) {
                    toProcess.add(new ProcessEntityCommand(e, p));
                    break;
                }
            }
        }
    }
        
    @OnEntityDeleted
    public void onDeleted(Context ctx, Entity e) {
        clearCachedState(e);
    }
    
    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry entry) {
        if (!isWardDeath(entry)) return;
        
        String killer;
        if ((killer = entry.getDamageSourceName()) != null) {
            wardKillersByWardClass.get(entry.getTargetName()).add(killer);
        }
    }
    
    @OnTickEnd
    public void onTickEnd(Context ctx, boolean synthetic) {
        if (!synthetic) return;
        
        ProcessEntityCommand cmd;
        while ((cmd = toProcess.poll()) != null) {
            processLifeStateChange(cmd.entity, cmd.fieldPath);
        }
    }
        
    private FieldPath getFieldPathForEntity(Entity e) {
        return lifeStatePaths.get(e.getDtClass().getClassId());
    }

    private void clearCachedState(Entity e) {
        currentLifeState.remove(e.getIndex());
    }
    
    private void ensureFieldPathForEntityInitialized(Entity e) {
        Integer cid = e.getDtClass().getClassId();
        if (!lifeStatePaths.containsKey(cid)) {
            lifeStatePaths.put(cid, e.getDtClass().getFieldPathForName("m_lifeState"));
        }
    }
    
    private boolean isWard(Entity e) {
        return WARDS_DT_CLASSES.contains(e.getDtClass().getDtName());
    }
    
    private boolean isWardDeath(CombatLogEntry e) {
        return e.getType().equals(DotaUserMessages.DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH)
                && WARDS_TARGET_NAMES.contains(e.getTargetName());
    }
    
    public  void processLifeStateChange(Entity e, FieldPath p) {
        int oldState = currentLifeState.containsKey(e.getIndex()) ? currentLifeState.get(e.getIndex()) : 2;
        int newState = e.getPropertyForFieldPath(p);
        if (oldState != newState) {
            switch(newState) {
                case 0:
                    if (evPlaced != null) {
                        evPlaced.raise(e);
                    }
                    break;
                case 1:
                    String killer;
                    if ((killer = wardKillersByWardClass.get(getWardTargetName(e.getDtClass().getDtName())).poll()) != null) {
                        if (evKilled != null) {
                            evKilled.raise(e, killer);
                        }
                    } else {
                        if (evExpired != null) {
                            evExpired.raise(e);
                        }
                    }
                    break;
            }
        }
        
        currentLifeState.put(e.getIndex(), newState);
    }
    
    private String getWardTargetName(String ward_dtclass_name) {
        return WARDS_TARGET_NAME_BY_DT_CLASS.get(ward_dtclass_name);
    }
}

