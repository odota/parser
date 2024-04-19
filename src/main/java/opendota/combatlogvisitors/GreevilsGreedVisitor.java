package opendota.combatlogvisitors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.DOTA_COMBATLOG_TYPES;

public class GreevilsGreedVisitor implements Visitor<Integer> {

    private static final int GREEVILS_GREED_WINDOW = 36;
    
	private boolean greevilsGreedLearned = false;
    private Set<Integer> lastHitTimings = new HashSet<>();
    
	private HashMap<String, Integer> nameToSlot; 
    
	public GreevilsGreedVisitor(HashMap<String, Integer> nameToSlot) {
		this.nameToSlot = nameToSlot;
	}

	@Override
	public Integer visit(int time, CombatLogEntry cle) {
        if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_MODIFIER_ADD
        		&& cle.getAttackerName().equals("npc_dota_hero_alchemist")
        		&& cle.getInflictorName().equals("modifier_alchemist_goblins_greed")
        		&& !cle.isAttackerIllusion()) {
        	greevilsGreedLearned = true;
        }
        
        if (greevilsGreedLearned
        		&& cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH
        		&& cle.getAttackerName().equals("npc_dota_hero_alchemist")
        		&& !cle.isAttackerIllusion()) {

        	if (isDeny(cle.getTargetName())) {
        		return null;
        	}
        	
        	Iterator<Integer> iterator = lastHitTimings.iterator();
			while(iterator.hasNext()) {
        		Integer lhTiming = iterator.next();
				boolean isExpired = lhTiming + GREEVILS_GREED_WINDOW < time;
				if (isExpired) {
        			iterator.remove();
        		}
        	}
        	
			int currentStack = lastHitTimings.size();
        	
        	lastHitTimings.add(time);
        	
        	return currentStack;
        }
        return null;
	}
	
	private boolean isDeny(String targetName) {
		String creepAllied = nameToSlot.get("npc_dota_hero_alchemist") < 5 ? "goodguys" : "badguys";
		return targetName.contains(creepAllied);
	}

}
