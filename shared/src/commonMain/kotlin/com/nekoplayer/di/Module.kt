package com.nekoplayer.di

import org.koin.core.module.Module

/**
 * 平台特定模块（expect声明）
 */
expect fun platformModule(): Module
