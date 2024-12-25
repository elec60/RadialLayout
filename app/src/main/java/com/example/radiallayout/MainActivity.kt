@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.radiallayout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.radiallayout.ui.theme.RadialLayoutTheme
import kotlinx.serialization.Serializable

/**
 * Route definitions for navigation using Serializable data objects
 */
@Serializable
data object MainRoute // Main screen route

@Serializable
data object SecondRoute {
    // Route pattern with parameter for content
    const val ROUTE = "second_route/{content}"

    // Helper function to create route with specific content
    fun createRoute(content: String) = "second_route/$content"
}

// Key for shared element transition state
private const val SHARED_CONTENT_KEY = "shared_content"

/**
 * Main Activity implementing the RadialLayout with shared element transitions
 * Uses experimental shared transition API for smooth animations between screens
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadialLayoutTheme {
                // Wrapper for shared element transitions
                SharedTransitionLayout {
                    // Navigation controller for handling screen transitions
                    val navController = rememberNavController()

                    // Navigation host setup
                    NavHost(
                        navController = navController,
                        startDestination = MainRoute
                    ) {
                        // Main screen with radial layout
                        composable<MainRoute> {
                            MainScreen(this, navController)
                        }

                        // Detail screen with shared element transition
                        composable(
                            route = SecondRoute.ROUTE,
                            arguments = listOf(
                                navArgument("content") {
                                    type = NavType.StringType
                                }
                            )
                        ) { entry ->
                            val content = entry.arguments?.getString("content") ?: ""

                            // Container with shared transition bounds
                            Box(
                                modifier = Modifier
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            key = SHARED_CONTENT_KEY
                                        ),
                                        animatedVisibilityScope = this,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                                    )
                                    .fillMaxSize()
                                    .background(Color.Red),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = content,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main screen composable containing the RadialLayout
 * Implements shared transition animations for selected items
 *
 * @param animatedContentScope Scope for animation content
 * @param navController Controller for navigation between screens
 */
@Composable
private fun SharedTransitionScope.MainScreen(
    animatedContentScope: AnimatedContentScope,
    navController: NavHostController
) {
    // Track the currently centered item
    var centerItemIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        RadialLayout(
            items = List(7) { index ->
                {
                    CircleItem(
                        number = index + 1,
                        animatedContentScope,
                        isCenter = index == centerItemIndex
                    )
                }
            },
            onItemSelected = { index ->
                centerItemIndex = index
            },
            onCenterItemClicked = { index ->
                // Navigate to detail screen with shared element transition
                navController.navigate(
                    SecondRoute.createRoute((index + 1).toString())
                )
            }
        )
    }
}

/**
 * Individual circle item composable with shared transition support
 * Handles color animations and shared element transitions
 *
 * @param number The number to display in the circle
 * @param animatedContentScope Scope for animation content
 * @param isCenter Whether this item is currently centered
 */
@Composable
fun SharedTransitionScope.CircleItem(
    number: Int,
    animatedContentScope: AnimatedContentScope,
    isCenter: Boolean
) {
    // State for shared element transition
    val contentState = rememberSharedContentState(key = SHARED_CONTENT_KEY)

    // Animated color state
    val colorAnimatable = remember {
        Animatable(if (isCenter) Color.Red else Color.Blue)
    }

    // Trigger color animation when center state changes
    LaunchedEffect(isCenter) {
        colorAnimatable.animateTo(
            if (isCenter) Color.Red else Color.Blue,
            animationSpec = tween(durationMillis = 500)
        )
    }

    Text(
        "$number",
        modifier = Modifier
            .composeIf(isCenter) {
                // Apply shared bounds only to centered item
                sharedBounds(
                    sharedContentState = contentState,
                    animatedVisibilityScope = animatedContentScope,
                )
            }
            .size(size = 60.dp)
            .background(color = colorAnimatable.value, shape = CircleShape)
            .wrapContentSize(),
        textAlign = TextAlign.Center,
        color = Color.White
    )
}

/**
 * Utility extension function for conditional modifier application
 * Helps with clean conditional shared transition setup
 */
fun Modifier.composeIf(
    condition: Boolean,
    builder: Modifier.() -> Modifier
): Modifier {
    return if (condition) builder() else this
}