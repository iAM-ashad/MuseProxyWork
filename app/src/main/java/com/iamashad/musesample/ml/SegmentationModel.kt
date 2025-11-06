package com.iamashad.musesample.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.iamashad.musesample.utils.TAG_PCG_SEG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.min

class SegmentationModel(
    private val assetLoaderContext: android.content.Context,
    assetPath: String = "ml/pcg_segmentation.onnx",
    externalFilePath: String? = null
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes: ByteArray = try {
            if (!externalFilePath.isNullOrEmpty()) {
                val f = java.io.File(externalFilePath)
                if (f.exists()) f.readBytes() else throw IllegalStateException("External model file not found: $externalFilePath")
            } else {
                assetLoaderContext.assets.open(assetPath).use { it.readBytes() }
            }
        } catch (t: Throwable) {
            Log.e(TAG_PCG_SEG, "Failed to read model bytes: ${t.message}")
            throw t
        }

        try {
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            session = env.createSession(modelBytes, opts)
            for ((name, info) in session.inputInfo) Log.i("PCG_SEG", "Input: $name -> $info")
            for ((name, info) in session.outputInfo) Log.i("PCG_SEG", "Output: $name -> $info")
            Log.i(TAG_PCG_SEG, "ONNX model loaded OK (size=${modelBytes.size} bytes)")
        } catch (t: Throwable) {
            Log.e(TAG_PCG_SEG, "Failed to create OrtSession: ${t.message}")
            throw t
        }
    }

    /**
     * Run a single window (compat shim) — delegates to batched version of batchSize=1.
     */
    suspend fun runSegmentationWindow(
        windowInput: FloatArray,
        metaDefaults: FloatArray? = null,
        flagsDefault: Long = 0L
    ): SegmentationOutputs = withContext(Dispatchers.Default) {
        val list =
            runSegmentationWindows(listOf(windowInput), metaDefaults, flagsDefault, batchSize = 1)
        list[0]
    }

    /**
     * Run many windows in batches. Each window is a FloatArray of length 3*64*188 (channel-first).
     * Returns a list of SegmentationOutputs in the same order as input windows.
     *
     * metaDefaults: FloatArray length >= 3 used for all windows in the batch (if model expects meta).
     * flagsDefault: Long scalar used for all windows in the batch.
     * batchSize: how many windows to pack per session.run (tune for device memory; 4..16 is typical).
     */
    suspend fun runSegmentationWindows(
        windows: List<FloatArray>,
        metaDefaults: FloatArray? = null,
        flagsDefault: Long = 0L,
        batchSize: Int = 8
    ): List<SegmentationOutputs> = withContext(Dispatchers.Default) {
        if (windows.isEmpty()) return@withContext emptyList()

        val inputNames = session.inputNames
        val expectMeta = inputNames.contains("meta")
        val expectFlags = inputNames.contains("flags")

        val elementPerWindow = 3 * 64 * 188
        // quick sanity
        for ((i, w) in windows.withIndex()) {
            require(w.size == elementPerWindow) { "Window[$i] has incorrect length ${w.size}, expected $elementPerWindow" }
        }

        val outputs = ArrayList<SegmentationOutputs>(windows.size)
        var idx = 0
        while (idx < windows.size) {
            val end = min(idx + batchSize, windows.size)
            val curBatch = windows.subList(idx, end)
            val curBatchSize = curBatch.size

            // build contiguous float array for the batch: [B,3,64,188] layout (channel-first per window)
            val batchArr = FloatArray(curBatchSize * elementPerWindow)
            var destOff = 0
            for (w in curBatch) {
                // copy entire window
                System.arraycopy(w, 0, batchArr, destOff, elementPerWindow)
                destOff += elementPerWindow
            }

            // create tensors
            val tensorsToClose = mutableListOf<OnnxTensor>()
            val inputMap = mutableMapOf<String, OnnxTensor>()

            val batchShape = longArrayOf(curBatchSize.toLong(), 3L, 64L, 188L)
            val batchBuf = FloatBuffer.wrap(batchArr)
            val imageName = when {
                inputNames.contains("input_image") -> "input_image"
                inputNames.contains("input") -> "input"
                else -> inputNames.first()
            }
            val imageTensor = OnnxTensor.createTensor(env, batchBuf, batchShape)
            inputMap[imageName] = imageTensor
            tensorsToClose += imageTensor

            // meta: replicate per row if expected
            if (expectMeta) {
                val metaArr = FloatArray(curBatchSize * 3)
                val m =
                    if (metaDefaults != null && metaDefaults.size >= 3) metaDefaults else floatArrayOf(
                        0f,
                        0f,
                        0f
                    )
                var mo = 0
                for (b in 0 until curBatchSize) {
                    metaArr[mo++] = m[0]; metaArr[mo++] = m[1]; metaArr[mo++] = m[2]
                }
                val metaBuf = FloatBuffer.wrap(metaArr)
                val metaShape = longArrayOf(curBatchSize.toLong(), 3L)
                val metaTensor = OnnxTensor.createTensor(env, metaBuf, metaShape)
                inputMap["meta"] = metaTensor
                tensorsToClose += metaTensor
            }

            // flags: replicate
            if (expectFlags) {
                val flagsArr = LongArray(curBatchSize)
                for (b in 0 until curBatchSize) flagsArr[b] = flagsDefault
                val flagsBuf = LongBuffer.wrap(flagsArr)
                val flagsShape = longArrayOf(curBatchSize.toLong())
                val flagsTensor = OnnxTensor.createTensor(env, flagsBuf, flagsShape)
                inputMap["flags"] = flagsTensor
                tensorsToClose += flagsTensor
            }

            Log.i(TAG_PCG_SEG, "Running batched inference (B=$curBatchSize) inputs=${inputMap.keys}")

            val results = session.run(inputMap)

            // results[0].value expected to be Array(batch) -> [4][64][188] etc.
            val segObj = results[0].value
            val segBatch =
                extractBatchedSeg(segObj) // returns List<Array<Array<FloatArray>>> size == curBatchSize

            // append per-sample outputs
            for (seg in segBatch) outputs.add(SegmentationOutputs(seg))

            // cleanup
            try {
                results.close()
            } catch (_: Throwable) {
            }
            for (t in tensorsToClose) try {
                t.close()
            } catch (_: Throwable) {
            }

            idx = end
        }

        outputs.toList()
    }

    // helper to convert results[0].value into a list of segs (one per batch element)
    @Suppress("UNCHECKED_CAST")
    private fun extractBatchedSeg(obj: Any?): List<Array<Array<FloatArray>>> {
        // obj is expected as Array(batch) where each element is Array[4][64][188]
        val top = obj as Array<*>
        val out = ArrayList<Array<Array<FloatArray>>>(top.size)
        for (i in top.indices) {
            val a1 = top[i] as Array<*>
            val v1 = Array(a1.size) { j ->
                val a2 = a1[j] as Array<*>
                Array(a2.size) { k ->
                    (a2[k] as FloatArray).copyOf()
                }
            }
            out.add(v1)
        }
        return out
    }

    fun close() {
        try {
            session.close()
        } catch (_: Throwable) {
        }
        try {
            env.close()
        } catch (_: Throwable) {
        }
    }
}

@Suppress("ArrayInDataClass")
data class SegmentationOutputs(
    // segTuple: [4][64][188] for a single sample
    val segTuple: Array<Array<FloatArray>>
)

