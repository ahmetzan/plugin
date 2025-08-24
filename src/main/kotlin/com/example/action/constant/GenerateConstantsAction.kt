package com.example.action.constant

import com.intellij.ide.util.PackageChooserDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope

class GenerateConstantsAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = element is PsiClass
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiClass ?: return

        // Paket seçimi
        val packageChooser = PackageChooserDialog("Select Package for Constants", project)
        packageChooser.show()
        val selectedPackage = packageChooser.selectedPackage ?: return
        val directory = selectedPackage.directories.firstOrNull() ?: return

        // Class adı input
        val defaultClassName = "${psiClass.name}Constants"
        val newClassName = Messages.showInputDialog(
            project,
            "Enter constants class name:",
            "Constants Class Name",
            Messages.getQuestionIcon(),
            defaultClassName,
            null
        ) ?: return

        // Case format seçimi
        val options = arrayOf("SNAKE_CASE", "camelCase", "PascalCase")
        val selectedCase = Messages.showEditableChooseDialog(
            "Select constant name format:",
            "Constant Case Format",
            Messages.getQuestionIcon(),
            options,
            options[0],
            null
        ) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val psiFactory = JavaPsiFacade.getElementFactory(project)
            val newClass = JavaDirectoryService.getInstance().createClass(directory, newClassName)
            newClass.modifierList?.setModifierProperty(PsiModifier.FINAL, true)

            // Lombok @NoArgsConstructor(access = AccessLevel.PRIVATE)
            val annotation = psiFactory.createAnnotationFromText(
                "@lombok.NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)", newClass
            )
            newClass.modifierList?.addBefore(annotation, newClass.firstChild)

            // Import lombok classes
            val file = newClass.containingFile as? PsiJavaFile
            val psiFacade = JavaPsiFacade.getInstance(project)
            val noArgsClass = psiFacade.findClass("lombok.NoArgsConstructor", GlobalSearchScope.allScope(project))
            val accessLevelClass = psiFacade.findClass("lombok.AccessLevel", GlobalSearchScope.allScope(project))
            if (noArgsClass != null) {
                file?.importList?.add(psiFactory.createImportStatement(noArgsClass))
            }
            if (accessLevelClass != null) {
                file?.importList?.add(psiFactory.createImportStatement(accessLevelClass))
            }

            val classConstName = psiClass.name?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.uppercase()
            val classConstValue = formatName(psiClass.name ?: "", selectedCase)
            val classConstField = psiFactory.createFieldFromText(
                "public static final String $classConstName = \"$classConstValue\";",
                newClass
            )
            newClass.add(classConstField)

            // Field constant ekleme
            psiClass.allFields.forEach { field ->
                val constName = field.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
                val value = formatName(field.name, selectedCase)
                val constField = psiFactory.createFieldFromText(
                    "public static final String $constName = \"${value}\";",
                    newClass
                )
                newClass.add(constField)
            }
        }
    }

    private fun formatName(name: String, caseType: String): String {
        return when (caseType) {
            "SNAKE_CASE" -> name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
            "camelCase" -> name.replaceFirstChar { it.lowercaseChar() }
            "PascalCase" -> name.replaceFirstChar { it.uppercaseChar() }
            else -> name
        }
    }
}
