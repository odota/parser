package opendota;

import java.util.List;

public class Entry implements Cloneable {
    public Integer time = 0;
    public String type;
    public Integer team;
    public String unit;
    public String key;
    public Integer value;
    public Float floatValue;
    public Boolean booleanValue;
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
    public Integer facet_hero_id;
    public transient List<Item> hero_inventory;
    public Integer itemslot;
    public Integer charges;
    public Integer secondary_charges;
    public Integer life_state;
    public Integer level;
    public Integer kills;
    public Integer deaths;
    public Integer assists;
    public Integer denies;
    public Boolean entityleft;
    public Integer ehandle;
    public Boolean isNeutralActiveDrop;
    public Boolean isNeutralPassiveDrop;
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
    public Boolean posData;
    public Boolean max;
    public Boolean interval;
    public String event;
    public Integer killer;

    public Entry() {
    }

    public Entry(Integer time) {
        this.time = time;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
