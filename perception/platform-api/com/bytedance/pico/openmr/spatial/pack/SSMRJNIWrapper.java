// Copyright 2026 PICO. All rights reserved.
// Compile-only public signatures from SpatialML foundation 0.11.7, as used by SDK 6.1.9.
// The actual implementation and native loader are supplied by PICO OS. Never package these stubs.
package com.bytedance.pico.openmr.spatial.pack;

import android.os.SharedMemory;

public class SSMRJNIWrapper {
    public static native long createSessionHandle(
            long appFormId, int imageWidth, int imageHeight, int width, int height, int depth);

    public static native void destroySessionHandle(long sessionHandle);

    public static native long createTensor(long sessionHandle, int flag, int[] dimensions);

    public static native void resetTensor(long tensorId, SharedMemory content);

    public static native void destroyTensor(long tensorId);

    public static native SharedMemory readbackSharedMemory(long tensorId);

    public static class PipelineJNIWrapper {
        public static native long create(long sessionId);

        public static native void destroy(long pipelineId);

        public static native long submit(
                long pipelineId, long waitFor, long conditionTensor, long tensorMap);

        public static native long createTensor(
                long pipelineId, int flag, int[] dimensions, boolean hasLocalMemory);

        public static native void resetTensor(long pipelineId, long tensorId, SharedMemory content);

        public static native long addOperator(long pipelineId, int type, String config);

        public static native long addModelInference(
                long pipelineId,
                String name,
                SharedMemory binary,
                long encoding,
                int runtime,
                int backend);

        public static native void setOperand(long operator, long tensor, int index);

        public static native void setResult(long operator, long tensor, int index);

        public static native void setOperand(long operator, long tensor, String name);

        public static native void setResult(long operator, long tensor, String name);
    }
}
