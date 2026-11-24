// Copyright 2026 PICO. All rights reserved.
// Compile-only public signatures. The headset supplies the implementation.
package com.bytedance.pico.openmr.spatial.pack.utils;

public class ModelEncoding {
    public enum EncodingType {
        FLOATING_POINT32,
        UNSIGNED_FIXED_POINT8,
        SIGNED_FIXED_POINT8,
        UNSIGNED_FIXED_POINT16,
        INT32
    }

    public static native long create();

    public static native void destroy(long encoding);

    public static native void add(
            long encoding, boolean input, String name, String alias, EncodingType type);
}
