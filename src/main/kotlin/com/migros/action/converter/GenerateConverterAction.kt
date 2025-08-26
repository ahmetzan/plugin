package com.migros.action.converter

import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.migros.utils.NotificationUtils

class GenerateConverterAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = element is PsiClass
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val sourceClass = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiClass ?: return

        // 1. DTO seçimi
        val chooser = TreeClassChooserFactory.getInstance(project)
            .createAllProjectScopeChooser("Select Target DTO Class")
        chooser.showDialog()
        val targetClass = chooser.selected ?: return

        // 2. Package seçimi
        val packageChooser = PackageChooserDialog("Select Package for Converter", project)
        packageChooser.show()
        val selectedPackage = packageChooser.selectedPackage ?: return
        val directory: PsiDirectory = selectedPackage.directories.firstOrNull() ?: return

        // 3. Class adı oluşturma
        val defaultClassName = "${sourceClass.name}To${targetClass.name}Converter"

// 4. Kullanıcıya input dialog ile isim sor
        val newClassName = Messages.showInputDialog(
            project,
            "Enter converter class name:",
            "Converter Class Name",
            Messages.getQuestionIcon(),
            defaultClassName,
            null
        ) ?: return // Cancel basılırsa iptal et

        val existingClass = JavaDirectoryService.getInstance().getClasses(directory).firstOrNull {
            it.name == newClassName
        }

        if (existingClass != null) {
            NotificationUtils.showNotification(project, "Class '$newClassName' already exists in package ${selectedPackage.qualifiedName}", NotificationType.WARNING)
            return
        }

        // 4. Yeni class oluşturma ve metot ekleme
        WriteCommandAction.runWriteCommandAction(project) {
            val newClass = JavaDirectoryService.getInstance().createClass(directory, newClassName)
            val converterMethod = createConverterMethod(project, sourceClass, targetClass)
            newClass.add(converterMethod)

            val psiElementFactory = JavaPsiFacade.getElementFactory(project)
            val modifierList = newClass.modifierList
            if (modifierList != null) {
                 val annotation = psiElementFactory.createAnnotationFromText("@org.springframework.stereotype.Component", newClass)
                modifierList.addBefore(annotation, modifierList.firstChild)
            }

            val psiFile = newClass.containingFile as? PsiJavaFile ?: return@runWriteCommandAction
            psiFile.let { file ->
                val psiFacade = JavaPsiFacade.getInstance(project)
                val componentClass = psiFacade.findClass("org.springframework.stereotype.Component", GlobalSearchScope.moduleScope(module))
                if (componentClass != null) {
                    file.importList?.add(psiFacade.elementFactory.createImportStatement(componentClass))
                }

                val objectClass = psiFacade.findClass("java.util.Objects", GlobalSearchScope.allScope(project))

                if (objectClass != null) {
                    JavaCodeStyleManager.getInstance(project).addImport(psiFile, objectClass)
                }

                file.importList?.add(psiFacade.elementFactory.createImportStatement(targetClass))
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(file)
            }
        }

        NotificationUtils.showNotification(project, "Converter class '$newClassName' created successfully", NotificationType.INFORMATION)
    }

    private fun createConverterMethod(project: Project, sourceClass: PsiClass, targetClass: PsiClass) =
        PsiElementFactory.getInstance(project).createMethodFromText(buildString {

            val sourceClassVarName = sourceClass.name?.replaceFirstChar { it.lowercaseChar() }
            val targetClassVarName = targetClass.name?.replaceFirstChar { it.lowercaseChar() }

            append("public ${targetClass.name} convert(${sourceClass.name} ${sourceClassVarName}) {\n\n")
            append("if (Objects.isNull(${sourceClassVarName})) {return null;}\n")
            append("    ${targetClass.name} ${targetClassVarName} = new ${targetClass.name}();\n")
            targetClass.allFields.forEach { field ->
                val cap = field.name.replaceFirstChar { it.uppercase() }
                if (field.name != "serialVersionUID") {
                    append("   ${targetClassVarName}.set$cap(${sourceClassVarName}.get$cap());\n")
                }
            }
            append("    return ${targetClassVarName};\n")
            append("}\n")
        }, sourceClass)
}
