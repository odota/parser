package opendota.combatlogvisitors;

import opendota.Parse.Entry;
import skadistats.clarity.model.CombatLogEntry;

public interface Visitor<T> {

	T visit(int time, CombatLogEntry cle);
}

