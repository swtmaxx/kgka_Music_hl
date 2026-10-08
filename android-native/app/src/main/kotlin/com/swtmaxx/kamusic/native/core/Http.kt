package com.swtmaxx.kamusic.native.core

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局 OkHttp 客户端。
 *
 * 超时与 Flutter 版一致（单请求 20s），并额外设置 callTimeout 兜底，
 * 避免弱网下手表长时间卡在请求上。
 */
fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
