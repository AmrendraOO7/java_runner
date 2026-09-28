package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardHelper {

    fun copyText(context: Context, text: String, label: String = "Java Code", showToast: Boolean = true): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
                if (showToast) {
                    val preview = if (text.length > 30) text.take(30) + "..." else text
                    Toast.makeText(context, "Copied (${text.length} chars)", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun getClipboardText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            if (!clipboard.hasPrimaryClip()) return null
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                item.coerceToText(context)?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
