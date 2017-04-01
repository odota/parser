package opendota.combatlogvisitors;

import static opendota.combatlogvisitors.CombatLogConstants.DOTA_COMBATLOG_MODIFIER_ADD;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import opendota.Parse.Entry;

public class GreevilsGreedVisitor implements Visitor<Integer> {

    private static final int GREEVILS_GREED_WINDOW = 30;
    
	private boolean greevilsGreedLearned = false;
    private Set<Integer> lastHitTimings = new HashSet<>();
    
	private HashMap<String, Integer> nameToSlot; 
    
	public GreevilsGreedVisitor(HashMap<String, Integer> nameToSlot) {
		this.nameToSlot = nameToSlot;
	}

	@Override
	public Integer visit(Entry entry) {
        if (entry.type.equals(DOTA_COMBATLOG_MODIFIER_ADD)
        		&& entry.attackername.equals("npc_dota_hero_alchemist")
        		&& entry.inflictor.equals("modifier_alchemist_goblins_greed")
        		&& !entry.attackerillusion) {
        	greevilsGreedLearned = true;
        }
        
        if (greevilsGreedLearned
        		&& entry.type.equals(CombatLogConstants.DOTA_COMBATLOG_DEATH)
        		&& entry.attackername.equals("npc_dota_hero_alchemist")
        		&& !entry.attackerillusion) {

        	if (isDeny(entry.targetname)) {
        		return null;
        	}
        	
        	Iterator<Integer> iterator = lastHitTimings.iterator();
			while(iterator.hasNext()) {
        		Integer lhTiming = iterator.next();
				boolean isExpired = lhTiming + GREEVILS_GREED_WINDOW < entry.time;
				if (isExpired) {
        			iterator.remove();
        		}
        	}
        	
			int currentStack = lastHitTimings.size();
        	
        	lastHitTimings.add(entry.time);
        	
        	return currentStack;
        }
        return null;
	}
	
	private boolean isDeny(String targetName) {
		String creepAllied = nameToSlot.get("npc_dota_hero_alchemist") < 5 ? "goodguys" : "badguys";
		return targetName.contains(creepAllied);
	}

}
