package dev.c9tech.entityutils.model;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import dev.c9tech.entityutils.generator.TypeConversionHelper;
import dev.c9tech.entityutils.util.StringUtil;

public class FieldMappingItem {

    public enum ConversionKind {
        DIRECT("Direct assignment"),
        DATE_TO_TIMESTAMP("Date -> Timestamp"),
        TIMESTAMP_TO_DATE("Timestamp -> Date"),
        STRING_TO_DOUBLE("String -> Double"),
        DOUBLE_TO_STRING("Double -> String"),
        STRING_TO_INTEGER("String -> Integer"),
        INTEGER_TO_STRING("Integer -> String"),
        STRING_TO_LONG("String -> Long"),
        LONG_TO_STRING("Long -> String"),
        NUMBER_TO_STRING("Number -> String"),
        NESTED_OBJECT("Nested Object Converter"),
        CUSTOM("Custom Type");

        private final String description;

        ConversionKind(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private PsiField sourceField;
    private PsiField targetField;
    private String sourceFieldName;
    private String sourceFieldTypeText;
    private String targetFieldName;
    private String targetFieldTypeText;

    private String sourceGetter;
    private String targetSetter;
    private String targetGetter;
    private String sourceSetter;

    private boolean selected;
    private ConversionKind forwardConversion;
    private ConversionKind reverseConversion;

    public FieldMappingItem(String sourceFieldName,
                            String sourceFieldTypeText,
                            String targetFieldName,
                            String targetFieldTypeText,
                            PsiField sourceField,
                            PsiField targetField) {
        this.sourceFieldName = sourceFieldName;
        this.sourceFieldTypeText = sourceFieldTypeText;
        this.targetFieldName = targetFieldName != null ? targetFieldName : sourceFieldName;
        this.targetFieldTypeText = targetFieldTypeText != null ? targetFieldTypeText : sourceFieldTypeText;
        this.sourceField = sourceField;
        this.targetField = targetField;

        boolean isSrcBool = "boolean".equalsIgnoreCase(sourceFieldTypeText);
        boolean isTgtBool = "boolean".equalsIgnoreCase(this.targetFieldTypeText);

        this.sourceGetter = StringUtil.getGetterMethodName(sourceFieldName, isSrcBool);
        this.sourceSetter = StringUtil.getSetterMethodName(sourceFieldName);
        this.targetSetter = StringUtil.getSetterMethodName(this.targetFieldName);
        this.targetGetter = StringUtil.getGetterMethodName(this.targetFieldName, isTgtBool);

        this.forwardConversion = TypeConversionHelper.detectConversion(this.sourceFieldTypeText, this.targetFieldTypeText);
        this.reverseConversion = TypeConversionHelper.detectConversion(this.targetFieldTypeText, this.sourceFieldTypeText);

        this.selected = true;
    }

    public String getSourceFieldName() {
        return sourceFieldName;
    }

    public void setSourceFieldName(String sourceFieldName) {
        this.sourceFieldName = sourceFieldName;
    }

    public String getSourceFieldTypeText() {
        return sourceFieldTypeText;
    }

    public void setSourceFieldTypeText(String sourceFieldTypeText) {
        this.sourceFieldTypeText = sourceFieldTypeText;
    }

    public String getTargetFieldName() {
        return targetFieldName;
    }

    public void setTargetFieldName(String targetFieldName) {
        this.targetFieldName = targetFieldName;
        boolean isBool = "boolean".equalsIgnoreCase(this.targetFieldTypeText);
        this.targetSetter = StringUtil.getSetterMethodName(targetFieldName);
        this.targetGetter = StringUtil.getGetterMethodName(targetFieldName, isBool);
    }

    public String getTargetFieldTypeText() {
        return targetFieldTypeText;
    }

    public void setTargetFieldTypeText(String targetFieldTypeText) {
        this.targetFieldTypeText = targetFieldTypeText;
        boolean isBool = "boolean".equalsIgnoreCase(targetFieldTypeText);
        this.targetGetter = StringUtil.getGetterMethodName(this.targetFieldName, isBool);
        this.forwardConversion = TypeConversionHelper.detectConversion(this.sourceFieldTypeText, targetFieldTypeText);
        this.reverseConversion = TypeConversionHelper.detectConversion(targetFieldTypeText, this.sourceFieldTypeText);
    }

    public PsiField getSourceField() {
        return sourceField;
    }

    public void setSourceField(PsiField sourceField) {
        this.sourceField = sourceField;
    }

    public PsiField getTargetField() {
        return targetField;
    }

    public void setTargetField(PsiField targetField) {
        this.targetField = targetField;
        if (targetField != null) {
            this.targetFieldName = targetField.getName();
            this.targetFieldTypeText = targetField.getType().getPresentableText();
            this.forwardConversion = TypeConversionHelper.detectConversion(this.sourceFieldTypeText, this.targetFieldTypeText);
            this.reverseConversion = TypeConversionHelper.detectConversion(this.targetFieldTypeText, this.sourceFieldTypeText);
        }
    }

    public String getSourceGetter() {
        return sourceGetter;
    }

    public void setSourceGetter(String sourceGetter) {
        this.sourceGetter = sourceGetter;
    }

    public String getTargetSetter() {
        return targetSetter;
    }

    public void setTargetSetter(String targetSetter) {
        this.targetSetter = targetSetter;
    }

    public String getTargetGetter() {
        return targetGetter;
    }

    public void setTargetGetter(String targetGetter) {
        this.targetGetter = targetGetter;
    }

    public String getSourceSetter() {
        return sourceSetter;
    }

    public void setSourceSetter(String sourceSetter) {
        this.sourceSetter = sourceSetter;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public ConversionKind getForwardConversion() {
        return forwardConversion;
    }

    public void setForwardConversion(ConversionKind forwardConversion) {
        this.forwardConversion = forwardConversion;
    }

    public ConversionKind getReverseConversion() {
        return reverseConversion;
    }

    public void setReverseConversion(ConversionKind reverseConversion) {
        this.reverseConversion = reverseConversion;
    }
}
