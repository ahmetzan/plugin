package com.migros.widget.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component
import java.time.LocalTime
import java.util.Timer
import java.util.TimerTask

class TimeWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "TimeWidget"
    override fun getDisplayName() = "Clock"
    override fun isAvailable(project: Project) = true

    override fun createWidget(project: Project): StatusBarWidget {
        return object : StatusBarWidget {
            private var statusBar: StatusBar? = null
            private val timer = Timer()

            override fun ID() = "myplugin.time"

            override fun install(statusBar: StatusBar) {
                this.statusBar = statusBar
                timer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        statusBar.updateWidget(ID())
                    }
                }, 0, 1000)
            }

            override fun dispose() { timer.cancel() }

            override fun getPresentation(): StatusBarWidget.WidgetPresentation {
                return object : StatusBarWidget.TextPresentation {
                    override fun getText(): String = LocalTime.now().withNano(0).toString()
                    override fun getTooltipText(): String? = "Time"
                    override fun getAlignment(): Float = Component.LEFT_ALIGNMENT
                }
            }
        }
    }

    override fun disposeWidget(widget: StatusBarWidget) { widget.dispose() }
}