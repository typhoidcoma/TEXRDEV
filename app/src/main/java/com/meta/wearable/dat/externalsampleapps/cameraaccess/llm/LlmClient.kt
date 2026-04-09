/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.llm

import android.graphics.Bitmap
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.detection.Detection

/**
 * Hands the latest frame and detection list to a vision-capable LLM and returns a short answer.
 * Real implementations (Anthropic Claude, Gemini, on-device, …) should be added as separate
 * classes implementing this interface — the call site in StreamViewModel won't change.
 */
interface LlmClient {
  suspend fun ask(prompt: String, frame: Bitmap?, detections: List<Detection>): String
}

/**
 * Placeholder implementation that just logs the request payload and echoes a stub response.
 * Use this until a real provider is wired up.
 */
class NoopLlmClient : LlmClient {
  override suspend fun ask(
      prompt: String,
      frame: Bitmap?,
      detections: List<Detection>,
  ): String {
    val frameDesc = frame?.let { "${it.width}x${it.height}" } ?: "none"
    val detDesc =
        detections.joinToString(", ") { "${it.label}(${(it.score * 100).toInt()}%)" }
    Log.i(TAG, "ask prompt=\"$prompt\" frame=$frameDesc detections=[$detDesc]")
    return "(LLM not wired up yet) Saw ${detections.size} object(s): $detDesc"
  }

  companion object {
    private const val TAG = "NoopLlmClient"
  }
}
