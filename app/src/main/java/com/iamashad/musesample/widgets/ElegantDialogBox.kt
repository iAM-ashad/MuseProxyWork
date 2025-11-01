package com.iamashad.musesample.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Reusable, opinionated confirmation dialog with:
 * - Optional header image (wide banner).
 * - Prominent icon (defaults to a check mark).
 * - Title, message, and configurable primary/secondary actions.
 *
 * When to use:
 * - Destructive actions (delete, reset).
 * - Sharing/export confirmation.
 * - Any action that needs a little ceremony and clear choices.
 *
 * Styling notes:
 * - Uses a rounded container with tonal elevation and theme-aware colors.
 * - Primary action uses a FilledTonalButton; secondary is a TextButton (right-aligned).
 * - Buttons are UPPERCASE for emphasis/affordance.
 */
@Composable
fun ElegantAlertDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    icon: ImageVector = Icons.Rounded.CheckCircle,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(24.dp),
    image: Painter? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Optional banner image (e.g., illustration or photo).
                if (image != null) {
                    Image(
                        painter = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Leading icon to reinforce intent (success/warning/error).
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(8.dp))

                // Body message (centered, subdued color)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(24.dp))

                // Actions: secondary (dismiss) then primary (confirm), right-aligned.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText.uppercase())
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onConfirm) {
                        Text(confirmText.uppercase())
                    }
                }
            }
        }
    }
}
