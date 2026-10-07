package dev.c9tech.entityutils.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import dev.c9tech.entityutils.generator.DtoGenerator;
import dev.c9tech.entityutils.ui.ClassChooserUtil;
import dev.c9tech.entityutils.ui.GenerateDtoDialog;
import dev.c9tech.entityutils.ui.GenerateMapperDialog;
import org.jetbrains.annotations.NotNull;

public class GenerateDtoAction extends AnAction {

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

        PsiClass entityClass = getTargetClass(e);
        if (entityClass == null) {
            entityClass = ClassChooserUtil.chooseClass(project, "Choose Entity Class to Generate DTO", null);
            if (entityClass == null) {
                return;
            }
        }

        GenerateDtoDialog dialog = new GenerateDtoDialog(project, entityClass);
        if (dialog.showAndGet()) {
            PsiDirectory targetDir = dialog.getOrCreateTargetDirectory();
            if (targetDir == null) {
                Messages.showErrorDialog(project, "Could not determine target directory.", "EntityUtils Error");
                return;
            }

            PsiClass createdDto = DtoGenerator.generateDto(
                    project,
                    targetDir,
                    dialog.getTargetPackageName(),
                    dialog.getDtoClassName(),
                    dialog.getDtoStyle(),
                    dialog.getFieldItems(),
                    dialog.isSerializable()
            );

            NotificationGroupManager.getInstance()
                    .getNotificationGroup("EntityUtils Notification Group")
                    .createNotification("EntityUtils", "DTO created: " + dialog.getDtoClassName(), NotificationType.INFORMATION)
                    .notify(project);

            // If user checked open mapper after
            if (dialog.isOpenMapperAfter() && createdDto != null) {
                GenerateMapperDialog mapperDialog = new GenerateMapperDialog(project, entityClass, createdDto);
                if (mapperDialog.showAndGet()) {
                    PsiDirectory mapperDir = mapperDialog.getOrCreateTargetDirectory();
                    dev.c9tech.entityutils.generator.MapperGenerator.generateOrUpdateMapper(
                            project,
                            mapperDialog.getSourceClass(),
                            mapperDialog.getTargetClass(),
                            mapperDialog.getTargetClassQualifiedName(),
                            mapperDialog.getExistingMapperClass(),
                            mapperDir,
                            mapperDialog.getMapperPackageName(),
                            mapperDialog.getMapperClassName(),
                            mapperDialog.getMappingItems(),
                            mapperDialog.getMapperOptions()
                    );
                }
            }
        }
    }

    private PsiClass getTargetClass(AnActionEvent e) {
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
            int offset = 0;
            if (e.getData(CommonDataKeys.EDITOR) != null) {
                offset = e.getData(CommonDataKeys.EDITOR).getCaretModel().getOffset();
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
