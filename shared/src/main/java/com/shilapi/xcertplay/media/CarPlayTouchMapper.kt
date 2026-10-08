package com.shilapi.xcertplay.media

import android.view.MotionEvent
import com.shilapi.xcertplay.airplay.AirPlayContact

/** Keeps Android pointer IDs in stable CarPlay HID slots for one view's gesture sequence. */
class CarPlayTouchMapper {
    private val pointerIds = IntArray(2) { -1 }

    fun reset() { pointerIds.fill(-1) }

    fun contacts(event: MotionEvent, viewWidth: Int, viewHeight: Int): List<AirPlayContact> =
        contacts(event, CarPlayVideoLayout(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()))

    fun contacts(event: MotionEvent, content: CarPlayVideoLayout): List<AirPlayContact> {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) reset()
        for (slot in pointerIds.indices) {
            if (event.findPointerIndex(pointerIds[slot]) < 0) pointerIds[slot] = -1
        }
        for (index in 0 until event.pointerCount) {
            val id = event.getPointerId(index)
            if (id !in pointerIds) {
                val slot = pointerIds.indexOf(-1)
                if (slot >= 0) pointerIds[slot] = id
            }
        }
        val allUp = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        val lifted = if (action == MotionEvent.ACTION_POINTER_UP) event.getPointerId(event.actionIndex) else -1
        val width = content.width.coerceAtLeast(1f)
        val height = content.height.coerceAtLeast(1f)
        val contacts = pointerIds.withIndex().mapNotNull { (slot, id) ->
            val index = event.findPointerIndex(id)
            if (index < 0) null else AirPlayContact(
                id = slot,
                x = ((event.getX(index) - content.left).toDouble() / width).coerceIn(0.0, 1.0),
                y = ((event.getY(index) - content.top).toDouble() / height).coerceIn(0.0, 1.0),
                down = !allUp && id != lifted,
            )
        }
        if (allUp) reset()
        else pointerIds.indexOf(lifted).takeIf { lifted >= 0 && it >= 0 }?.let { pointerIds[it] = -1 }
        return contacts
    }
}
