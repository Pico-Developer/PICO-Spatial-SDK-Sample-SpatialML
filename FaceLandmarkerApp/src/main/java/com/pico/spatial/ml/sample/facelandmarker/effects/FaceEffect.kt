// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

enum class FaceEffect(val label: String, val description: String) {
    MESH("Face mesh", "Transparent mesh follows the face. Check its alignment first."),
    HELMET("Opening helmet", "A hinged faceplate opens to reveal your face."),
    CLOSED_HELMET("Closed helmet", "A solid face cover follows your head, with no moving visor."),
    EYES("Buggy eyes + grin", "Big blinking eyes and an oversized toothy grin follow your face."),
    PIGTAILS("Red pigtails", "Outward red braids and blue bows follow your head."),
    FLAG("USA cheek flags", "A USA flag on each cheek follows your face."),
    TEAM("Team colors", "Blue and gold face paint follows the measured face mesh."),
    LION("Lion dance", "Open your mouth, blink, and look around to animate the lion.");

    val outputs: FaceOutputs
        get() = if (this == LION || this == EYES) FaceOutputs.ALL else FaceOutputs.POSE_MESH
}
