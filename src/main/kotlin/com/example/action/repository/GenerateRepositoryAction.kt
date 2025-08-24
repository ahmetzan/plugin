package com.example.action.repository

import com.intellij.ide.util.PackageChooserDialog
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

class GenerateRepositoryAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = element is PsiClass
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val entityClass = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiClass ?: return

        // Paket seçimi
        val packageChooser = PackageChooserDialog("Select Package for Repository", project)
        packageChooser.show()
        val selectedPackage = packageChooser.selectedPackage ?: return
        val directory = selectedPackage.directories.firstOrNull() ?: return

        // Repository ismi
        val defaultRepoName = "${entityClass.name}Repository"

        val repoName = Messages.showInputDialog(
            project,
            "Enter repository interface name:",
            "Repository Name",
            Messages.getQuestionIcon(),
            defaultRepoName,
            null
        ) ?: return

        // Var mı kontrol et
        val existing = JavaDirectoryService.getInstance().getClasses(directory).firstOrNull { it.name == repoName }
        if (existing != null) {
            Messages.showErrorDialog(project, "Repository class '$repoName' already exists!", "Error")
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

            // Entity class import
//            JavaCodeStyleManager.getInstance(project).addImport(newInterface.containingFile as PsiJavaFile, entityClass)

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

            // Burada id tipini almak istersen, örneğin ilk field tipi (basitçe):
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

        Messages.showInfoMessage(project, "Repository '$repoName' created successfully!", "Success")
    }
}
