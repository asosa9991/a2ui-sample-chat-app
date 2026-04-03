package com.example.a2ui.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.a2ui.chat.data.a2ui.toComponentsJsonString
import com.example.a2ui.chat.data.a2ui.toPrettyString
import com.example.a2ui.chat.presentation.ChatViewModel
import com.example.a2ui.chat.presentation.screen.ChatScreen
import com.example.a2ui.chat.presentation.screen.EditorScreen
import com.example.a2ui.chat.theme.A2UIChatTheme
import kotlinx.serialization.json.JsonObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A2UIChatTheme {
                val navController = rememberNavController()
                val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)

                NavHost(navController = navController, startDestination = "chat") {
                    composable("chat") {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onEditMessage = { messageId ->
                                navController.navigate("editor/$messageId")
                            }
                        )
                    }
                    composable("editor/{messageId}") { backStackEntry ->
                        val messageId = backStackEntry.arguments?.getString("messageId") ?: return@composable
                        val message = chatViewModel.getMessageById(messageId)

                        val componentsJson = message?.uiDefinition?.toComponentsJsonString() ?: "[]"
                        val dataJson = message?.dataModelJson?.toPrettyString()
                            ?: JsonObject(emptyMap()).toPrettyString()

                        EditorScreen(
                            initialComponents = componentsJson,
                            initialData = dataJson,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
