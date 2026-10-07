package dev.c9tech.entityutils.model;

public enum DtoStyle {
    JAVA_BEAN("Standard Java Bean (Getters & Setters)"),
    LOMBOK("Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor)"),
    RECORD("Java 17 Record");

    private final String displayName;

    DtoStyle(String displayName) {
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
