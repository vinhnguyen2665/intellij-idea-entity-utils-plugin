package dev.c9tech.entityutils.model;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;

public class DtoFieldItem {
    private String name;
    private PsiType type;
    private String typeText;
    private boolean selected;
    private PsiField originalField;

    public DtoFieldItem(PsiField originalField) {
        this.originalField = originalField;
        this.name = originalField.getName();
        this.type = originalField.getType();
        this.typeText = originalField.getType().getPresentableText();
        this.selected = true;
    }

    public DtoFieldItem(String name, String typeText, PsiField originalField) {
        this.originalField = originalField;
        this.name = name;
        this.type = originalField != null ? originalField.getType() : null;
        this.typeText = typeText;
        this.selected = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PsiType getType() {
        return type;
    }

    public String getTypeText() {
        return typeText;
    }

    public void setTypeText(String typeText) {
        this.typeText = typeText;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public PsiField getOriginalField() {
        return originalField;
    }
}
