package com.migros.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpression

class EqualityInspection : LocalInspectionTool() {

    companion object {
        const val OBJECTS_IMPORT = "java.util.Objects"
        const val COLLECTIONS_IMPORT = "com.migros.next.utils.CollectionUtils"
    }

    override fun getDisplayName() = "Suspicious equality check"
    override fun getShortName() = "EqualityInspection"
    override fun getGroupDisplayName() = "Simplifier"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitBinaryExpression(expression: PsiBinaryExpression) {
                val operationSign = expression.operationSign.text
                val leftOperand = expression.lOperand
                val rightOperand = expression.rOperand ?: return

                if (operationSign == "==" || operationSign == "!=") {
                    val type = leftOperand.type ?: return
                    val typeName = type.canonicalText

                    replaceWrappers(typeName, expression, operationSign, leftOperand, rightOperand, holder)
                    replaceCollections(typeName, expression, operationSign, leftOperand, rightOperand, holder)

                }
            }
        }
    }

    private fun replaceCollections(
        typeName: String, expression: PsiBinaryExpression,
        operationSign: String, leftOperand: PsiExpression,
        rightOperand: PsiExpression, holder: ProblemsHolder
    ) {
        if (isCollection(typeName)) {

            when {
                rightOperand.text == "null" && operationSign == "==" -> {
                    val replacement = "CollectionUtils.isEmpty(${leftOperand.text})"
                    holder.registerProblem(
                        expression,
                        "Use CollectionUtils.isEmpty(${leftOperand.text})",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        ReplaceWithObjectsFix(replacement, COLLECTIONS_IMPORT)
                    )
                }

                rightOperand.text == "null" && operationSign == "!=" -> {
                    val replacement = "CollectionUtils.isNotEmpty(${leftOperand.text})"
                    holder.registerProblem(
                        expression,
                        "Use CollectionUtils.isNotEmpty(${leftOperand.text})",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        ReplaceWithObjectsFix(replacement, COLLECTIONS_IMPORT)
                    )
                }
            }
        }
    }

    private fun replaceWrappers(
        typeName: String, expression: PsiBinaryExpression,
        operationSign: String, leftOperand: PsiExpression,
        rightOperand: PsiExpression, holder: ProblemsHolder
    ) {

        if (isWrapper(typeName)) {
            when {
                rightOperand.text == "null" && operationSign == "==" -> {
                    val replacement = "Objects.isNull(${leftOperand.text})"
                    holder.registerProblem(
                        expression,
                        "Use Objects.isNull(${leftOperand.text})",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        ReplaceWithObjectsFix(replacement, OBJECTS_IMPORT)
                    )
                }

                rightOperand.text == "null" && operationSign == "!=" -> {
                    val replacement = "Objects.nonNull(${leftOperand.text})"
                    holder.registerProblem(
                        expression,
                        "Use Objects.nonNull(${leftOperand.text})",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        ReplaceWithObjectsFix(replacement, OBJECTS_IMPORT)
                    )
                }

                else -> {
                    if (operationSign == "!=") {
                        val replacement = "!${leftOperand.text}.equals(${rightOperand.text})"
                        holder.registerProblem(
                            expression,
                            "Use !${leftOperand.text}.equals(${rightOperand.text})",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            ReplaceWithObjectsFix(replacement, OBJECTS_IMPORT)
                        )
                    } else {
                        val replacement = "${leftOperand.text}.equals(${rightOperand.text})"
                        holder.registerProblem(
                            expression,
                            "Use ${leftOperand.text}.equal(${rightOperand.text})",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            ReplaceWithObjectsFix(replacement, OBJECTS_IMPORT)
                        )
                    }

                }
            }
        }

    }

    private fun isCollection(typeName: String): Boolean {
        return typeName.startsWith("java.util.List") ||
                typeName.startsWith("java.util.Set") ||
                typeName.startsWith("java.util.Map")
    }

    private fun isWrapper(typeName: String): Boolean {
        return typeName in setOf(
            "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Boolean", "java.lang.Byte",
            "java.lang.Short", "java.lang.Character", "java.lang.String"
        )
    }
}
