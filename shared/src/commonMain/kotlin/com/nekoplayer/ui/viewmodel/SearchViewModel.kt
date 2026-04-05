package com.nekoplayer.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.nekoplayer.data.api.BilibiliApi
import com.nekoplayer.data.api.MiguApi
import com.nekoplayer.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 搜索界面ViewModel
 * 同时搜索 Bilibili 和 Migu 两个源
 */
class SearchViewModel(
    private val bilibiliApi: BilibiliApi,
    private val miguApi: MiguApi
) : ScreenModel {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isLoadingPlayUrl = MutableStateFlow(false)
    val isLoadingPlayUrl: StateFlow<Boolean> = _isLoadingPlayUrl.asStateFlow()
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        
        screenModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // 同时搜索两个源
                val bilibiliDeferred = async { 
                    try { bilibiliApi.fuzzySearch(query) } catch (e: Exception) { emptyList() }
                }
                val miguDeferred = async { 
                    try { miguApi.search(query) } catch (e: Exception) { emptyList() }
                }
                
                val bilibiliResults = bilibiliDeferred.await()
                val miguResults = miguDeferred.await()
                
                // 合并结果：Bilibili 在前，Migu 在后，各限制20条避免过多
                val combinedResults = (bilibiliResults.take(20) + miguResults.take(20))
                    .distinctBy { it.id } // 去重
                
                _searchResults.value = combinedResults
                
                if (combinedResults.isEmpty()) {
                    _errorMessage.value = "未找到相关歌曲，换个关键词试试"
                }
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 获取歌曲播放链接
     */
    suspend fun getPlayUrl(song: Song): Song? {
        if (!song.playUrl.isNullOrEmpty()) return song
        
        _isLoadingPlayUrl.value = true
        
        return try {
            val playUrl = when (song.source) {
                com.nekoplayer.data.model.MusicSource.MIGU -> miguApi.getPlayUrl(song.sourceId)
                else -> bilibiliApi.getPlayUrl(song.sourceId)
            }
            
            if (playUrl != null) {
                song.copy(playUrl = playUrl)
            } else {
                _errorMessage.value = "获取播放链接失败"
                null
            }
        } catch (e: Exception) {
            _errorMessage.value = "获取播放链接失败: ${e.message}"
            null
        } finally {
            _isLoadingPlayUrl.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
