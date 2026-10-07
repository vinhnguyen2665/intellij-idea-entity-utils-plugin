package dev.c9tech.entityutils.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PropertyUtilBase;

import java.util.*;

public class PsiClassUtil {

    public static class PropertyDescriptor {
        private final String name;
        private final String typeText;
        private final PsiField field;

        public PropertyDescriptor(String name, String typeText, PsiField field) {
            this.name = name;
            this.typeText = typeText;
            this.field = field;
        }

        public String getName() {
            return name;
        }

        public String getTypeText() {
            return typeText;
        }

        public PsiField getField() {
            return field;
        }
    }

    /**
     * Collects all non-static fields of a class, optionally including superclasses.
     */
    public static List<PsiField> getAllFields(PsiClass psiClass, boolean includeSuper) {
        List<PsiField> result = new ArrayList<>();
        if (psiClass == null) {
            return result;
        }

        Set<String> seenNames = new HashSet<>();
        PsiClass current = psiClass;
        while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
            for (PsiField field : current.getFields()) {
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                if (seenNames.add(field.getName())) {
                    result.add(field);
                }
            }
            if (!includeSuper) {
                break;
            }
            current = current.getSuperClass();
        }
        return result;
    }

    /**
     * Collects properties from fields, falling back to getter methods if fields are absent.
     */
    public static List<PropertyDescriptor> getAllProperties(PsiClass psiClass) {
        List<PropertyDescriptor> result = new ArrayList<>();
        if (psiClass == null) return result;

        Set<String> seen = new HashSet<>();
        List<PsiField> fields = getAllFields(psiClass, true);
        for (PsiField field : fields) {
            if (seen.add(field.getName())) {
                result.add(new PropertyDescriptor(field.getName(), field.getType().getPresentableText(), field));
            }
        }

        // If no fields found (e.g. record, interface, or methods only)
        if (result.isEmpty()) {
            for (PsiMethod method : psiClass.getAllMethods()) {
                if (method.hasModifierProperty(PsiModifier.STATIC)) continue;
                String methodName = method.getName();
                if ((methodName.startsWith("get") && methodName.length() > 3) ||
                        (methodName.startsWith("is") && methodName.length() > 2)) {
                    if (method.getParameterList().isEmpty() && method.getReturnType() != null) {
                        String propName = StringUtil.extractPropertyNameFromMethod(methodName);
                        if (seen.add(propName)) {
                            result.add(new PropertyDescriptor(propName, method.getReturnType().getPresentableText(), null));
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Checks if class or field has Lombok annotations that synthesize getters.
     */
    public static boolean hasLombokGetter(PsiClass psiClass, PsiField field) {
        if (psiClass == null) return false;
        if (field != null && (field.hasAnnotation("lombok.Getter") || field.hasAnnotation("Getter"))) {
            return true;
        }
        return psiClass.hasAnnotation("lombok.Data") || psiClass.hasAnnotation("Data")
                || psiClass.hasAnnotation("lombok.Getter") || psiClass.hasAnnotation("Getter")
                || psiClass.hasAnnotation("lombok.Value") || psiClass.hasAnnotation("Value");
    }

    /**
     * Checks if class or field has Lombok annotations that synthesize setters.
     */
    public static boolean hasLombokSetter(PsiClass psiClass, PsiField field) {
        if (psiClass == null) return false;
        if (field != null && (field.hasAnnotation("lombok.Setter") || field.hasAnnotation("Setter"))) {
            return true;
        }
        return psiClass.hasAnnotation("lombok.Data") || psiClass.hasAnnotation("Data")
                || psiClass.hasAnnotation("lombok.Setter") || psiClass.hasAnnotation("Setter");
    }

    /**
     * Finds getter method name for field in class. Falls back to standard naming if Lombok is present.
     */
    public static String findGetterName(PsiClass psiClass, PsiField field) {
        if (field == null) return null;
        PsiMethod getter = PropertyUtilBase.findGetterForField(field);
        if (getter != null) {
            return getter.getName();
        }
        boolean isBool = PsiTypes.booleanType().equals(field.getType())
                || "java.lang.Boolean".equals(field.getType().getCanonicalText());
        return StringUtil.getGetterMethodName(field.getName(), isBool);
    }

    /**
     * Finds setter method name for field in class. Falls back to standard naming if Lombok is present.
     */
    public static String findSetterName(PsiClass psiClass, PsiField field) {
        if (field == null) return null;
        PsiMethod setter = PropertyUtilBase.findSetterForField(field);
        if (setter != null) {
            return setter.getName();
        }
        return StringUtil.getSetterMethodName(field.getName());
    }

    /**
     * Finds matching field in target class by name (case-insensitive and ignoring underscores).
     */
    public static PsiField findMatchingField(PsiClass targetClass, String sourceFieldName) {
        if (targetClass == null || sourceFieldName == null) {
            return null;
        }
        String normalizedSource = normalizeName(sourceFieldName);
        for (PsiField field : getAllFields(targetClass, true)) {
            if (normalizeName(field.getName()).equalsIgnoreCase(normalizedSource)) {
                return field;
            }
        }
        return null;
    }

    public static PropertyDescriptor findMatchingProperty(PsiClass targetClass, String sourceFieldName) {
        if (targetClass == null || sourceFieldName == null) {
            return null;
        }
        String normalizedSource = normalizeName(sourceFieldName);
        for (PropertyDescriptor prop : getAllProperties(targetClass)) {
            if (normalizeName(prop.getName()).equalsIgnoreCase(normalizedSource)) {
                return prop;
            }
        }
        return null;
    }

    /**
     * Automatically converts package name to standard .dto convention.
     * e.g. dev.c9tech.fabo.inventory.entity -> dev.c9tech.fabo.inventory.dto
     */
    public static String getSuggestedDtoPackage(String entityPackage) {
        if (StringUtil.isEmpty(entityPackage)) {
            return "dto";
        }
        if (entityPackage.endsWith(".entity")) {
            return entityPackage.substring(0, entityPackage.length() - ".entity".length()) + ".dto";
        }
        if (entityPackage.endsWith(".entities")) {
            return entityPackage.substring(0, entityPackage.length() - ".entities".length()) + ".dto";
        }
        if (entityPackage.endsWith(".model")) {
            return entityPackage.substring(0, entityPackage.length() - ".model".length()) + ".dto";
        }
        if (entityPackage.endsWith(".models")) {
            return entityPackage.substring(0, entityPackage.length() - ".models".length()) + ".dto";
        }
        if (entityPackage.endsWith(".domain")) {
            return entityPackage.substring(0, entityPackage.length() - ".domain".length()) + ".dto";
        }
        if (entityPackage.endsWith(".dao")) {
            return entityPackage.substring(0, entityPackage.length() - ".dao".length()) + ".dto";
        }
        if (entityPackage.endsWith(".dto")) {
            return entityPackage;
        }
        return entityPackage + ".dto";
    }

    public static String getSuggestedDtoClassName(PsiClass sourceClass) {
        if (sourceClass == null || sourceClass.getName() == null) {
            return "Dto";
        }
        String name = sourceClass.getName();
        if (name.endsWith("Dto") || name.endsWith("DTO") || name.endsWith("Bean")) {
            return name;
        }
        return name + "Dto";
    }

    public static String getSuggestedTargetClassQualifiedName(PsiClass sourceClass) {
        if (sourceClass == null) return "";
        String pkg = getPackageName(sourceClass);
        String dtoPkg = getSuggestedDtoPackage(pkg);
        String dtoName = getSuggestedDtoClassName(sourceClass);
        return dtoPkg + "." + dtoName;
    }

    /**
     * Automatically attempts to find related DTO or Entity class in the project.
     * Prioritizes package ....dto
     */
    public static PsiClass findRelatedClass(Project project, PsiClass sourceClass) {
        if (project == null || sourceClass == null) return null;
        String name = sourceClass.getName();
        if (name == null) return null;

        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        String pkg = getPackageName(sourceClass);
        String suggestedDtoPkg = getSuggestedDtoPackage(pkg);

        List<String> candidates = new ArrayList<>();
        if (name.endsWith("Dto") || name.endsWith("DTO") || name.endsWith("Bean")) {
            String base = name.replaceAll("(?i)(Dto|Bean)$", "");
            candidates.add(base);
        } else {
            candidates.add(name + "Dto");
            candidates.add(name + "DTO");
            candidates.add(name + "Bean");
            candidates.add(name + "Vo");
            candidates.add(name + "VO");
            candidates.add("AC" + name);
        }

        // 1. Search in suggested ....dto package first!
        for (String candidate : candidates) {
            PsiClass found = facade.findClass(suggestedDtoPkg + "." + candidate, scope);
            if (found != null) return found;
        }

        // 2. Search in same package or subpackages
        for (String candidate : candidates) {
            if (!pkg.isEmpty()) {
                PsiClass found = facade.findClass(pkg + "." + candidate, scope);
                if (found != null) return found;
                found = facade.findClass(pkg + ".dto." + candidate, scope);
                if (found != null) return found;
                found = facade.findClass(pkg + ".beans." + candidate, scope);
                if (found != null) return found;
            }
        }

        // 3. Search project-wide by simple name
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        for (String candidate : candidates) {
            PsiClass[] matches = cache.getClassesByName(candidate, scope);
            if (matches.length > 0) {
                return matches[0];
            }
        }
        return null;
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    public static String getPackageName(PsiClass psiClass) {
        if (psiClass == null) return "";
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile) psiFile).getPackageName();
        }
        return "";
    }

    public static boolean isAuditField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.equals("createdat") || lower.equals("created_at")
                || lower.equals("updatedat") || lower.equals("updated_at")
                || lower.equals("createdby") || lower.equals("created_by")
                || lower.equals("updatedby") || lower.equals("updated_by")
                || lower.equals("createdate") || lower.equals("create_date")
                || lower.equals("updatedate") || lower.equals("update_date")
                || lower.equals("creatorid") || lower.equals("creator_id")
                || lower.equals("updaterid") || lower.equals("updater_id")
                || lower.equals("deleteflg") || lower.equals("delete_flg")
                || lower.equals("isdeleted") || lower.equals("is_deleted");
    }

    public static PsiClass findClass(Project project, String qualifiedName) {
        if (project == null || qualifiedName == null) return null;
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
    }
}
