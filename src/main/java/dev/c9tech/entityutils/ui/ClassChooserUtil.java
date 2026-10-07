package dev.c9tech.entityutils.ui;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;

public class ClassChooserUtil {

    public static PsiClass chooseClass(Project project, String title, PsiClass initialClass) {
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
                .createProjectScopeChooser(title, initialClass);
        chooser.showDialog();
        return chooser.getSelected();
    }

    public static String choosePackage(Project project, String title, String initialPackage) {
        PackageChooserDialog dialog = new PackageChooserDialog(title, project);
        if (initialPackage != null && !initialPackage.isEmpty()) {
            PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(initialPackage);
            if (psiPackage != null) {
                dialog.selectPackage(initialPackage);
            }
        }
        dialog.show();
        PsiPackage selectedPackage = dialog.getSelectedPackage();
        return selectedPackage != null ? selectedPackage.getQualifiedName() : null;
    }
}
