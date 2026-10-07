package dev.c9tech.entityutils.model;

public enum MethodNamingStyle {
    CONVERT_X_TO_Y("convert{Source}To{Target} (e.g. convertUserToUserDto)"),
    TO_TARGET("to{Target} (e.g. toUserDto / toEntity)");

    private final String displayName;

    MethodNamingStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
