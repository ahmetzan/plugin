package com.migros.action.repository

import com.intellij.ide.util.PackageChooserDialog
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.migros.utils.NotificationUtils

class GenerateRepositoryAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element is PsiClass) {
            val hasEntity = element.hasAnnotation("javax.persistence.Entity")
                    || element.hasAnnotation("jakarta.persistence.Entity")

            e.presentation.isEnabledAndVisible = hasEntity
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val entityClass = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiClass ?: return

        val packageChooser = PackageChooserDialog("Select Package for Repository", project)
        packageChooser.show()
        val selectedPackage = packageChooser.selectedPackage ?: return
        val directory = selectedPackage.directories.firstOrNull() ?: return

        val defaultRepoName = "${entityClass.name}Repository"

        val repoName = Messages.showInputDialog(
            project,
            "Enter repository interface name:",
            "Repository Name",
            Messages.getQuestionIcon(),
            defaultRepoName,
            null
        ) ?: return

        val existing = JavaDirectoryService.getInstance().getClasses(directory).firstOrNull { it.name == repoName }
        if (existing != null) {
            NotificationUtils.showNotification(project, "Repository class '$repoName' already exists", NotificationType.WARNING)
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val newInterface = JavaDirectoryService.getInstance().createInterface(directory, repoName)

            val psiElementFactory = JavaPsiFacade.getElementFactory(project)
            val modifierList = newInterface.modifierList
            if (modifierList != null) {
                val annotation = psiElementFactory.createAnnotationFromText("@org.springframework.stereotype.Repository", newInterface)
                modifierList.addBefore(annotation, modifierList.firstChild)
            }

            val psiFile = newInterface.containingFile as? PsiJavaFile ?: return@runWriteCommandAction
            psiFile.let { file ->
                val psiFacade = JavaPsiFacade.getInstance(project)
                val componentClass = psiFacade.findClass("org.springframework.stereotype.Component", GlobalSearchScope.moduleScope(module))
                if (componentClass != null) {
                    file.importList?.add(psiFacade.elementFactory.createImportStatement(componentClass))
                }

                val jpaRepositoryImport = psiFacade.findClass("org.springframework.data.jpa.repository.JpaRepository", GlobalSearchScope.allScope(project))
                if (jpaRepositoryImport != null) {
                    JavaCodeStyleManager.getInstance(project).addImport(psiFile, jpaRepositoryImport)
                }

                file.importList?.add(psiFacade.elementFactory.createImportStatement(entityClass))

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(file)
            }

            val idField = entityClass.allFields.firstOrNull { field ->
                field.annotations.any { annotation ->
                    annotation.qualifiedName == "javax.persistence.Id" || annotation.qualifiedName == "jakarta.persistence.Id"
                }
            }

            val idType = idField?.type?.presentableText ?: "Long"

            val factory = JavaPsiFacade.getElementFactory(project)

            val entityQualifiedName = entityClass.qualifiedName ?: return@runWriteCommandAction
            val extendsRef = factory.createReferenceFromText("JpaRepository<$entityQualifiedName, $idType>", newInterface)
            newInterface.extendsList?.add(extendsRef)
        }

        NotificationUtils.showNotification(project, "Repository '$repoName' created successfully", NotificationType.INFORMATION)
    }
}
