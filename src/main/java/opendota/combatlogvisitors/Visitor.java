package opendota.combatlogvisitors;

import opendota.Parse.Entry;

public interface Visitor<T> {

	T visit(Entry entry);
}
