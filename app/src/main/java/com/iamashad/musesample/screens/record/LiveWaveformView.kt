package com.iamashad.musesample.screens.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A Composable wrapper for the legacy MyTaalSurfaceView
 *
 * This view uses its own drawing thread and SurfaceHolder for high-performance
 * waveform rendering. It also supports pinch-to-zoom and double-tap-to-reset gestures.
 *
 * @param samples The latest chunk of audio samples to feed to the view.
 * @param sampleRate The sample rate of the audio, used by the view for calculations.
 * @param modifier Modifier for layout.
 * @param isInteractionEnabled Toggles pinch-to-zoom and double-tap gestures.
 * @param clearTrigger A Boolean that, when toggled to true, clears the waveform.
 */

@Composable
fun LiveWaveformView(
    samples: FloatArray?,
    sampleRate: Int?,
    modifier: Modifier = Modifier,
    isInteractionEnabled: Boolean = true,
    clearTrigger: Boolean = false
) {
    // This LaunchedEffect will clear the view when clearTrigger becomes true.
    // We use Unit as the key so it only re-launches if the composable is disposed
    // and recreated, but we'll use an if-check inside.
    // A better way is to use the 'update' block..........................................................................................................................


    AndroidView(
        factory = { context ->
            // Create the view instance
            MyTaalSurfaceView(context)
        },
        modifier = modifier,
        update = { view ->
            // This block is called on recomposition.

            // Clear the view if the trigger is set
            if (clearTrigger) {
                view.clear()
            }

            // Push new sample data
            if (samples != null && sampleRate != null) {
                // The view handles its own downsampling and queueing
                view.pushSamples(sampleRate, 0.0, samples)
            }

            // Update interaction state
            view.setInteractionEnabled(isInteractionEnabled)
        }
    )
}