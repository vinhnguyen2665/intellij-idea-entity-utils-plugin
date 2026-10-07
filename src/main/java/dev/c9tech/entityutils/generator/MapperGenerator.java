package dev.c9tech.entityutils.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import dev.c9tech.entityutils.model.FieldMappingItem;
import dev.c9tech.entityutils.model.MethodNamingStyle;
import dev.c9tech.entityutils.util.StringUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MapperGenerator {

    public static class MapperOptions {
        public boolean generateForwardSingle = true;
        public boolean generateForwardList = true;
        public boolean generateReverseSingle = true;
        public boolean generateReverseList = true;
        public boolean generateMerge = false;
        public MethodNamingStyle namingStyle = MethodNamingStyle.CONVERT_X_TO_Y;
        public boolean isStaticMethods = true;
    }

    public static PsiClass generateOrUpdateMapper(Project project,
                                                 PsiClass sourceClass,
                                                 PsiClass targetClass,
                                                 String targetClassQualifiedName,
                                                 PsiClass existingMapperClass,
                                                 PsiDirectory targetDirectory,
                                                 String mapperPackageName,
                                                 String mapperClassName,
                                                 List<FieldMappingItem> mappingItems,
                                                 MapperOptions options) {

        String destType = targetClass != null ? targetClass.getName() : TypeConversionHelper.simplifyType(targetClassQualifiedName);
        String destQualifiedName = targetClass != null ? targetClass.getQualifiedName() : targetClassQualifiedName;

        List<FieldMappingItem> activeMappings = mappingItems.stream()
                .filter(FieldMappingItem::isSelected)
                .collect(Collectors.toList());

        final PsiClass[] resultClass = new PsiClass[1];

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

            if (existingMapperClass != null) {
                // Append methods to existing class
                addMethodsToClass(project, existingMapperClass, sourceClass, destType, destQualifiedName, activeMappings, options, elementFactory);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(existingMapperClass);
                CodeStyleManager.getInstance(project).reformat(existingMapperClass);
                resultClass[0] = existingMapperClass;

                VirtualFile vf = existingMapperClass.getContainingFile().getVirtualFile();
                if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, true);
                }
            } else if (targetDirectory != null) {
                // Create new Mapper class file
                String sourceCode = buildNewMapperSource(mapperPackageName, mapperClassName, sourceClass, destType, destQualifiedName, activeMappings, options);
                PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                PsiJavaFile newFile = (PsiJavaFile) fileFactory.createFileFromText(
                        mapperClassName + ".java",
                        JavaFileType.INSTANCE,
                        sourceCode
                );

                PsiElement added = targetDirectory.add(newFile);
                if (added instanceof PsiJavaFile) {
                    PsiJavaFile addedFile = (PsiJavaFile) added;
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedFile);
                    CodeStyleManager.getInstance(project).reformat(addedFile);

                    PsiClass[] classes = addedFile.getClasses();
                    if (classes.length > 0) {
                        resultClass[0] = classes[0];
                    }

                    VirtualFile vf = addedFile.getVirtualFile();
                    if (vf != null) {
                        FileEditorManager.getInstance(project).openFile(vf, true);
                    }
                }
            }
        });

        return resultClass[0];
    }

    private static void addMethodsToClass(Project project,
                                         PsiClass mapperClass,
                                         PsiClass sourceClass,
                                         String destType,
                                         String destQualifiedName,
                                         List<FieldMappingItem> mappings,
                                         MapperOptions options,
                                         PsiElementFactory elementFactory) {

        PsiFile containingFile = mapperClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) containingFile;
            importClassIfMissing(javaFile, "java.util.List");
            importClassIfMissing(javaFile, "java.util.ArrayList");
            if (sourceClass.getQualifiedName() != null) {
                importClassIfMissing(javaFile, sourceClass.getQualifiedName());
            }
            if (StringUtil.isNotEmpty(destQualifiedName)) {
                importClassIfMissing(javaFile, destQualifiedName);
            }

            for (FieldMappingItem item : mappings) {
                if (item.getForwardConversion() == FieldMappingItem.ConversionKind.DATE_TO_TIMESTAMP
                        || item.getReverseConversion() == FieldMappingItem.ConversionKind.DATE_TO_TIMESTAMP) {
                    importClassIfMissing(javaFile, "java.sql.Timestamp");
                }
                if (item.getForwardConversion() == FieldMappingItem.ConversionKind.TIMESTAMP_TO_DATE
                        || item.getReverseConversion() == FieldMappingItem.ConversionKind.TIMESTAMP_TO_DATE) {
                    importClassIfMissing(javaFile, "java.util.Date");
                }
            }
        }

        String fwdSingleName = getForwardMethodName(sourceClass.getName(), destType, options.namingStyle);
        String revSingleName = getReverseMethodName(sourceClass.getName(), destType, options.namingStyle);

        if (options.generateForwardSingle) {
            String methodCode = buildForwardSingleMethod(sourceClass.getName(), destType, fwdSingleName, mappings, options.isStaticMethods);
            PsiMethod method = elementFactory.createMethodFromText(methodCode, mapperClass);
            mapperClass.add(method);
        }

        if (options.generateForwardList) {
            String methodCode = buildForwardListMethod(sourceClass.getName(), destType, fwdSingleName, options.namingStyle, options.isStaticMethods);
            PsiMethod method = elementFactory.createMethodFromText(methodCode, mapperClass);
            mapperClass.add(method);
        }

        if (options.generateReverseSingle) {
            String methodCode = buildReverseSingleMethod(sourceClass.getName(), destType, revSingleName, mappings, options.isStaticMethods);
            PsiMethod method = elementFactory.createMethodFromText(methodCode, mapperClass);
            mapperClass.add(method);
        }

        if (options.generateReverseList) {
            String methodCode = buildReverseListMethod(sourceClass.getName(), destType, revSingleName, options.namingStyle, options.isStaticMethods);
            PsiMethod method = elementFactory.createMethodFromText(methodCode, mapperClass);
            mapperClass.add(method);
        }

        if (options.generateMerge) {
            String methodCode = buildMergeMethod(sourceClass.getName(), destType, mappings, options.isStaticMethods);
            PsiMethod method = elementFactory.createMethodFromText(methodCode, mapperClass);
            mapperClass.add(method);
        }
    }

    private static void importClassIfMissing(PsiJavaFile javaFile, String qualifiedName) {
        Project project = javaFile.getProject();
        PsiClass target = JavaPsiFacade.getInstance(project).findClass(qualifiedName, javaFile.getResolveScope());
        if (target != null) {
            JavaCodeStyleManager.getInstance(project).addImport(javaFile, target);
        }
    }

    private static String buildNewMapperSource(String packageName,
                                              String mapperClassName,
                                              PsiClass sourceClass,
                                              String destType,
                                              String destQualifiedName,
                                              List<FieldMappingItem> mappings,
                                              MapperOptions options) {
        StringBuilder sb = new StringBuilder();

        if (StringUtil.isNotEmpty(packageName)) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        // Imports
        Set<String> imports = new HashSet<>();
        imports.add("java.util.List");
        imports.add("java.util.ArrayList");
        if (sourceClass.getQualifiedName() != null) imports.add(sourceClass.getQualifiedName());
        if (StringUtil.isNotEmpty(destQualifiedName)) imports.add(destQualifiedName);

        for (FieldMappingItem item : mappings) {
            if (item.getForwardConversion() == FieldMappingItem.ConversionKind.DATE_TO_TIMESTAMP
                    || item.getReverseConversion() == FieldMappingItem.ConversionKind.DATE_TO_TIMESTAMP) {
                imports.add("java.sql.Timestamp");
            }
            if (item.getForwardConversion() == FieldMappingItem.ConversionKind.TIMESTAMP_TO_DATE
                    || item.getReverseConversion() == FieldMappingItem.ConversionKind.TIMESTAMP_TO_DATE) {
                imports.add("java.util.Date");
            }
        }

        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        sb.append("public class ").append(mapperClassName).append(" {\n\n");

        String fwdSingleName = getForwardMethodName(sourceClass.getName(), destType, options.namingStyle);
        String revSingleName = getReverseMethodName(sourceClass.getName(), destType, options.namingStyle);

        if (options.generateForwardSingle) {
            sb.append(buildForwardSingleMethod(sourceClass.getName(), destType, fwdSingleName, mappings, options.isStaticMethods)).append("\n\n");
        }

        if (options.generateForwardList) {
            sb.append(buildForwardListMethod(sourceClass.getName(), destType, fwdSingleName, options.namingStyle, options.isStaticMethods)).append("\n\n");
        }

        if (options.generateReverseSingle) {
            sb.append(buildReverseSingleMethod(sourceClass.getName(), destType, revSingleName, mappings, options.isStaticMethods)).append("\n\n");
        }

        if (options.generateReverseList) {
            sb.append(buildReverseListMethod(sourceClass.getName(), destType, revSingleName, options.namingStyle, options.isStaticMethods)).append("\n\n");
        }

        if (options.generateMerge) {
            sb.append(buildMergeMethod(sourceClass.getName(), destType, mappings, options.isStaticMethods)).append("\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String getForwardMethodName(String srcType, String destType, MethodNamingStyle namingStyle) {
        if (namingStyle == MethodNamingStyle.TO_TARGET) {
            return "to" + destType;
        }
        return "convert" + srcType + "To" + destType;
    }

    private static String getReverseMethodName(String srcType, String destType, MethodNamingStyle namingStyle) {
        if (namingStyle == MethodNamingStyle.TO_TARGET) {
            return "to" + srcType;
        }
        return "convert" + destType + "To" + srcType;
    }

    private static String buildForwardSingleMethod(String srcType,
                                                   String destType,
                                                   String methodName,
                                                   List<FieldMappingItem> mappings,
                                                   boolean isStatic) {
        String srcVar = StringUtil.uncapitalize(srcType);
        String destVar = StringUtil.uncapitalize(destType);

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(isStatic ? "static " : "").append(destType).append(" ")
                .append(methodName).append("(").append(srcType).append(" ").append(srcVar).append(") {\n");
        sb.append("        if (null == ").append(srcVar).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        ").append(destType).append(" ").append(destVar).append(" = new ").append(destType).append("();\n");

        for (FieldMappingItem item : mappings) {
            if (StringUtil.isEmpty(item.getSourceGetter()) || StringUtil.isEmpty(item.getTargetSetter())) {
                continue;
            }

            String valueExpr = TypeConversionHelper.generateValueExpression(
                    srcVar,
                    item.getSourceGetter(),
                    item.getForwardConversion()
            );

            boolean isPrimitive = TypeConversionHelper.isPrimitiveType(item.getSourceFieldTypeText());
            if (isPrimitive) {
                sb.append("        ").append(destVar).append(".").append(item.getTargetSetter()).append("(").append(valueExpr).append(");\n");
            } else {
                sb.append("        if (null != ").append(srcVar).append(".").append(item.getSourceGetter()).append("()) {\n");
                sb.append("            ").append(destVar).append(".").append(item.getTargetSetter()).append("(").append(valueExpr).append(");\n");
                sb.append("        }\n");
            }
        }

        sb.append("        return ").append(destVar).append(";\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildForwardListMethod(String srcType,
                                                 String destType,
                                                 String singleMethodName,
                                                 MethodNamingStyle namingStyle,
                                                 boolean isStatic) {
        String srcVar = StringUtil.uncapitalize(srcType);
        String srcListVar = srcVar + "Lst";
        String destListVar = StringUtil.uncapitalize(destType) + "Lst";

        String methodName = namingStyle == MethodNamingStyle.TO_TARGET
                ? "to" + destType + "List"
                : singleMethodName;

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(isStatic ? "static " : "").append("List<").append(destType).append("> ")
                .append(methodName).append("(List<").append(srcType).append("> ").append(srcListVar).append(") {\n");
        sb.append("        if (null == ").append(srcListVar).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        List<").append(destType).append("> ").append(destListVar).append(" = new ArrayList<>();\n");
        sb.append("        for (").append(srcType).append(" item : ").append(srcListVar).append(") {\n");
        sb.append("            ").append(destListVar).append(".add(").append(singleMethodName).append("(item));\n");
        sb.append("        }\n");
        sb.append("        return ").append(destListVar).append(";\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildReverseSingleMethod(String srcType,
                                                   String destType,
                                                   String methodName,
                                                   List<FieldMappingItem> mappings,
                                                   boolean isStatic) {
        String srcVar = StringUtil.uncapitalize(destType);
        String destVar = StringUtil.uncapitalize(srcType);

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(isStatic ? "static " : "").append(srcType).append(" ")
                .append(methodName).append("(").append(destType).append(" ").append(srcVar).append(") {\n");
        sb.append("        if (null == ").append(srcVar).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        ").append(srcType).append(" ").append(destVar).append(" = new ").append(srcType).append("();\n");

        for (FieldMappingItem item : mappings) {
            if (StringUtil.isEmpty(item.getTargetGetter()) || StringUtil.isEmpty(item.getSourceSetter())) {
                continue;
            }

            String valueExpr = TypeConversionHelper.generateValueExpression(
                    srcVar,
                    item.getTargetGetter(),
                    item.getReverseConversion()
            );

            boolean isPrimitive = TypeConversionHelper.isPrimitiveType(item.getTargetFieldTypeText());
            if (isPrimitive) {
                sb.append("        ").append(destVar).append(".").append(item.getSourceSetter()).append("(").append(valueExpr).append(");\n");
            } else {
                sb.append("        if (null != ").append(srcVar).append(".").append(item.getTargetGetter()).append("()) {\n");
                sb.append("            ").append(destVar).append(".").append(item.getSourceSetter()).append("(").append(valueExpr).append(");\n");
                sb.append("        }\n");
            }
        }

        sb.append("        return ").append(destVar).append(";\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildReverseListMethod(String srcType,
                                                 String destType,
                                                 String singleMethodName,
                                                 MethodNamingStyle namingStyle,
                                                 boolean isStatic) {
        String srcVar = StringUtil.uncapitalize(destType);
        String srcListVar = srcVar + "Lst";
        String destListVar = StringUtil.uncapitalize(srcType) + "Lst";

        String methodName = namingStyle == MethodNamingStyle.TO_TARGET
                ? "to" + srcType + "List"
                : singleMethodName;

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(isStatic ? "static " : "").append("List<").append(srcType).append("> ")
                .append(methodName).append("(List<").append(destType).append("> ").append(srcListVar).append(") {\n");
        sb.append("        if (null == ").append(srcListVar).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        List<").append(srcType).append("> ").append(destListVar).append(" = new ArrayList<>();\n");
        sb.append("        for (").append(destType).append(" item : ").append(srcListVar).append(") {\n");
        sb.append("            ").append(destListVar).append(".add(").append(singleMethodName).append("(item));\n");
        sb.append("        }\n");
        sb.append("        return ").append(destListVar).append(";\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildMergeMethod(String srcType,
                                           String destType,
                                           List<FieldMappingItem> mappings,
                                           boolean isStatic) {
        String entityType = srcType;
        String dtoType = destType;
        String srcVar = "src";
        String desVar = "des";

        String methodName = "merge" + entityType;

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(isStatic ? "static " : "").append(entityType).append(" ")
                .append(methodName).append("(").append(dtoType).append(" ").append(srcVar).append(", ")
                .append(entityType).append(" ").append(desVar).append(") {\n");
        sb.append("        if (null == ").append(srcVar).append(" || null == ").append(desVar).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");

        for (FieldMappingItem item : mappings) {
            if (StringUtil.isEmpty(item.getTargetGetter()) || StringUtil.isEmpty(item.getSourceSetter())) {
                continue;
            }

            String valueExpr = TypeConversionHelper.generateValueExpression(
                    srcVar,
                    item.getTargetGetter(),
                    item.getReverseConversion()
            );

            boolean isPrimitive = TypeConversionHelper.isPrimitiveType(item.getTargetFieldTypeText());
            if (isPrimitive) {
                sb.append("        ").append(desVar).append(".").append(item.getSourceSetter()).append("(").append(valueExpr).append(");\n");
            } else {
                sb.append("        if (null != ").append(srcVar).append(".").append(item.getTargetGetter()).append("()) {\n");
                sb.append("            ").append(desVar).append(".").append(item.getSourceSetter()).append("(").append(valueExpr).append(");\n");
                sb.append("        }\n");
            }
        }

        sb.append("        return ").append(desVar).append(";\n");
        sb.append("    }");
        return sb.toString();
    }
}
