// Copyright 2026 PICO. All rights reserved.
// Compile-only public signatures. The headset supplies the implementation.
package com.bytedance.pico.openmr.spatial.pack.utils;

public class PipelineTensorMap {
    public static native long create();

    public static native void add(long map, long pipelineTensor, long globalTensor);

    public static native void destroy(long map);
}
