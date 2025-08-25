package com.migros.action.error

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

class AddErrorMessageToYamlAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        // update()'i background thread'te çalıştır
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        if (project == null || editor == null || file == null) {
            presentation.isEnabledAndVisible = false
            return
        }
        // Ağır PSI erişimlerini burada güvenle yapabilirsin
        presentation.isEnabledAndVisible = isOnEligibleLiteral(file, editor)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val literal = runReadAction { literalAtCaret(file, editor) } ?: return
        val key = (literal.value as? String)?.takeIf { it.isNotBlank() } ?: return

        val yamlFile = findTargetYaml(project) ?: return
        val psiYaml = PsiManager.getInstance(project).findFile(yamlFile) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiYaml) ?: return

        val newClassName = Messages.showInputDialog(
            project,
            "Enter error message:",
            "Error Message",
            Messages.getQuestionIcon(),
            "",
            null
        ) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
            val original = document.text

            // Already exists?
            val keyRegex = Regex("(^|\\n)\\s{4}${Regex.escape(key)}:\\n", RegexOption.MULTILINE)
            if (keyRegex.containsMatchIn(original)){
                showNotification(project, "Error Message Already Exists!", NotificationType.WARNING)
                return@runWriteCommandAction
            }

            val builder = StringBuilder(original)
            val hasRoot = original.contains("\nerror:") || original.trimStart().startsWith("error:")
            val hasMessages = original.contains("\n  messages:") || original.trimStart().startsWith("error:\n  messages:")
            if (!hasRoot) builder.append("error:\n")
            if (!hasMessages) builder.append("  messages:\n")

            // Compute service-specific prefix (PRODUCT, ASSET, PRICE, ...)
            val prefix = runReadAction { inferServicePrefix(literal, builder.toString()) }

            val codeRegex = Regex("code:\\s*\"${Regex.escape(prefix)}-([0-9]{4})\"")
            val maxNum = codeRegex.findAll(builder.toString()).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
            val nextNum = (maxNum + 1).coerceAtLeast(1)
            val codeStr = "$prefix-" + nextNum.toString().padStart(4, '0')

            val block = buildString {
                append("\n    ")
                append(key)
                append(":\n")
                append("      code: \"")
                append(codeStr)
                append("\"\n")
                append("      message: ")
                append("\"")
                append(newClassName)
                append("\"\n")
            }
            if (!builder.endsWith("\n")) builder.append('\n')
            builder.append(block)

            document.setText(builder.toString())
            PsiDocumentManager.getInstance(project).commitDocument(document)
            VfsUtil.markDirtyAndRefresh(true, false, false, yamlFile)

            showNotification(project, "Error Message Created Successfully!", NotificationType.INFORMATION)
            openYmlFile(project, yamlFile)
        }

    }

    fun showNotification(project: Project, message : String, notificationType: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Custom Notifications") // plugin.xml’de tanımlanacak ID
            .createNotification(
                message,
                notificationType
            )
            .notify(project)
    }

    private fun openYmlFile(project : Project, yamlFile : VirtualFile) {

        val editors = FileEditorManager.getInstance(project).openFile(yamlFile, true)
        val psiFile = PsiManager.getInstance(project).findFile(yamlFile) ?: return
        val ymlDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        val lastLine = ymlDocument.lineCount - 1
        val lastLineOffset = ymlDocument.getLineStartOffset(lastLine)

        editors.filterIsInstance<TextEditor>().firstOrNull()?.let { te ->
            val editor = te.editor
            editor.caretModel.moveToOffset(lastLineOffset)
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
        }

    }

    private fun isOnEligibleLiteral(file: PsiFile, editor: Editor): Boolean {
        val literal = literalAtCaret(file, editor) ?: return false
        val field = PsiTreeUtil.getParentOfType(literal, PsiField::class.java) ?: return false
        val cls = field.containingClass ?: return false
        val fqn = cls.qualifiedName ?: return false
        val pkg = fqn.substringBeforeLast('.', fqn)

        val inConstantsClass = cls.name == "ErrorMessageConstants" && pkg.endsWith(".constants")
        val isString = field.type.canonicalText == "java.lang.String"
        val hasModifiers = field.hasModifierProperty(PsiModifier.PUBLIC) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.hasModifierProperty(PsiModifier.FINAL)
        return inConstantsClass && isString && hasModifiers && (literal.value as? String)?.isNotBlank() == true
    }

    private fun literalAtCaret(file: PsiFile, editor: Editor): PsiLiteralExpression? {
        val offset = editor.caretModel.offset
        var element = file.findElementAt(offset)
        if (element == null && offset > 0) element = file.findElementAt(offset - 1)
        return PsiTreeUtil.getParentOfType(element, PsiLiteralExpression::class.java, false)
    }

    private fun findTargetYaml(project: Project): VirtualFile? {
        val scope = GlobalSearchScope.projectScope(project)
        // Tek bir dosya adıyla arıyoruz
        val matches = FilenameIndex.getVirtualFilesByName("error_messages.yml", scope)
        // Öncelik: src/main/resources > resources > diğer
        return matches.minByOrNull { vf ->
            val p = vf.path
            when {
                p.contains("/src/main/resources/") -> 0
                p.contains("/resources/") -> 1
                else -> 2
            }
        }
    }

    private fun inferServicePrefix(literal: PsiLiteralExpression, yamlText: String): String {
        // 1) Prefer existing prefix from YAML if any (e.g., PRODUCT-0007)
        val existing = Regex("code:\\s*\"([A-Z]+)-([0-9]{4})\"").find(yamlText)?.groupValues?.get(1)
        if (!existing.isNullOrBlank()) return existing

        // 2) Fallback to package: com.migros.<service>...
        val field = PsiTreeUtil.getParentOfType(literal, PsiField::class.java) ?: return "GEN"
        val cls = field.containingClass ?: return "GEN"
        val parts = (cls.qualifiedName ?: "").split('.')
        val idx = parts.indexOf("migros")
        val candidate = if (idx != -1 && idx + 1 < parts.size) parts[idx + 1] else "gen"
        return candidate.uppercase()
    }
}