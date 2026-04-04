package com.nekoplayer.data.repository

import com.nekoplayer.data.api.BiliCookies
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * 用户数据存储仓库
 * 使用Multiplatform Settings保存登录状态
 */
class UserRepository(
    private val settings: Settings
) {
    companion object {
        private const val KEY_SESSDATA = "bili_sessdata"
        private const val KEY_BILI_JCT = "bili_jct"
        private const val KEY_DEDE_USER_ID = "bili_dede_user_id"
        private const val KEY_DEDE_USER_ID_CK_MD5 = "bili_dede_user_id_ck_md5"
        private const val KEY_SID = "bili_sid"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    /**
     * 保存B站Cookie
     */
    fun saveBiliCookies(cookies: BiliCookies) {
        settings[KEY_SESSDATA] = cookies.sessdata
        settings[KEY_BILI_JCT] = cookies.biliJct
        settings[KEY_DEDE_USER_ID] = cookies.dedeUserId
        settings[KEY_DEDE_USER_ID_CK_MD5] = cookies.dedeUserIdCkMd5
        settings[KEY_SID] = cookies.sid
        settings[KEY_IS_LOGGED_IN] = true
    }
    
    /**
     * 获取保存的B站Cookie
     */
    fun getBiliCookies(): BiliCookies? {
        val sessdata: String = settings[KEY_SESSDATA] ?: return null
        if (sessdata.isEmpty()) return null
        
        return BiliCookies(
            sessdata = sessdata,
            biliJct = settings[KEY_BILI_JCT] ?: "",
            dedeUserId = settings[KEY_DEDE_USER_ID] ?: "",
            dedeUserIdCkMd5 = settings[KEY_DEDE_USER_ID_CK_MD5] ?: "",
            sid = settings[KEY_SID] ?: ""
        )
    }
    
    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return settings[KEY_IS_LOGGED_IN] ?: false
    }
    
    /**
     * 清除登录状态
     */
    fun clearLogin() {
        settings.remove(KEY_SESSDATA)
        settings.remove(KEY_BILI_JCT)
        settings.remove(KEY_DEDE_USER_ID)
        settings.remove(KEY_DEDE_USER_ID_CK_MD5)
        settings.remove(KEY_SID)
        settings[KEY_IS_LOGGED_IN] = false
    }
}
