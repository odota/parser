package opendota.combatlogvisitors;

import skadistats.clarity.model.CombatLogEntry;

public interface Visitor<T> {

	T visit(int time, CombatLogEntry cle);
}

