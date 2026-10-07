package dev.c9tech.entityutils.generator;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.TypeConversionUtil;
import dev.c9tech.entityutils.model.FieldMappingItem.ConversionKind;

public class TypeConversionHelper {

    public static final String[] COMMON_TYPES = {
            "String",
            "Long",
            "Integer",
            "Double",
            "Float",
            "Boolean",
            "Date",
            "Timestamp",
            "BigDecimal",
            "int",
            "long",
            "double",
            "boolean"
    };

    public static ConversionKind detectConversion(PsiType srcType, PsiType destType) {
        if (srcType == null || destType == null) {
            return ConversionKind.DIRECT;
        }

        if (TypeConversionUtil.isAssignable(destType, srcType)) {
            return ConversionKind.DIRECT;
        }

        String srcCanonical = srcType.getCanonicalText();
        String destCanonical = destType.getCanonicalText();

        return detectConversion(srcCanonical, destCanonical);
    }

    public static ConversionKind detectConversion(String srcTypeName, String destTypeName) {
        if (srcTypeName == null || destTypeName == null) {
            return ConversionKind.DIRECT;
        }

        String src = simplifyType(srcTypeName);
        String dest = simplifyType(destTypeName);

        if (src.equalsIgnoreCase(dest)) {
            return ConversionKind.DIRECT;
        }

        // Date <-> Timestamp
        if (isDate(src) && isTimestamp(dest)) {
            return ConversionKind.DATE_TO_TIMESTAMP;
        }
        if (isTimestamp(src) && isDate(dest)) {
            return ConversionKind.TIMESTAMP_TO_DATE;
        }

        // String <-> Number
        if (isString(src)) {
            if (isDouble(dest)) return ConversionKind.STRING_TO_DOUBLE;
            if (isInteger(dest)) return ConversionKind.STRING_TO_INTEGER;
            if (isLong(dest)) return ConversionKind.STRING_TO_LONG;
        }

        if (isString(dest)) {
            if (isDouble(src)) return ConversionKind.DOUBLE_TO_STRING;
            if (isInteger(src)) return ConversionKind.INTEGER_TO_STRING;
            if (isLong(src)) return ConversionKind.LONG_TO_STRING;
            return ConversionKind.NUMBER_TO_STRING;
        }

        return ConversionKind.DIRECT;
    }

    public static String generateValueExpression(String srcVar, String getterName, ConversionKind kind) {
        String getterCall = srcVar + "." + getterName + "()";

        switch (kind) {
            case DATE_TO_TIMESTAMP:
                return "new java.sql.Timestamp(" + getterCall + ".getTime())";
            case TIMESTAMP_TO_DATE:
                return "new java.util.Date(" + getterCall + ".getTime())";
            case STRING_TO_DOUBLE:
                return "Double.valueOf(" + getterCall + ")";
            case DOUBLE_TO_STRING:
            case INTEGER_TO_STRING:
            case LONG_TO_STRING:
            case NUMBER_TO_STRING:
                return "String.valueOf(" + getterCall + ")";
            case STRING_TO_INTEGER:
                return "Integer.valueOf(" + getterCall + ")";
            case STRING_TO_LONG:
                return "Long.valueOf(" + getterCall + ")";
            case DIRECT:
            default:
                return getterCall;
        }
    }

    public static String simplifyType(String fullType) {
        if (fullType == null) return "";
        int dot = fullType.lastIndexOf('.');
        return dot >= 0 ? fullType.substring(dot + 1) : fullType;
    }

    public static boolean isPrimitiveType(String typeName) {
        if (typeName == null) return false;
        String simple = simplifyType(typeName);
        return simple.equals("int")
                || simple.equals("long")
                || simple.equals("double")
                || simple.equals("float")
                || simple.equals("boolean")
                || simple.equals("short")
                || simple.equals("byte")
                || simple.equals("char");
    }

    public static boolean isPrimitive(PsiType type) {
        if (type == null) return false;
        return type.equals(PsiTypes.booleanType())
                || type.equals(PsiTypes.intType())
                || type.equals(PsiTypes.longType())
                || type.equals(PsiTypes.doubleType())
                || type.equals(PsiTypes.floatType())
                || type.equals(PsiTypes.shortType())
                || type.equals(PsiTypes.byteType())
                || type.equals(PsiTypes.charType());
    }

    private static boolean isDate(String name) {
        return "Date".equalsIgnoreCase(name) || "java.util.Date".equalsIgnoreCase(name);
    }

    private static boolean isTimestamp(String name) {
        return "Timestamp".equalsIgnoreCase(name) || "java.sql.Timestamp".equalsIgnoreCase(name);
    }

    private static boolean isString(String name) {
        return "String".equalsIgnoreCase(name) || "java.lang.String".equalsIgnoreCase(name);
    }

    private static boolean isDouble(String name) {
        return "Double".equalsIgnoreCase(name) || "double".equalsIgnoreCase(name);
    }

    private static boolean isInteger(String name) {
        return "Integer".equalsIgnoreCase(name) || "int".equalsIgnoreCase(name);
    }

    private static boolean isLong(String name) {
        return "Long".equalsIgnoreCase(name) || "long".equalsIgnoreCase(name);
    }
}
