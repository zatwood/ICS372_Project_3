package com.abc

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

class OrderTrackingApp : Application() {

    override fun start(primaryStage: Stage) {
        val root = FXMLLoader.load<Parent>(
            javaClass.getResource("/com/abc/main.fxml")
                ?: throw IllegalStateException("FXML file not found")
        )

        primaryStage.title = APP_TITLE
        primaryStage.scene = Scene(root, WINDOW_WIDTH.toDouble(), WINDOW_HEIGHT.toDouble())
        primaryStage.minWidth = 800.0
        primaryStage.minHeight = 600.0

        // Set application icon
        try {
            javaClass.getResourceAsStream("/com/abc/foodicon.png")?.use { stream ->
                primaryStage.icons.add(Image(stream))
            }
        } catch (e: Exception) {
            println("Icon not found, using default")
        }

        primaryStage.show()
    }

    companion object {
        private const val APP_TITLE = "Order Tracking System"
        private const val WINDOW_WIDTH = 1200
        private const val WINDOW_HEIGHT = 700

        @JvmStatic
        fun main(args: Array<String>) {
            launch(OrderTrackingApp::class.java, *args)
        }
    }
}
