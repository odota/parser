package opendota.combatlogvisitors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import opendota.Parse.Entry;

public class CombatLogVisitors {

	final List<Visitor> visitors;
	
	public CombatLogVisitors(HashMap<String, Integer> nameToSlot) {
		visitors = Arrays.asList(new BountyHunterTrackVisitor(),new GreevilsGreedVisitor(nameToSlot));
	}
	
	public VisitorResult visit(Entry entry) {
		VisitorResult result = new VisitorResult(); 
		for (Visitor v : visitors) {
			v.visit(entry, result);
		}
		return result;
	}
}
