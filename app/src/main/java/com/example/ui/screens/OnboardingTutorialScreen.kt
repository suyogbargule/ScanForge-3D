package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingTutorialScreen(
    onDismiss: () -> Unit,
    onStartCapture: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        "Stand 3-4 feet away from nearby objects or surfaces.",
        "Keep phone close to your chest",
        "Plant your feet and rotate in one spot.",
        "To capture floor, step back and extend your arm to align the camera directly over the target."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03160F)) // Deep dark green / black background as in screenshots
            .padding(24.dp)
            .testTag("onboarding_tutorial_screen")
    ) {
        // Close 'X' in top-right
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp)
                .testTag("btn_close_tutorial")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Tutorial",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Illustration Area
            Box(
                modifier = Modifier
                    .size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                TutorialIllustration(step = currentStep)
            }

            // Instructional Text
            Text(
                text = steps[currentStep],
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentStep == 0) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_skip_tutorial"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
                    ) {
                        Text("Skip", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_back_tutorial"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
                    ) {
                        Text("Back", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < 3) {
                            currentStep++
                        } else {
                            onStartCapture()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_next_tutorial"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (currentStep == 3) "Start" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialIllustration(step: Int) {
    val greenColor = Color(0xFF22C55E) // Vibrant green matching reference screenshots

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val stroke = Stroke(width = 3.5f)
        val dashedStroke = Stroke(
            width = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        )

        when (step) {
            0 -> {
                // Step 1: Room with person in center circle
                // 3D Room lines
                drawLine(greenColor, Offset(30f, 60f), Offset(70f, 80f), strokeWidth = 3f)
                drawLine(greenColor, Offset(w - 30f, 60f), Offset(w - 70f, 80f), strokeWidth = 3f)
                drawLine(greenColor, Offset(70f, 80f), Offset(w - 70f, 80f), strokeWidth = 3f)
                drawLine(greenColor, Offset(70f, 80f), Offset(70f, 190f), strokeWidth = 3f)
                drawLine(greenColor, Offset(w - 70f, 80f), Offset(w - 70f, 190f), strokeWidth = 3f)

                // TV on wall
                drawRect(greenColor, topLeft = Offset(40f, 90f), size = Size(35f, 50f), style = stroke)
                // Mirror
                drawCircle(greenColor, radius = 20f, center = Offset(cx, 95f), style = stroke)

                // Dashed floor circle
                drawOval(
                    color = greenColor,
                    topLeft = Offset(cx - 65f, cy + 30f),
                    size = Size(130f, 50f),
                    style = dashedStroke
                )

                // Person stick figure standing
                drawCircle(greenColor, radius = 9f, center = Offset(cx, cy - 25f), style = stroke)
                drawLine(greenColor, Offset(cx, cy - 16f), Offset(cx, cy + 35f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx, cy + 35f), Offset(cx - 8f, cy + 55f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx, cy + 35f), Offset(cx + 8f, cy + 55f), strokeWidth = 3.5f)
                // Holding phone
                drawLine(greenColor, Offset(cx, cy - 5f), Offset(cx - 10f, cy + 10f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx, cy - 5f), Offset(cx + 10f, cy + 10f), strokeWidth = 3.5f)
            }

            1 -> {
                // Step 2: Person holding phone close to chest
                // Hooded torso outline
                val bodyPath = Path().apply {
                    moveTo(cx - 30f, cy - 65f)
                    lineTo(cx + 20f, cy - 40f)
                    lineTo(cx + 5f, cy - 5f)
                    lineTo(cx + 20f, cy + 15f)
                    lineTo(cx + 45f, cy - 10f)
                    lineTo(cx + 50f, cy + 35f)
                    lineTo(cx + 10f, cy + 65f)
                    lineTo(cx - 40f, cy + 65f)
                    lineTo(cx - 55f, cy - 15f)
                    close()
                }
                drawPath(bodyPath, greenColor, style = stroke)

                // Phone
                drawRoundRect(
                    color = greenColor,
                    topLeft = Offset(cx + 42f, cy - 25f),
                    size = Size(14f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    style = stroke
                )

                // Double arrow between phone and chest
                drawLine(greenColor, Offset(cx + 14f, cy - 10f), Offset(cx + 38f, cy - 10f), strokeWidth = 3f)
                // Arrow heads
                drawLine(greenColor, Offset(cx + 20f, cy - 15f), Offset(cx + 14f, cy - 10f), strokeWidth = 3f)
                drawLine(greenColor, Offset(cx + 20f, cy - 5f), Offset(cx + 14f, cy - 10f), strokeWidth = 3f)
                drawLine(greenColor, Offset(cx + 32f, cy - 15f), Offset(cx + 38f, cy - 10f), strokeWidth = 3f)
                drawLine(greenColor, Offset(cx + 32f, cy - 5f), Offset(cx + 38f, cy - 10f), strokeWidth = 3f)
            }

            2 -> {
                // Step 3: Person rotating in one spot with panoramic frames
                // 360 viewpoint frames around
                val frames = listOf(-80f, -40f, 0f, 40f, 80f)
                frames.forEach { offsetAngle ->
                    val frameX = cx + offsetAngle * 1.1f
                    val frameY = cy - 40f + (abs(offsetAngle) * 0.15f)
                    drawRoundRect(
                        color = greenColor,
                        topLeft = Offset(frameX - 14f, frameY - 26f),
                        size = Size(28f, 48f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        style = stroke
                    )
                    drawCircle(greenColor, radius = 4f, center = Offset(frameX, frameY), style = stroke)
                }

                // Rotating curved arrow around person
                val arrowRect = Rect(cx - 75f, cy + 15f, cx + 75f, cy + 55f)
                drawArc(greenColor, startAngle = 10f, sweepAngle = 200f, useCenter = false, topLeft = arrowRect.topLeft, size = arrowRect.size, style = stroke)

                // Person in center
                drawCircle(greenColor, radius = 12f, center = Offset(cx, cy - 30f), style = stroke)
                drawLine(greenColor, Offset(cx, cy - 18f), Offset(cx, cy + 30f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx, cy + 30f), Offset(cx - 15f, cy + 65f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx, cy + 30f), Offset(cx + 15f, cy + 65f), strokeWidth = 3.5f)
            }

            3 -> {
                // Step 4: Step back and extend arm to capture floor target
                // Floor circle target
                drawOval(
                    color = greenColor.copy(alpha = 0.35f),
                    topLeft = Offset(cx - 20f, cy + 60f),
                    size = Size(90f, 32f)
                )
                drawOval(
                    color = greenColor,
                    topLeft = Offset(cx - 20f, cy + 60f),
                    size = Size(90f, 32f),
                    style = stroke
                )

                // Person extending arm downward
                drawCircle(greenColor, radius = 12f, center = Offset(cx - 65f, cy - 45f), style = stroke)
                drawLine(greenColor, Offset(cx - 65f, cy - 33f), Offset(cx - 60f, cy + 35f), strokeWidth = 3.5f)
                // Extended arm holding phone directly over circle
                drawLine(greenColor, Offset(cx - 62f, cy - 15f), Offset(cx + 25f, cy - 15f), strokeWidth = 3.5f)
                // Phone
                drawRoundRect(
                    color = greenColor,
                    topLeft = Offset(cx + 20f, cy - 22f),
                    size = Size(20f, 10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                    style = stroke
                )

                // Dotted laser line from phone to floor circle center
                drawLine(
                    color = greenColor,
                    start = Offset(cx + 25f, cy - 10f),
                    end = Offset(cx + 25f, cy + 68f),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                // Feet stepped back
                drawLine(greenColor, Offset(cx - 60f, cy + 35f), Offset(cx - 80f, cy + 75f), strokeWidth = 3.5f)
                drawLine(greenColor, Offset(cx - 60f, cy + 35f), Offset(cx - 50f, cy + 75f), strokeWidth = 3.5f)
            }
        }
    }
}
