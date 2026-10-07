package dev.c9tech.entityutils.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import dev.c9tech.entityutils.model.DtoFieldItem;
import dev.c9tech.entityutils.model.DtoStyle;
import dev.c9tech.entityutils.model.FieldMappingItem;
import dev.c9tech.entityutils.util.StringUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DtoGenerator {

    public static PsiClass generateDtoFromMappings(Project project,
                                                  PsiDirectory targetDirectory,
                                                  String packageName,
                                                  String dtoClassName,
                                                  DtoStyle style,
                                                  List<FieldMappingItem> mappingItems,
                                                  boolean implementSerializable) {

        List<DtoFieldItem> fieldItems = mappingItems.stream()
                .filter(FieldMappingItem::isSelected)
                .map(item -> new DtoFieldItem(item.getTargetFieldName(), item.getTargetFieldTypeText(), item.getSourceField()))
                .collect(Collectors.toList());

        return generateDto(project, targetDirectory, packageName, dtoClassName, style, fieldItems, implementSerializable);
    }

    public static PsiClass generateDto(Project project,
                                      PsiDirectory targetDirectory,
                                      String packageName,
                                      String dtoClassName,
                                      DtoStyle style,
                                      List<DtoFieldItem> fieldItems,
                                      boolean implementSerializable) {

        List<DtoFieldItem> selectedFields = fieldItems.stream()
                .filter(DtoFieldItem::isSelected)
                .collect(Collectors.toList());

        String fileContent = buildDtoClassSource(packageName, dtoClassName, style, selectedFields, implementSerializable);

        final PsiClass[] createdClass = new PsiClass[1];

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
            PsiJavaFile newFile = (PsiJavaFile) fileFactory.createFileFromText(
                    dtoClassName + ".java",
                    JavaFileType.INSTANCE,
                    fileContent
            );

            // Delete old file if already exists to avoid collision
            PsiFile existing = targetDirectory.findFile(dtoClassName + ".java");
            if (existing != null) {
                existing.delete();
            }

            // Add file to directory
            PsiElement addedElement = targetDirectory.add(newFile);
            if (addedElement instanceof PsiJavaFile) {
                PsiJavaFile addedJavaFile = (PsiJavaFile) addedElement;

                // Reformat and shorten references
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedJavaFile);
                CodeStyleManager.getInstance(project).reformat(addedJavaFile);

                PsiClass[] classes = addedJavaFile.getClasses();
                if (classes.length > 0) {
                    createdClass[0] = classes[0];
                }

                // Open in editor
                VirtualFile virtualFile = addedJavaFile.getVirtualFile();
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
            }
        });

        return createdClass[0];
    }

    private static String buildDtoClassSource(String packageName,
                                             String dtoClassName,
                                             DtoStyle style,
                                             List<DtoFieldItem> fields,
                                             boolean implementSerializable) {
        StringBuilder sb = new StringBuilder();

        if (StringUtil.isNotEmpty(packageName)) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        // Collect imports
        Set<String> imports = new HashSet<>();
        if (implementSerializable) {
            imports.add("java.io.Serializable");
        }
        if (style == DtoStyle.LOMBOK) {
            imports.add("lombok.Data");
            imports.add("lombok.NoArgsConstructor");
            imports.add("lombok.AllArgsConstructor");
        }

        for (DtoFieldItem item : fields) {
            String typeText = item.getTypeText();
            if (typeText != null) {
                if (typeText.contains("Date") && !typeText.contains("java.sql.Date")) {
                    imports.add("java.util.Date");
                }
                if (typeText.contains("Timestamp")) {
                    imports.add("java.sql.Timestamp");
                }
                if (typeText.contains("BigDecimal")) {
                    imports.add("java.math.BigDecimal");
                }
                if (typeText.contains("List")) {
                    imports.add("java.util.List");
                }
            }

            PsiType type = item.getType();
            if (type instanceof PsiClassType) {
                PsiClass resolved = ((PsiClassType) type).resolve();
                if (resolved != null && resolved.getQualifiedName() != null) {
                    String qName = resolved.getQualifiedName();
                    if (!qName.startsWith("java.lang.")) {
                        imports.add(qName);
                    }
                }
            }
        }

        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        if (!imports.isEmpty()) {
            sb.append("\n");
        }

        if (style == DtoStyle.RECORD) {
            sb.append("public record ").append(dtoClassName).append("(\n");
            for (int i = 0; i < fields.size(); i++) {
                DtoFieldItem field = fields.get(i);
                sb.append("        ").append(field.getTypeText()).append(" ").append(field.getName());
                if (i < fields.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("\n)");
            if (implementSerializable) {
                sb.append(" implements Serializable");
            }
            sb.append(" {\n");
            sb.append("}\n");
            return sb.toString();
        }

        if (style == DtoStyle.LOMBOK) {
            sb.append("@Data\n");
            sb.append("@NoArgsConstructor\n");
            sb.append("@AllArgsConstructor\n");
        }

        sb.append("public class ").append(dtoClassName);
        if (implementSerializable) {
            sb.append(" implements Serializable");
        }
        sb.append(" {\n");

        if (implementSerializable) {
            sb.append("    private static final long serialVersionUID = 1L;\n\n");
        }

        // Fields
        for (DtoFieldItem field : fields) {
            sb.append("    private ").append(field.getTypeText()).append(" ").append(field.getName()).append(";\n");
        }
        sb.append("\n");

        // If Java Bean, generate constructor, getters & setters
        if (style == DtoStyle.JAVA_BEAN) {
            sb.append("    public ").append(dtoClassName).append("() {\n    }\n\n");

            for (DtoFieldItem field : fields) {
                String capName = StringUtil.capitalize(field.getName());
                String typeText = field.getTypeText();
                boolean isBool = "boolean".equalsIgnoreCase(typeText);
                String getterName = StringUtil.getGetterMethodName(field.getName(), isBool);
                String setterName = StringUtil.getSetterMethodName(field.getName());

                // Getter
                sb.append("    public ").append(typeText).append(" ").append(getterName).append("() {\n");
                sb.append("        return this.").append(field.getName()).append(";\n");
                sb.append("    }\n\n");

                // Setter
                sb.append("    public void ").append(setterName).append("(").append(typeText).append(" ").append(field.getName()).append(") {\n");
                sb.append("        this.").append(field.getName()).append(" = ").append(field.getName()).append(";\n");
                sb.append("    }\n\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }
}
