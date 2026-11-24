@file:Suppress("unused", "DEPRECATION")

package com.pico.spatial.ml.sample.hello

import android.graphics.Color
import android.graphics.Point
import android.os.SharedMemory
import com.pico.spatial.ml.securemr.*
import com.pico.spatial.ml.securemr.Pipeline.ColorConversion
import com.pico.spatial.ml.securemr.Pipeline.JavaScriptIO
import com.pico.spatial.ml.securemr.Pipeline.ModelInferenceType
import com.pico.spatial.ml.securemr.Pipeline.ModelNodeEncoding
import com.pico.spatial.ml.securemr.Pipeline.NormType
import com.pico.spatial.ml.securemr.Pipeline.NormalizeType
import com.pico.spatial.ml.securemr.Pipeline.RunTask
import com.pico.spatial.ml.securemr.Pipeline.SortType
import com.pico.spatial.ml.securemr.Pipeline.TextHorizontalAlignment
import com.pico.spatial.ml.securemr.Pipeline.TextVerticalAlignment

// Compile-only named calls, never invoked by the tutorial. Runtime tensor contracts remain
// required.
fun call_applyAffinePoint_0(
    pipeline: Pipeline,
    affineMatrix: Tensor,
    srcPoints: Tensor,
    affinedPoints: Tensor,
) =
    pipeline.applyAffinePoint(
        affineMatrix = affineMatrix,
        srcPoints = srcPoints,
        affinedPoints = affinedPoints,
    )

fun call_applyAffine_0(
    pipeline: Pipeline,
    affineMatrix: Tensor,
    srcImage: Tensor,
    affinedImage: Tensor,
) =
    pipeline.applyAffine(
        affineMatrix = affineMatrix,
        srcImage = srcImage,
        affinedImage = affinedImage,
    )

fun call_argmax_0(pipeline: Pipeline, source: Tensor, result: Tensor) =
    pipeline.argmax(source = source, result = result)

fun call_arithmetic_0(
    pipeline: Pipeline,
    result: Tensor,
    arithmeticOperations: PipelineArithmeticScope.() -> PipelineArithmeticScope.TensorArithmetic,
) = pipeline.arithmetic(result = result, arithmeticOperations = arithmeticOperations)

fun call_bitwiseAnd_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.bitwiseAnd(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_bitwiseOr_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.bitwiseOr(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_bytewiseAll_0(pipeline: Pipeline, operand: Tensor, result: Tensor) =
    pipeline.bytewiseAll(operand = operand, result = result)

fun call_bytewiseAny_0(pipeline: Pipeline, operand: Tensor, result: Tensor) =
    pipeline.bytewiseAny(operand = operand, result = result)

fun call_captureMicrophone_0(
    pipeline: Pipeline,
    sampleRate: Int,
    stereoTrackResult: Tensor,
    timestampResult: Tensor?,
) =
    pipeline.captureMicrophone(
        sampleRate = sampleRate,
        stereoTrackResult = stereoTrackResult,
        timestampResult = timestampResult,
    )

fun call_captureMicrophone_1(
    pipeline: Pipeline,
    sampleRate: Int,
    leftTrackResult: Tensor?,
    rightTrackResult: Tensor?,
    timestampResult: Tensor?,
) =
    pipeline.captureMicrophone(
        sampleRate = sampleRate,
        leftTrackResult = leftTrackResult,
        rightTrackResult = rightTrackResult,
        timestampResult = timestampResult,
    )

fun call_convertColor_0(
    pipeline: Pipeline,
    opencvConvertStr: String,
    source: Tensor,
    result: Tensor,
) = pipeline.convertColor(opencvConvertStr = opencvConvertStr, source = source, result = result)

fun call_convertColor_1(
    pipeline: Pipeline,
    conversionType: ColorConversion,
    source: Tensor,
    result: Tensor,
) = pipeline.convertColor(conversionType = conversionType, source = source, result = result)

fun call_copy_0(pipeline: Pipeline, src: Tensor, dst: Tensor) = pipeline.copy(src = src, dst = dst)

fun call_copy_1(pipeline: Pipeline, src: PipelineTensorSlice, dst: Tensor) =
    pipeline.copy(src = src, dst = dst)

fun call_copy_2(pipeline: Pipeline, src: Tensor, dst: PipelineTensorSlice) =
    pipeline.copy(src = src, dst = dst)

fun call_copy_3(pipeline: Pipeline, src: PipelineTensorSlice, dst: PipelineTensorSlice) =
    pipeline.copy(src = src, dst = dst)

fun call_elementwiseMax_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.elementwiseMax(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_elementwiseMin_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.elementwiseMin(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_elementwiseMultiply_0(
    pipeline: Pipeline,
    tensor1: Tensor,
    tensor2: Tensor,
    result: Tensor,
) = pipeline.elementwiseMultiply(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_equal_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.equal(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_getAffine_0(
    pipeline: Pipeline,
    srcPoints: Tensor,
    affinedPoints: Tensor,
    affineMatrixResult: Tensor,
) =
    pipeline.getAffine(
        srcPoints = srcPoints,
        affinedPoints = affinedPoints,
        affineMatrixResult = affineMatrixResult,
    )

fun call_getDepthMap_0(pipeline: Pipeline, depthMapResult: Tensor) =
    pipeline.getDepthMap(depthMapResult = depthMapResult)

fun call_inversion_0(pipeline: Pipeline, source: Tensor, result: Tensor) =
    pipeline.inversion(source = source, result = result)

fun call_largerEqual_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.largerEqual(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_largerThan_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.largerThan(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_makeTransform_0(
    pipeline: Pipeline,
    rotationVec: Tensor?,
    translationVec: Tensor?,
    scaleVec: Tensor?,
    result: Tensor,
) =
    pipeline.makeTransform(
        rotationVec = rotationVec,
        translationVec = translationVec,
        scaleVec = scaleVec,
        result = result,
    )

fun call_newLocalTensor_0(pipeline: Pipeline, config: Tensor.InitInfo) =
    pipeline.newLocalTensor(config = config)

fun call_newLocalTensor_1(pipeline: Pipeline, point2: Point) =
    pipeline.newLocalTensor(point2 = point2)

fun call_newLocalTensor_2(pipeline: Pipeline, point2Array: Array<Point>) =
    pipeline.newLocalTensor(point2Array = point2Array)

fun call_newLocalTensor_3(pipeline: Pipeline, color: Color) = pipeline.newLocalTensor(color = color)

fun call_newLocalTensor_4(pipeline: Pipeline, colorArray: Array<Color>) =
    pipeline.newLocalTensor(colorArray = colorArray)

fun call_newLocalTensor_5(pipeline: Pipeline, doubleVal: Double) =
    pipeline.newLocalTensor(doubleVal = doubleVal)

fun call_newLocalTensor_6(pipeline: Pipeline, doubleVal: DoubleArray) =
    pipeline.newLocalTensor(doubleVal = doubleVal)

fun call_newLocalTensor_7(pipeline: Pipeline, floatVal: Float) =
    pipeline.newLocalTensor(floatVal = floatVal)

fun call_newLocalTensor_8(pipeline: Pipeline, floatVal: FloatArray) =
    pipeline.newLocalTensor(floatVal = floatVal)

fun call_newLocalTensor_9(pipeline: Pipeline, intVal: Int) =
    pipeline.newLocalTensor(intVal = intVal)

fun call_newLocalTensor_10(pipeline: Pipeline, intVal: IntArray) =
    pipeline.newLocalTensor(intVal = intVal)

fun call_newLocalTensor_11(pipeline: Pipeline, shortVal: Short) =
    pipeline.newLocalTensor(shortVal = shortVal)

fun call_newLocalTensor_12(pipeline: Pipeline, shortVal: ShortArray) =
    pipeline.newLocalTensor(shortVal = shortVal)

fun call_newLocalTensor_13(pipeline: Pipeline, char: Byte) = pipeline.newLocalTensor(char = char)

fun call_newLocalTensor_14(pipeline: Pipeline, string: String) =
    pipeline.newLocalTensor(string = string)

fun call_newPlaceholderLike_0(pipeline: Pipeline, globalTensor: GlobalTensor) =
    pipeline.newPlaceholderLike(globalTensor = globalTensor)

fun call_newPlaceholder_0(pipeline: Pipeline, config: Tensor.InitInfo) =
    pipeline.newPlaceholder(config = config)

fun call_newSceneFromGLTF_0(pipeline: Pipeline, gltfSceneMemory: SharedMemory) =
    pipeline.newSceneFromGLTF(gltfSceneMemory = gltfSceneMemory)

fun call_nonMaximumSuppression_0(
    pipeline: Pipeline,
    iou: Float,
    scores: Tensor,
    boxes: Tensor,
    scoresResult: Tensor?,
    boxesResult: Tensor?,
    indicesResult: Tensor?,
) =
    pipeline.nonMaximumSuppression(
        iou = iou,
        scores = scores,
        boxes = boxes,
        scoresResult = scoresResult,
        boxesResult = boxesResult,
        indicesResult = indicesResult,
    )

fun call_norm_0(pipeline: Pipeline, type: NormType, srcTensor: Tensor, result: Tensor) =
    pipeline.norm(type = type, srcTensor = srcTensor, result = result)

fun call_normalize_0(
    pipeline: Pipeline,
    type: NormalizeType,
    source: Tensor,
    alphaBeta: Tensor?,
    result: Tensor,
) = pipeline.normalize(type = type, source = source, alphaBeta = alphaBeta, result = result)

fun call_notEqual_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.notEqual(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_outputSounds_0(pipeline: Pipeline, sampleRate: Int, audioTrack: Tensor) =
    pipeline.outputSounds(sampleRate = sampleRate, audioTrack = audioTrack)

fun call_rectifiedVSTAccess_0(
    pipeline: Pipeline,
    rightImageResult: Tensor?,
    leftImageResult: Tensor?,
    timestampResult: Tensor?,
    cameraMatrixResult: Tensor?,
) =
    pipeline.rectifiedVSTAccess(
        rightImageResult = rightImageResult,
        leftImageResult = leftImageResult,
        timestampResult = timestampResult,
        cameraMatrixResult = cameraMatrixResult,
    )

fun call_runJavaScript_0(pipeline: Pipeline, script: String, jsVarIO: List<JavaScriptIO>) =
    pipeline.runJavaScript(script = script, jsVarIO = jsVarIO)

fun call_runModelInference_0(
    pipeline: Pipeline,
    modelName: String,
    modelType: ModelInferenceType,
    modelBinary: SharedMemory,
    inputs: Array<ModelNodeEncoding>,
    outputs: Array<ModelNodeEncoding>,
) =
    pipeline.runModelInference(
        modelName = modelName,
        modelType = modelType,
        modelBinary = modelBinary,
        inputs = inputs,
        outputs = outputs,
    )

fun call_singularValueDecomposition_0(
    pipeline: Pipeline,
    source: Tensor,
    wResult: Tensor?,
    uResult: Tensor?,
    vtResult: Tensor?,
) =
    pipeline.singularValueDecomposition(
        source = source,
        wResult = wResult,
        uResult = uResult,
        vtResult = vtResult,
    )

fun call_smallerEqual_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.smallerEqual(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_smallerThan_0(pipeline: Pipeline, tensor1: Tensor, tensor2: Tensor, result: Tensor) =
    pipeline.smallerThan(tensor1 = tensor1, tensor2 = tensor2, result = result)

fun call_solvePnP_0(
    pipeline: Pipeline,
    objPoints: Tensor,
    imgPoints: Tensor,
    camMatrix: Tensor,
    rotationVecResult: Tensor?,
    translationVecResult: Tensor?,
) =
    pipeline.solvePnP(
        objPoints = objPoints,
        imgPoints = imgPoints,
        camMatrix = camMatrix,
        rotationVecResult = rotationVecResult,
        translationVecResult = translationVecResult,
    )

fun call_sortMatrix_0(
    pipeline: Pipeline,
    sortType: SortType,
    source: Tensor,
    sortedResult: Tensor?,
    indexResult: Tensor?,
) =
    pipeline.sortMatrix(
        sortType = sortType,
        source = source,
        sortedResult = sortedResult,
        indexResult = indexResult,
    )

fun call_sortVec_0(
    pipeline: Pipeline,
    source: Tensor,
    sortedResult: Tensor?,
    indexResult: Tensor?,
) = pipeline.sortVec(source = source, sortedResult = sortedResult, indexResult = indexResult)

fun call_submit_0(
    pipeline: Pipeline,
    parameters: Map<PipelineTensorPlaceholder, GlobalTensor>,
    condition: GlobalTensor?,
    waitFor: RunTask?,
) = pipeline.submit(parameters = parameters, condition = condition, waitFor = waitFor)

fun call_switchCHWAndHWC_0(pipeline: Pipeline, source: Tensor, result: Tensor) =
    pipeline.switchCHWAndHWC(source = source, result = result)

fun call_switchSceneVisibility_0(pipeline: Pipeline, sceneEntity: Tensor, visible: Tensor) =
    pipeline.switchSceneVisibility(sceneEntity = sceneEntity, visible = visible)

fun call_updateSceneGraphProperty_0(
    pipeline: Pipeline,
    sceneEntity: Tensor,
    entityPath: String,
    targetProperty: SceneGraphProperty,
    data: Tensor,
) =
    pipeline.updateSceneGraphProperty(
        sceneEntity = sceneEntity,
        entityPath = entityPath,
        targetProperty = targetProperty,
        data = data,
    )

fun call_updateSceneGraphTextContent_0(
    pipeline: Pipeline,
    sceneEntity: Tensor,
    entityPath: String,
    text: String,
) =
    pipeline.updateSceneGraphTextContent(
        sceneEntity = sceneEntity,
        entityPath = entityPath,
        text = text,
    )

fun call_updateSceneGraphTextHorizontalAlignment_0(
    pipeline: Pipeline,
    sceneEntity: Tensor,
    entityPath: String,
    horizontalAlignment: TextHorizontalAlignment,
) =
    pipeline.updateSceneGraphTextHorizontalAlignment(
        sceneEntity = sceneEntity,
        entityPath = entityPath,
        horizontalAlignment = horizontalAlignment,
    )

fun call_updateSceneGraphTextVerticalAlignment_0(
    pipeline: Pipeline,
    sceneEntity: Tensor,
    entityPath: String,
    verticalAlignment: TextVerticalAlignment,
) =
    pipeline.updateSceneGraphTextVerticalAlignment(
        sceneEntity = sceneEntity,
        entityPath = entityPath,
        verticalAlignment = verticalAlignment,
    )

fun call_uvTo3DInCameraSpace_0(
    pipeline: Pipeline,
    uv: Tensor,
    timestamp: Tensor,
    camMatrix: Tensor,
    leftImage: Tensor,
    rightImage: Tensor,
    point3Result: Tensor,
) =
    pipeline.uvTo3DInCameraSpace(
        uv = uv,
        timestamp = timestamp,
        camMatrix = camMatrix,
        leftImage = leftImage,
        rightImage = rightImage,
        point3Result = point3Result,
    )

fun call_get(pipeline: Pipeline, source: PipelineTensor, indices: PipelineTensor, result: Tensor) {
    pipeline.copy(source[0..1, 0..1], result)
    pipeline.copy(source[0..3 step 2, 0..1], result)
    pipeline.copy(source[indices], result)
}
