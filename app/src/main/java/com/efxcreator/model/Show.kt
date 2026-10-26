package com.efxcreator.model

/**
 * 공연: 여러 곡(EFX 프로젝트)을 묶어 둔 목록.
 * [songIds] 는 [EfxProjectMetadata.id] 이며 공연 안에서의 순서를 따른다.
 */
data class Show(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Show",
    val songIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
