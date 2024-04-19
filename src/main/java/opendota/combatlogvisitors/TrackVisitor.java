package opendota.combatlogvisitors;

import java.util.HashMap;

import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.DOTA_COMBATLOG_TYPES;

public class TrackVisitor implements Visitor<TrackVisitor.TrackStatus> {

	public static class TrackStatus {
		public String inflictor;
		public boolean tracked;
		
		public TrackStatus() {
			inflictor = null;
			tracked = false;
		}
		
		public TrackStatus(String inflictor, boolean tracked) {
			this.inflictor = inflictor;
			this.tracked = tracked;
		}
		
	}
	
	private static final String BH_TRACK = "modifier_bounty_hunter_track";

	HashMap<String, TrackStatus> trackStatus = new HashMap<String, TrackStatus>();

	@Override
	public TrackStatus visit(int time, CombatLogEntry cle) {

		if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_MODIFIER_ADD && cle.getInflictorName().equals(BH_TRACK)) {
			trackStatus.put(cle.getTargetName(), new TrackStatus(cle.getAttackerName(), true));
		}

		if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_MODIFIER_REMOVE && cle.getInflictorName().equals(BH_TRACK)) {
			trackStatus.remove(cle.getTargetName());
		}

		if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH && trackStatus.getOrDefault(cle.getTargetName(), new TrackStatus()).tracked) {
			return trackStatus.get(cle.getTargetName());
		}

		return null;
	}
}
