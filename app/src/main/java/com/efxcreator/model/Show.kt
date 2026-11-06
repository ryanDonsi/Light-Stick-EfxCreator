package com.efxcreator.model

/**
 * 공연: 여러 곡(EFX 프로젝트)을 묶어 둔 목록.
 * [songIds] 는 [EfxProjectMetadata.id] 이며 공연 안에서의 순서를 따른다.
 */
data class Show(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Show",
    val songIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** 공연 일시 (epoch ms, 기기 시간대 기준). 정해지지 않았으면 null */
    val performanceAt: Long? = null,
    /** 앱 내부에 복사해 둔 공연 이미지 경로. 없으면 GLOWSYNC 기본 이미지를 쓴다 */
    val imagePath: String? = null
)
