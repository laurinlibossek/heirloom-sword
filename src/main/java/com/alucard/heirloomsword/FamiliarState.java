package com.alucard.heirloomsword;

public enum FamiliarState {
    HOVERING(0),
    LAUNCHING(1),
    STUCK(2),
    RETURNING(3),
    CHARGING(4),
    SWEEPING_HOLD(5),
    SWEEPING_RELEASE(6),
    BLOCKING(7),
    DYING(8),
    ARRIVING(9),
    QUICK_FIRE(10);

    private final int id;

    FamiliarState(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static FamiliarState fromId(int id) {
        return switch (id) {
            case 1 -> LAUNCHING;
            case 2 -> STUCK;
            case 3 -> RETURNING;
            case 4 -> CHARGING;
            case 5 -> SWEEPING_HOLD;
            case 6 -> SWEEPING_RELEASE;
            case 7 -> BLOCKING;
            case 8 -> DYING;
            case 9 -> ARRIVING;
            case 10 -> QUICK_FIRE;
            default -> HOVERING;
        };
    }
}
