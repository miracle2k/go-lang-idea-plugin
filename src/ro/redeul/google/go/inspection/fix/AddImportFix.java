package ro.redeul.google.go.inspection.fix;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import ro.redeul.google.go.lang.psi.GoFile;
import ro.redeul.google.go.lang.psi.toplevel.GoImportDeclaration;
import ro.redeul.google.go.lang.psi.toplevel.GoImportDeclarations;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static ro.redeul.google.go.lang.psi.utils.GoPsiUtils.getPrevSiblingIfItsWhiteSpaceOrComment;

public class AddImportFix implements QuestionAction {
    private final List<String> pathsToImport = new ArrayList<String>();
    private final List<String> sdkPackages;
    private final GoFile file;
    private final Document document;
    private final Editor editor;

    public AddImportFix(List<String> sdkPackages, List<String> projectPackages, GoFile file, Editor editor) {
        this.sdkPackages = sdkPackages;
        this.file = file;
        this.editor = editor;
        this.document = editor.getDocument();
        pathsToImport.addAll(projectPackages);
        pathsToImport.addAll(sdkPackages);
    }

    @Override
    public boolean execute() {
        if (pathsToImport == null || pathsToImport.isEmpty()) {
            return true;
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                if (pathsToImport.size() == 1) {
                    addImport(pathsToImport.get(0));
                } else {
                    JBPopupFactory popup = JBPopupFactory.getInstance();
                    popup.createListPopup(new ChoosePackagePopupStep()).showInBestPositionFor(editor);
                }
            }
        });
        return true;
    }

    private void addImport(final String pathToImport) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                doAddImport(pathToImport);
            }
        });
    }

    private void doAddImport(String pathToImport) {
        GoImportDeclarations[] ids = file.getImportDeclarations();
        if (ids.length == 0) {
            addImportUnderPackage(pathToImport);
            return;
        }

        GoImportDeclarations importDeclarations = ids[ids.length - 1];
        GoImportDeclaration[] imports = importDeclarations.getDeclarations();
        if (imports.length == 0) {
            addImportUnderPackage(pathToImport);
            return;
        }

        GoImportDeclaration lastImport = imports[imports.length - 1];

        PsiElement lastChild = getPrevSiblingIfItsWhiteSpaceOrComment(importDeclarations.getLastChild());
        if (lastChild == null) {
            addImportUnderPackage(pathToImport);
            return;
        }

        if (")".equals(lastChild.getText())) {
            document.insertString(lastChild.getTextOffset(), "    \"" + pathToImport + "\"\n");
        } else {
            String oldImport = lastImport.getText();
            int start = lastImport.getTextOffset();
            int end = start + lastImport.getTextLength();
            document.replaceString(start, end, String.format("(\n    %s\n    \"%s\"\n)", oldImport, pathToImport));
        }
    }

    private void addImportUnderPackage(String pathToImport) {
        int insertPoint = file.getPackage().getTextRange().getEndOffset();
        document.insertString(insertPoint, String.format("\n\nimport \"%s\"", pathToImport));
    }

    private class ChoosePackagePopupStep extends BaseListPopupStep<String> {
        public ChoosePackagePopupStep() {
            super("Choose package to import", pathsToImport);
        }

        @Override
        public boolean isSpeedSearchEnabled() {
            return true;
        }

        @Override
        public boolean isAutoSelectionEnabled() {
            return false;
        }

        @Override
        public Icon getIconFor(String aValue) {
            return sdkPackages.contains(aValue) ? PlatformIcons.LIBRARY_ICON : PlatformIcons.PACKAGE_ICON;
        }

        @Override
        public PopupStep onChosen(String selectedValue, boolean finalChoice) {
            if (finalChoice) {
                addImport(selectedValue);
            }
            return FINAL_CHOICE;
        }
    }
}