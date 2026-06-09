package com.alucard.heirloomsword;

public enum FamiliarState {
    HOVERING(0),
    LAUNCHING(1),
    STUCK(2),
    RETURNING(3),
    CHARGING(4);

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
            default -> HOVERING;
        };
    }
}
