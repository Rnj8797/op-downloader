package com.opdownloader.app.domain.repository

import com.opdownloader.app.domain.model.DownloadItem
import com.opdownloader.app.domain.model.UrlInspectionResult
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    fun getActiveDownloads(): Flow<List<DownloadItem>>
    fun getDownloadById(id: String): Flow<DownloadItem?>
    suspend fun enqueueDownload(url: String, formatId: String, safeTitle: String): Result<String>
    suspend fun pauseDownload(id: String): Result<Unit>
    suspend fun resumeDownload(id: String): Result<Unit>
    suspend fun cancelDownload(id: String): Result<Unit>
    suspend fun deleteDownload(id: String, deleteLocalFile: Boolean): Result<Unit>
    suspend fun inspectUrl(url: String): Result<UrlInspectionResult>
}
