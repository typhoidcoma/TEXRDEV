/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/**
 * Wraps a MediaPipe [ObjectDetector] running in LIVE_STREAM mode with the GPU delegate.
 *
 * Frames are submitted via [submit]; results are delivered asynchronously through [onResults]
 * on MediaPipe's worker thread. Submissions are throttled to ~10 Hz so the GPU never builds a
 * backlog at the camera's 24 FPS.
 *
 * Call [close] when the streaming session ends to release native resources.
 */
class ObjectDetectorEngine(
    context: Context,
    private val onResults: (List<Detection>) -> Unit,
    modelAsset: String = MODEL_ASSET,
    scoreThreshold: Float = 0.4f,
    maxResults: Int = 5,
    private val minSubmitIntervalUs: Long = 100_000L, // ~10 Hz
) : AutoCloseable {

  private val detector: ObjectDetector

  private var lastSubmitUs: Long = 0L
  private var firstSubmit: Boolean = true
  private var lastTimestampMs: Long = 0L

  // Held so the result listener can stamp the original frame timestamp on each Detection
  @Volatile private var inFlightTimestampUs: Long = 0L

  init {
    // CPU delegate (not GPU). EfficientDet-Lite0 is small enough to run at >30 FPS on CPU on
    // any modern phone, and the GPU delegate's EGL context grabs were conflicting with the DAT
    // video pipeline (white preview / no frames delivered when GPU was enabled).
    val baseOptions =
        BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath(modelAsset)
            .build()

    val options =
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(maxResults)
            .setResultListener { result, input -> handleResult(result, input.width, input.height) }
            .setErrorListener { error -> Log.e(TAG, "MediaPipe detection error", error) }
            .build()

    detector = ObjectDetector.createFromOptions(context, options)
    Log.i(TAG, "ObjectDetectorEngine ready (model=$modelAsset, threshold=$scoreThreshold)")
  }

  /**
   * Submit a frame for inference. No-op if called more often than the throttle interval.
   *
   * IMPORTANT: the caller's bitmap is the shared, in-place-mutated cache from
   * YuvToBitmapConverter — every video frame overwrites its pixels. We MUST take a private
   * copy before handing it to MediaPipe, otherwise MediaPipe's async worker thread will read
   * partially-overwritten pixels and the display path will fight with us over the same
   * bitmap, producing a blank/white preview.
   */
  fun submit(bitmap: Bitmap, frameTimestampUs: Long) {
    if (!firstSubmit && frameTimestampUs - lastSubmitUs < minSubmitIntervalUs) return
    firstSubmit = false
    lastSubmitUs = frameTimestampUs

    // MediaPipe LIVE_STREAM requires strictly monotonically increasing millisecond timestamps.
    var tsMs = frameTimestampUs / 1000
    if (tsMs <= lastTimestampMs) tsMs = lastTimestampMs + 1
    lastTimestampMs = tsMs

    inFlightTimestampUs = frameTimestampUs
    try {
      val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false)
      val mpImage = BitmapImageBuilder(snapshot).build()
      detector.detectAsync(mpImage, tsMs)
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to submit frame for detection", t)
    }
  }

  private fun handleResult(result: ObjectDetectorResult, imgW: Int, imgH: Int) {
    val frameTsUs = inFlightTimestampUs
    val detections =
        result.detections().map { d ->
          val cat = d.categories().firstOrNull()
          val bb = d.boundingBox()
          val w = imgW.toFloat().coerceAtLeast(1f)
          val h = imgH.toFloat().coerceAtLeast(1f)
          Detection(
              label = cat?.categoryName() ?: "?",
              score = cat?.score() ?: 0f,
              box = RectF(bb.left / w, bb.top / h, bb.right / w, bb.bottom / h),
              frameTimestampUs = frameTsUs,
          )
        }
    logDetections(frameTsUs, detections)
    onResults(detections)
  }

  private fun logDetections(frameTsUs: Long, detections: List<Detection>) {
    val parts =
        detections.joinToString(separator = " ") { d ->
          val pct = (d.score * 100f).toInt()
          val b = d.box
          "[${d.label}:${pct}% (${"%.2f".format(b.left)},${"%.2f".format(b.top)}," +
              "${"%.2f".format(b.width())},${"%.2f".format(b.height())})]"
        }
    Log.i(LOG_TAG, "t=$frameTsUs n=${detections.size} $parts")
  }

  override fun close() {
    try {
      detector.close()
    } catch (t: Throwable) {
      Log.w(TAG, "Error closing ObjectDetector", t)
    }
    Log.i(TAG, "ObjectDetectorEngine: closed")
  }

  companion object {
    private const val TAG = "ObjectDetectorEngine"
    private const val LOG_TAG = "Detection"
    const val MODEL_ASSET = "efficientdet_lite0.tflite"
  }
}
