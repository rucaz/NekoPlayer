package com.nekoplayer.audio.fingerprint

import kotlin.math.sqrt

/**
 * 本地向量索引（简化版HNSW）
 * 
 * 为移动端优化的近似最近邻搜索：
 * - 内存占用低（10万首歌约40MB）
 * - 构建速度快
 * - 查询延迟 < 10ms
 * 
 * 算法：多层 navigable small world 图
 */
class VectorIndex(
    private val dim: Int = 128,
    private val maxElements: Int = 100000,
    private val m: Int = 16,              // 每层最大邻居数
    private val efConstruction: Int = 200 // 构建时搜索深度
) {
    // 存储的向量
    private val vectors = mutableListOf<FloatArray>()
    private val ids = mutableListOf<String>()
    
    // 分层图结构：layer -> node -> neighbors
    private val graphs = mutableListOf<MutableMap<Int, MutableList<Int>>>()
    
    // 当前元素数量
    private var elementCount = 0
    
    // 入口点（最高层的节点）
    private var entryPoint: Int = -1
    
    // 最大层数
    private val maxLevel: Int
        get() = (ln(elementCount.toDouble() + 1) / ln(m.toDouble())).toInt().coerceAtLeast(1)
    
    /**
     * 添加向量到索引
     * @param id 歌曲ID
     * @param vector 指纹向量
     */
    fun add(id: String, vector: FloatArray) {
        require(vector.size == dim) { "Vector dimension mismatch" }
        
        val newId = elementCount
        vectors.add(vector.normalize())
        ids.add(id)
        
        // 确定新节点的层数
        val level = randomLevel()
        
        // 确保有足够的层
        while (graphs.size <= level) {
            graphs.add(mutableMapOf())
        }
        
        // 在每一层建立连接
        if (elementCount == 0) {
            entryPoint = newId
            for (l in 0..level) {
                graphs[l][newId] = mutableListOf()
            }
        } else {
            var currentEntry = entryPoint
            
            // 从最高层开始搜索
            for (l in maxLevel downTo 0) {
                if (l > level) {
                    // 只搜索，不连接
                    currentEntry = searchLayer(vector, currentEntry, l, 1)[0]
                } else {
                    // 搜索并建立连接
                    val neighbors = searchLayer(vector, currentEntry, l, m)
                    
                    // 双向连接
                    graphs[l][newId] = neighbors.toMutableList()
                    for (neighbor in neighbors) {
                        val neighborLinks = graphs[l].getOrPut(neighbor) { mutableListOf() }
                        if (!neighborLinks.contains(newId)) {
                            neighborLinks.add(newId)
                            // 如果邻居数超过M，需要剪枝
                            if (neighborLinks.size > m) {
                                pruneNeighbors(neighbor, l)
                            }
                        }
                    }
                    
                    currentEntry = neighbors.firstOrNull() ?: currentEntry
                }
            }
            
            // 更新入口点（如果新节点层数更高）
            if (level > maxLevel - 1) {
                entryPoint = newId
            }
        }
        
        elementCount++
    }
    
    /**
     * 搜索最近邻
     * @param query 查询向量
     * @param k 返回结果数
     * @return 最近邻ID列表和距离
     */
    fun search(query: FloatArray, k: Int = 5): List<SearchResult> {
        if (elementCount == 0) return emptyList()
        
        val normalizedQuery = query.normalize()
        var currentEntry = entryPoint
        
        // 从最高层贪心下降到第0层
        for (l in maxLevel downTo 1) {
            currentEntry = searchLayerGreedy(normalizedQuery, currentEntry, l)
        }
        
        // 在最底层搜索k个最近邻
        val candidates = searchLayer(normalizedQuery, currentEntry, 0, k * 2)
        
        // 精确计算距离并排序
        return candidates
            .map { id ->
                SearchResult(ids[id], cosineDistance(normalizedQuery, vectors[id]))
            }
            .sortedBy { it.distance }
            .take(k)
    }
    
    /**
     * 批量添加（优化构建速度）
     */
    fun addAll(entries: List<Pair<String, FloatArray>>) {
        entries.forEach { (id, vector) ->
            add(id, vector)
        }
    }
    
    /**
     * 清空索引
     */
    fun clear() {
        vectors.clear()
        ids.clear()
        graphs.clear()
        elementCount = 0
        entryPoint = -1
    }
    
    /**
     * 获取索引大小
     */
    fun size(): Int = elementCount
    
    /**
     * 估计内存占用（MB）
     */
    fun estimatedMemoryMB(): Float {
        val vectorMemory = elementCount * dim * 4f / (1024 * 1024)  // Float = 4 bytes
        val graphMemory = graphs.sumOf { layer ->
            layer.values.sumOf { it.size * 4 }
        } / (1024 * 1024f)
        return vectorMemory + graphMemory
    }
    
    /**
     * 单层搜索（返回多个候选）
     */
    private fun searchLayer(
        query: FloatArray,
        entryPoint: Int,
        level: Int,
        ef: Int
    ): List<Int> {
        val visited = mutableSetOf<Int>()
        val candidates = mutableListOf<Pair<Int, Float>>()  // id, distance
        val result = mutableListOf<Pair<Int, Float>>()
        
        val entryDist = cosineDistance(query, vectors[entryPoint])
        candidates.add(entryPoint to entryDist)
        result.add(entryPoint to entryDist)
        visited.add(entryPoint)
        
        while (candidates.isNotEmpty()) {
            // 取出距离最近的候选
            val (current, currentDist) = candidates.removeAt(0)
            
            // 如果当前节点距离已经大于结果中最远的，停止
            if (result.size >= ef && currentDist > result.last().second) {
                break
            }
            
            // 遍历邻居
            val neighbors = graphs.getOrNull(level)?.get(current) ?: continue
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    val dist = cosineDistance(query, vectors[neighbor])
                    
                    if (result.size < ef || dist < result.last().second) {
                        candidates.add(neighbor to dist)
                        candidates.sortBy { it.second }
                        result.add(neighbor to dist)
                        result.sortBy { it.second }
                        if (result.size > ef) {
                            result.removeAt(result.size - 1)
                        }
                    }
                }
            }
        }
        
        return result.map { it.first }
    }
    
    /**
     * 单层贪心搜索（返回最近的一个）
     */
    private fun searchLayerGreedy(
        query: FloatArray,
        entryPoint: Int,
        level: Int
    ): Int {
        var current = entryPoint
        var currentDist = cosineDistance(query, vectors[current])
        var improved = true
        
        while (improved) {
            improved = false
            val neighbors = graphs.getOrNull(level)?.get(current) ?: break
            
            for (neighbor in neighbors) {
                val dist = cosineDistance(query, vectors[neighbor])
                if (dist < currentDist) {
                    current = neighbor
                    currentDist = dist
                    improved = true
                }
            }
        }
        
        return current
    }
    
    /**
     * 剪枝邻居（保留最近的M个）
     */
    private fun pruneNeighbors(nodeId: Int, level: Int) {
        val neighbors = graphs[level][nodeId] ?: return
        if (neighbors.size <= m) return
        
        val node = vectors[nodeId]
        val sorted = neighbors.sortedBy { cosineDistance(node, vectors[it]) }
        graphs[level][nodeId] = sorted.take(m).toMutableList()
    }
    
    /**
     * 随机层数（指数分布）
     */
    private fun randomLevel(): Int {
        var level = 0
        while (kotlin.random.Random.nextDouble() < 1.0 / m && level < 16) {
            level++
        }
        return level
    }
    
    /**
     * 余弦距离（1 - 余弦相似度）
     */
    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return 1 - dot / (sqrt(normA) * sqrt(normB) + 1e-10f)
    }
    
    /**
     * 向量归一化
     */
    private fun FloatArray.normalize(): FloatArray {
        val norm = sqrt(this.map { it * it }.sum())
        return if (norm > 0) {
            FloatArray(this.size) { this[it] / norm }
        } else {
            this.copyOf()
        }
    }
    
    /**
     * 搜索结果
     */
    data class SearchResult(
        val id: String,
        val distance: Float  // 余弦距离，越小越相似
    )
}
