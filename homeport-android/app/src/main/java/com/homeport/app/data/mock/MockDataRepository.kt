package com.homeport.app.data.mock

import com.homeport.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock data provider simulating the HOMEPORT P2P network.
 * Replace with real WebRTC P2P implementation in Phase 3.
 */
@Singleton
class MockDataRepository @Inject constructor() {

    val devices: List<DeviceInfo> = emptyList()
    val recentFiles: List<FileItem> = emptyList()
    val laptopFiles: List<FileItem> = emptyList()
    val activeTransfers: List<TransferItem> = emptyList()
    val activityEvents: List<ActivityEvent> = emptyList()
    val searchResults: List<FileItem> = emptyList()

    val storageCategories: List<Pair<String, Long>> = listOf(
        Pair("Documents", 0L),
        Pair("Videos", 0L),
        Pair("Pictures", 0L),
        Pair("Projects", 0L),
        Pair("Music", 0L),
        Pair("Other", 0L)
    )

    val sharedFolders: List<SharedFolder> = emptyList()
}

data class SharedFolder(
    val id: String,
    val path: String,
    val displayName: String,
    val isShared: Boolean,
    val permissions: Set<Permission>
)
