package opendota.combatlogvisitors;

import opendota.Parse.Entry;

public interface Visitor {

	void visit(Entry entry, VisitorResult result);
}
