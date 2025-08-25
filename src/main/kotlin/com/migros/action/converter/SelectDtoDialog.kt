package com.migros.action.converter

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class SelectDtoDialog : DialogWrapper(true) {

    private val dtoClassNameField = JBTextField()

    init {
        title = "Select DTO Class"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(5, 5))
        panel.minimumSize = Dimension(400, 80)

        val label = JBLabel("Enter fully-qualified DTO class name:")
        panel.add(label, BorderLayout.NORTH)
        panel.add(dtoClassNameField, BorderLayout.CENTER)

        return panel
    }

    fun getDtoClassName(): String {
        return dtoClassNameField.text.trim()
    }
}
