package com.efxcreator.model

import android.net.Uri

data class EfxProjectMetadata(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New EFX",
    val musicUriString: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val musicUri: Uri?
        get() = musicUriString?.let { Uri.parse(it) }
}