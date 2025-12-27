package opendota;

public class Item {
    String id;
    // Charges can be used to determine how many items are stacked together on
    // stackable items
    Integer slot;
    Integer num_charges;
    // item_ward_dispenser uses num_charges for observer wards
    // and num_secondary_charges for sentry wards count
    // and is considered not stackable
    Integer num_secondary_charges;
}