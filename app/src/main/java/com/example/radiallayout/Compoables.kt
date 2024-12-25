package com.example.radiallayout

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


/**
 * Data class representing the animation state for each item in the radial layout.
 * @property position Animatable for x,y coordinates using Vector2D for smooth position transitions
 * @property scale Animatable for size changes using Vector1D for scaling animations
 */
private data class RadialItemAnimationState(
    val position: Animatable<Offset, AnimationVector2D>,
    val scale: Animatable<Float, AnimationVector1D>
)

/**
 * Main composable function for the RadialLayout.
 * Creates a circular arrangement of items with interactive animations.
 *
 * @param items List of composable items to be arranged in a circle
 * @param modifier Optional modifier for the layout
 * @param onItemSelected Callback when a peripheral item is selected
 * @param onCenterItemClicked Callback when the center item is clicked
 */
@Composable
fun RadialLayout(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    onItemSelected: (Int) -> Unit = {},
    onCenterItemClicked: (Int) -> Unit = {}
) {
    // Tracks which item is currently in the center
    var centerItemIndex by rememberSaveable { mutableIntStateOf(0) }

    // Coroutine scope for handling animations
    val scope = rememberCoroutineScope()

    // Initialize animation states for all items
    val animationStates = rememberRadialAnimationStates(items)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        RadialLayoutContent(
            items = items,
            animationStates = animationStates,
            onItemClick = { clickedIndex ->
                handleItemClick(
                    clickedIndex = clickedIndex,
                    centerItemIndex = centerItemIndex,
                    animationStates = animationStates,
                    scope = scope,
                    onCenterItemClicked = onCenterItemClicked,
                    onItemSelected = onItemSelected,
                    onAnimationComplete = { centerItemIndex = clickedIndex }
                )
            }
        )
    }
}

/**
 * Creates and remembers animation states for all items in the layout.
 * Each item has its position and scale animations managed here.
 *
 * @param items List of composable items
 * @return List of RadialItemAnimationState for each item
 */
@Composable
private fun rememberRadialAnimationStates(
    items: List<@Composable () -> Unit>
): List<RadialItemAnimationState> = rememberSaveable(items) {
    List(items.size) { index ->
        RadialItemAnimationState(
            // Position animatable with initial calculated position
            position = Animatable(
                initialValue = calculateItemPosition(index, items.size),
                typeConverter = Offset.VectorConverter
            ),
            // Scale animatable: 1.2f for center item, 0.8f for others
            scale = Animatable(if (index == 0) 1.2f else 0.8f)
        )
    }
}

/**
 * Custom layout implementation for arranging items in a radial pattern.
 * Handles measurement and placement of items according to their animation states.
 */
@Composable
private fun RadialLayoutContent(
    items: List<@Composable () -> Unit>,
    animationStates: List<RadialItemAnimationState>,
    onItemClick: (Int) -> Unit
) {
    Layout(
        contents = items.mapIndexed { index, item ->
            {
                RadialItem(
                    content = item,
                    onClick = { onItemClick(index) }
                )
            }
        }
    ) { measurables, constraints ->
        // Measure all items
        val placeables = measurables.flatten().map { it.measure(constraints) }

        // Calculate layout size as square
        val dimension = minOf(constraints.maxWidth, constraints.maxHeight)

        layout(dimension, dimension) {
            placeables.forEachIndexed { index, placeable ->
                placeRadialItem(
                    placeable = placeable,
                    animationState = animationStates[index],
                    dimension = dimension
                )
            }
        }
    }
}

/**
 * Handles click events on items in the radial layout.
 * Manages animation triggering and callback execution.
 */
private fun handleItemClick(
    clickedIndex: Int,
    centerItemIndex: Int,
    animationStates: List<RadialItemAnimationState>,
    scope: CoroutineScope,
    onCenterItemClicked: (Int) -> Unit,
    onItemSelected: (Int) -> Unit,
    onAnimationComplete: () -> Unit
) {
    // Check if any position animation is currently running
    // This prevents animation interruption
    if (animationStates.any { it.position.isRunning }) return

    // If the clicked item is already in the center, trigger center click callback
    if (clickedIndex == centerItemIndex) {
        onCenterItemClicked(clickedIndex)
        return
    }

    // Notify about peripheral item selection
    onItemSelected(clickedIndex)

    // Launch parallel animations for position and scale changes
    scope.launch {
        animateItemSwap(
            clickedIndex = clickedIndex,
            centerItemIndex = centerItemIndex,
            animationStates = animationStates,
            onComplete = onAnimationComplete
        )
    }
}

/**
 * Performs the animation of swapping items between center and clicked positions.
 * Uses coroutines for parallel animations of position and scale.
 */
private suspend fun animateItemSwap(
    clickedIndex: Int,
    centerItemIndex: Int,
    animationStates: List<RadialItemAnimationState>,
    onComplete: () -> Unit
) {
    coroutineScope {
        val clickedState = animationStates[clickedIndex]
        val centerState = animationStates[centerItemIndex]

        // Run four animations in parallel:
        // 1. Clicked item position animation
        // 2. Center item position animation
        // 3. Clicked item scale animation
        // 4. Center item scale animation
        val animations = listOf(
            // Move clicked item to center
            async { clickedState.position.animateTo(centerState.position.value, animationSpec = createTweenSpec()) },

            // Move center item to clicked position
            async { centerState.position.animateTo(clickedState.position.value, animationSpec = createTweenSpec()) },

            // Scale up clicked item
            async { clickedState.scale.animateTo(1.2f, animationSpec = createTweenSpec()) },

            // Scale down center item
            async { centerState.scale.animateTo(0.8f, animationSpec = createTweenSpec()) }
        )

        // Wait for all animations to complete
        animations.awaitAll()

        // Trigger completion callback
        onComplete()
    }
}

/**
 * Calculates the position for an item based on its index and total items.
 * Uses polar coordinates converted to Cartesian coordinates.
 *
 * @param index Item index (0 is center)
 * @param totalItems Total number of items
 * @param radius Radius of the circle (as fraction of layout size)
 * @return Normalized Offset position (0.0 to 1.0)
 */
private fun calculateItemPosition(
    index: Int,
    totalItems: Int,
    radius: Float = 0.3f
): Offset = when (index) {
    0 -> Offset(0.5f, 0.5f) // Center position
    else -> {
        val adjustedIndex = index - 1
        // Calculate angle using polar coordinates
        val angle = (2 * PI * adjustedIndex) / (totalItems - 1)
        // Convert to Cartesian coordinates
        Offset(
            x = 0.5f + (radius * cos(angle)).toFloat(),
            y = 0.5f + (radius * sin(angle)).toFloat()
        )
    }
}

/**
 * Places an item in the layout with proper positioning and scaling.
 * Handles coordinate transformation from normalized space to actual pixels.
 */
private fun Placeable.PlacementScope.placeRadialItem(
    placeable: Placeable,
    animationState: RadialItemAnimationState,
    dimension: Int
) {
    // Convert normalized coordinates to actual pixels
    val centerX = (dimension * animationState.position.value.x).roundToInt()
    val centerY = (dimension * animationState.position.value.y).roundToInt()

    // Place item with proper transformation
    placeable.placeWithLayer(
        x = centerX - (placeable.width / 2),
        y = centerY - (placeable.height / 2)
    ) {
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        scaleX = animationState.scale.value
        scaleY = animationState.scale.value
    }
}

/**
 * RadialItem is a composable that wraps individual items in the radial layout.
 * It provides click functionality and handles interaction states for each item.
 *
 * Key features:
 * 1. Custom click handling without ripple effect
 * 2. Wrapper for content with proper interaction handling
 * 3. Stateless implementation for better recomposition
 *
 * @param content The composable content to be displayed in the radial item
 * @param onClick Callback function triggered when the item is clicked
 */
@Composable
private fun RadialItem(
    content: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        // Modifier configuration for click handling
        modifier = Modifier.clickable(
            // Custom interaction source to prevent multiple simultaneous clicks
            interactionSource = remember { MutableInteractionSource() },
            // Disable ripple effect for clean visual appearance
            indication = null,
            // Click handler passed from parent
            onClick = onClick
        )
    ) {
        // Render the provided content within the clickable container
        content()
    }
}

/**
 * Creates animation specification with overshoot effect.
 * Uses OvershootInterpolator for bouncy animation feel.
 */
private fun <T> createTweenSpec(): TweenSpec<T> = tween(
    durationMillis = 1000,
    easing = { OvershootInterpolator().getInterpolation(it) }
)

