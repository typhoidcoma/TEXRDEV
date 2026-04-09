/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamScreen - DAT Camera Streaming UI
//
// This composable demonstrates the main streaming UI for DAT camera functionality. It shows how to
// display live video from wearable devices and handle photo capture.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.detection.Detection
import kotlin.math.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
) {
  val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
  var askDialogOpen by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { streamViewModel.startStream() }

  Box(modifier = modifier.fillMaxSize()) {
    streamUiState.videoFrame?.let { videoFrame ->
      // Use key() to force recomposition when frame counter changes,
      // even if the bitmap reference is the same (due to caching optimization)
      key(streamUiState.videoFrameCount) {
        Image(
            bitmap = videoFrame.asImageBitmap(),
            contentDescription = stringResource(R.string.live_stream),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
      }
      DetectionOverlay(
          detections = streamUiState.lastDetections,
          frameWidth = videoFrame.width,
          frameHeight = videoFrame.height,
          modifier = Modifier.fillMaxSize(),
      )
    }
    if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center),
      )
    }

    Box(modifier = Modifier.fillMaxSize().padding(all = 24.dp)) {
      Row(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .fillMaxWidth()
                  .height(56.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        SwitchButton(
            label = stringResource(R.string.stop_stream_button_title),
            onClick = {
              streamViewModel.stopStream()
              wearablesViewModel.navigateToDeviceSelection()
            },
            isDestructive = true,
            modifier = Modifier.weight(1f),
        )

        // Photo capture button
        CaptureButton(
            onClick = { streamViewModel.capturePhoto() },
        )

        // Ask-the-LLM button
        FilledIconButton(onClick = { askDialogOpen = true }) {
          Icon(
              imageVector = Icons.Filled.QuestionAnswer,
              contentDescription = "Ask about what you see",
          )
        }
      }
    }
  }

  if (askDialogOpen) {
    AskLlmDialog(
        isAsking = streamUiState.isAskingLlm,
        answer = streamUiState.lastLlmAnswer,
        onSubmit = { question -> streamViewModel.askLlm(question) },
        onDismiss = {
          askDialogOpen = false
          streamViewModel.clearLlmAnswer()
        },
    )
  }

  streamUiState.capturedPhoto?.let { photo ->
    if (streamUiState.isShareDialogVisible) {
      SharePhotoDialog(
          photo = photo,
          onDismiss = { streamViewModel.hideShareDialog() },
          onShare = { bitmap ->
            streamViewModel.sharePhoto(bitmap)
            streamViewModel.hideShareDialog()
          },
      )
    }
  }
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
  if (frameWidth <= 0 || frameHeight <= 0) return
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(color = Color.Black, fontSize = 12.sp)
  Canvas(modifier = modifier) {
    // Image is drawn with ContentScale.Crop: scale = max so one axis fills, the other overflows
    // and is center-cropped. We must apply the same transform to map normalized boxes to screen.
    val scale = max(size.width / frameWidth.toFloat(), size.height / frameHeight.toFloat())
    val displayedW = frameWidth * scale
    val displayedH = frameHeight * scale
    val offsetX = (size.width - displayedW) / 2f
    val offsetY = (size.height - displayedH) / 2f

    detections.forEach { d ->
      val left = offsetX + d.box.left * displayedW
      val top = offsetY + d.box.top * displayedH
      val w = d.box.width() * displayedW
      val h = d.box.height() * displayedH

      drawRect(
          color = Color(0xFF00E676),
          topLeft = Offset(left, top),
          size = Size(w, h),
          style = Stroke(width = 4f),
      )

      val labelText = "${d.label} ${(d.score * 100).toInt()}%"
      val measured = textMeasurer.measure(AnnotatedString(labelText), labelStyle)
      val padX = 6f
      val padY = 3f
      val bgW = measured.size.width + padX * 2
      val bgH = measured.size.height + padY * 2
      drawRect(
          color = Color(0xCC00E676),
          topLeft = Offset(left, (top - bgH).coerceAtLeast(0f)),
          size = Size(bgW, bgH),
      )
      drawText(
          textMeasurer = textMeasurer,
          text = labelText,
          topLeft = Offset(left + padX, (top - bgH).coerceAtLeast(0f) + padY),
          style = labelStyle,
      )
    }
  }
}

@Composable
private fun AskLlmDialog(
    isAsking: Boolean,
    answer: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var question by remember { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Ask about what you see") },
      text = {
        if (answer != null) {
          Text(answer)
        } else {
          OutlinedTextField(
              value = question,
              onValueChange = { question = it },
              label = { Text("Your question") },
              enabled = !isAsking,
          )
        }
      },
      confirmButton = {
        if (answer != null) {
          TextButton(onClick = onDismiss) { Text("Close") }
        } else {
          TextButton(
              onClick = { onSubmit(question) },
              enabled = !isAsking && question.isNotBlank(),
          ) {
            Text(if (isAsking) "Asking…" else "Ask")
          }
        }
      },
      dismissButton = {
        if (answer == null) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
        }
      },
  )
}
