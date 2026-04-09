/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.detection

import android.graphics.RectF

/**
 * One object detection result. Coordinates in [box] are normalized to the source frame
 * (0..1 in both axes), so they're independent of the bitmap dimensions used at inference time.
 */
data class Detection(
    val label: String,
    val score: Float,
    val box: RectF,
    val frameTimestampUs: Long,
)
