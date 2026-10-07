package dev.c9tech.entityutils.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import dev.c9tech.entityutils.generator.MapperGenerator;
import dev.c9tech.entityutils.ui.GenerateMapperDialog;
import org.jetbrains.annotations.NotNull;

public class GenerateMapperAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiClass currentClass = getContextClass(e);
        PsiClass sourceClass = null;
        PsiClass targetClass = null;

        if (currentClass != null) {
            String name = currentClass.getName();
            if (name != null && (name.endsWith("Dto") || name.endsWith("DTO") || name.endsWith("Bean"))) {
                targetClass = currentClass;
                sourceClass = dev.c9tech.entityutils.util.PsiClassUtil.findRelatedClass(project, currentClass);
            } else {
                sourceClass = currentClass;
                targetClass = dev.c9tech.entityutils.util.PsiClassUtil.findRelatedClass(project, currentClass);
            }
        }

        GenerateMapperDialog dialog = new GenerateMapperDialog(project, sourceClass, targetClass);
        if (dialog.showAndGet()) {
            PsiClass targetClassToUse = dialog.getTargetClass();

            // Auto-create or update DTO class if requested
            if (dialog.isCreateDto()) {
                PsiDirectory dtoDir = dialog.getOrCreateDtoTargetDirectory();
                if (dtoDir != null) {
                    PsiClass createdDto = dev.c9tech.entityutils.generator.DtoGenerator.generateDtoFromMappings(
                            project,
                            dtoDir,
                            dialog.getTargetPackageName(),
                            dialog.getTargetSimpleClassName(),
                            dialog.getDtoStyle(),
                            dialog.getMappingItems(),
                            false
                    );
                    if (createdDto != null) {
                        targetClassToUse = createdDto;
                    }
                }
            }

            PsiDirectory targetDir = dialog.getOrCreateTargetDirectory();
            PsiClass result = MapperGenerator.generateOrUpdateMapper(
                    project,
                    dialog.getSourceClass(),
                    targetClassToUse,
                    dialog.getTargetClassQualifiedName(),
                    dialog.getExistingMapperClass(),
                    targetDir,
                    dialog.getMapperPackageName(),
                    dialog.getMapperClassName(),
                    dialog.getMappingItems(),
                    dialog.getMapperOptions()
            );

            if (result != null) {
                String successMsg = dialog.isCreateDto()
                        ? "DTO and EntityMapper generated successfully!"
                        : "EntityMapper generated successfully!";
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("EntityUtils Notification Group")
                        .createNotification("EntityUtils", successMsg, NotificationType.INFORMATION)
                        .notify(project);
            }
        }
    }

    private PsiClass getContextClass(AnActionEvent e) {
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement != null) {
            PsiClass cls = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
            if (cls != null) {
                return cls;
            }
        }

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            if (e.getData(CommonDataKeys.EDITOR) != null) {
                int offset = e.getData(CommonDataKeys.EDITOR).getCaretModel().getOffset();
                PsiElement elementAtCaret = javaFile.findElementAt(offset);
                PsiClass enclosingClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass.class);
                if (enclosingClass != null) {
                    return enclosingClass;
                }
            }
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }
        return null;
    }
}
