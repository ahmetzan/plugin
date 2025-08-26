package com.migros.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class CommitMessageCheckHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {

    private var shouldCheck = true

    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
        val checkbox = JBCheckBox("Check commit message format", shouldCheck)

        return object : RefreshableOnComponent {
            override fun getComponent(): JComponent {
                val jPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
                    add(checkbox, BorderLayout.CENTER)
                }
                return jPanel
            }

            override fun restoreState() {
                checkbox.isSelected = shouldCheck
            }

            override fun saveState() {
                shouldCheck = checkbox.isSelected
            }

        }
    }

    override fun beforeCheckin(): ReturnResult {
        if (!shouldCheck) return ReturnResult.COMMIT

        val commitMessage = panel.commitMessage.trim()
        val branchName = getGitBranchName(panel.project) ?: return ReturnResult.COMMIT

        val prefix = extractPrefixFromBranch(branchName) ?: return ReturnResult.COMMIT

        if (!commitMessage.startsWith("$prefix |")) {
            val result = Messages.showYesNoDialog(
                panel.project,
                """
                    Format isn't appropriate
                    Expected format: $prefix | ...
                    Would you like to continue?
                """.trimIndent(),
                "Commit Message Warning",
                "Continue",
                "Cancel",
                Messages.getWarningIcon()
            )
            return if (result == Messages.YES) ReturnResult.COMMIT else ReturnResult.CANCEL
        }

        return ReturnResult.COMMIT
    }

    private fun extractPrefixFromBranch(branchName: String): String? {
        return branchName.substringAfterLast('/')
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