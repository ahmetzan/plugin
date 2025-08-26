package com.migros.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope

class ReplaceWithObjectsFix(
    private val replacement: String,
    private val importFqName: String? = null
) : LocalQuickFix {

    override fun getFamilyName(): String = "Replace with utility method"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PsiBinaryExpression ?: return
        val factory = JavaPsiFacade.getElementFactory(project)

        val newExpr = factory.createExpressionFromText(replacement, element.context)
        val file = element.containingFile as? PsiJavaFile

        if (importFqName != null && file != null) {
            val facade = JavaPsiFacade.getInstance(project)
            val targetClass = facade.findClass(importFqName, GlobalSearchScope.allScope(project))

            if (targetClass != null) {
                val alreadyImported = file.importList?.allImportStatements?.any { stmt ->
                    stmt.importReference?.qualifiedName == importFqName
                } ?: false

                if (!alreadyImported) {
                    file.importList?.add(factory.createImportStatement(targetClass))
                }
            }
        }

        element.replace(newExpr)
    }
}