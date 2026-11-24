// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.ml.sample.perception.StageRegistration

/** Paired image mapping, intrinsics and head correction. Values apply only to the named ROM. */
enum class FaceCalibrationProfile {
    SERVICE,
    SWAN_STEREO;

    val imageTransform: FloatArray
        get() =
            if (this == SWAN_STEREO)
                floatArrayOf(.7673501372337341f, 0f, 377.70703125f, 0f, 1f, 0f, 0f, 0f, 1f)
            else identity

    val sourceIntrinsics: FloatArray
        get() = if (this == SWAN_STEREO) SwanCalibration.sourceIntrinsics else FloatArray(9)

    /** The model treats zero as service calibration and nonzero as supplied calibration. */
    val modelMode: Float
        get() = if (this == SWAN_STEREO) 5f else 0f

    fun serviceToStage(head: FloatArray, optical: FloatArray, service: FloatArray): FloatArray =
        StageRegistration.serviceToStage(
            head,
            if (this == SWAN_STEREO)
                StageRegistration.multiply(SwanCalibration.headCorrection, optical)
            else optical,
            service,
        )

    companion object {
        const val SWAN_ROM =
            "Pico/swan/swan:16/BQ2A.260122.002-BP2A.250705.008/20261014024400:userdebug/test-keys"
        val identity: FloatArray
            get() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        fun forRom(fingerprint: String): FaceCalibrationProfile =
            if (fingerprint == SWAN_ROM) SWAN_STEREO else SERVICE
    }
}

/** Accepted camera/registration pair measured on Swan; never apply to another ROM implicitly. */
private object SwanCalibration {
    val sourceIntrinsics: FloatArray
        get() =
            floatArrayOf(
                2358.870351068085f,
                0f,
                1561.261136055872f,
                0f,
                1815.389847770632f,
                1222.179704991295f,
                0f,
                0f,
                1f,
            )

    val headCorrection: FloatArray
        get() =
            floatArrayOf(
                0.9997588246273411f,
                -0.0011340528732299644f,
                0.02193186047371193f,
                -0.004310512859900328f,
                0.0012597332461165298f,
                0.9999828613348418f,
                -0.005717526452179058f,
                -0.04409491847453374f,
                -0.021925000613598113f,
                0.00574377581939419f,
                0.999743118699714f,
                0.04219700998229438f,
                0f,
                0f,
                0f,
                1f,
            )
}
