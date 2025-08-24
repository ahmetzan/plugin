package com.example.hook

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import java.io.File;

class CommitMessageCheckHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {

    private var shouldCheck = true

    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
        val checkbox = JCheckBox("Check commit message format", true)
        val panelUI = JPanel(BorderLayout()).apply {
            add(checkbox, BorderLayout.CENTER)
        }

        return object : RefreshableOnComponent {
            override fun getComponent() = panelUI
            override fun refresh() {}
            override fun saveState() {
                shouldCheck = checkbox.isSelected
            }
            override fun restoreState() {
                checkbox.isSelected = shouldCheck
            }
        }
    }

    override fun beforeCheckin(): ReturnResult {
        if (!shouldCheck) return ReturnResult.COMMIT

        val commitMessage = panel.commitMessage?.trim().orEmpty()
        val branchName = getGitBranchName(panel.project) ?: return ReturnResult.COMMIT

        val prefix = extractPrefixFromBranch(branchName) ?: return ReturnResult.COMMIT

        if (!commitMessage.startsWith("$prefix |")) {
            val result = Messages.showYesNoDialog(
                panel.project,
                "Commit mesajı branch adına uygun değil.\n\n" +
                        "Beklenen: \"$prefix | ...\"\n\nDevam etmek istiyor musun?",
                "Commit Mesajı Uyarısı",
                "Devam Et",
                "İptal Et",
                Messages.getWarningIcon()
            )
            return if (result == Messages.YES) ReturnResult.COMMIT else ReturnResult.CANCEL
        }

        return ReturnResult.COMMIT
    }

    private fun extractPrefixFromBranch(branchName: String): String? {
        val regex = Regex("""(?:story|bugfix)/([A-Z]+-\d+)|rc/([\d.]+)""")
        val match = regex.find(branchName) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
    }

    private fun getGitBranchName(project: Project): String? {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(project.basePath?.let(::File))
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}