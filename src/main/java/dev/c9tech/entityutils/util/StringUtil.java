package dev.c9tech.entityutils.util;

import java.util.Locale;

public class StringUtil {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (str.length() == 1) {
            return str.toUpperCase(Locale.ROOT);
        }
        // Handle special cases like mStatus -> MStatus, etc.
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String uncapitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (str.length() == 1) {
            return str.toLowerCase(Locale.ROOT);
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    public static String getGetterMethodName(String fieldName, boolean isBoolean) {
        if (isEmpty(fieldName)) {
            return "";
        }
        String prefix = isBoolean ? "is" : "get";
        // If field already starts with is (e.g. isValid), don't prefix with get/is again if boolean
        if (isBoolean && fieldName.startsWith("is") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2))) {
            return fieldName;
        }
        return prefix + capitalize(fieldName);
    }

    public static String getSetterMethodName(String fieldName) {
        if (isEmpty(fieldName)) {
            return "";
        }
        // If field is boolean like isValid, setter is setValid or setIsValid
        if (fieldName.startsWith("is") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2))) {
            return "set" + fieldName.substring(2);
        }
        return "set" + capitalize(fieldName);
    }

    public static String extractPropertyNameFromMethod(String methodName) {
        if (isEmpty(methodName)) {
            return "";
        }
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return uncapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return uncapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return uncapitalize(methodName.substring(2));
        }
        return methodName;
    }
}
