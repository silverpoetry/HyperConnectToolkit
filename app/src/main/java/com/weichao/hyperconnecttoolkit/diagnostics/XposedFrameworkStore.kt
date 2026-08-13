package com.weichao.hyperconnecttoolkit.diagnostics

import io.github.libxposed.service.XposedService

data class XposedFrameworkSnapshot(
    val name: String,
    val version: String,
    val versionCode: Long,
    val apiVersion: Int,
    val properties: Long,
    val scope: List<String>,
)

/** App-side snapshot from libxposed's official service; no manager package lookup is needed. */
object XposedFrameworkStore {
    @Volatile
    private var snapshot: XposedFrameworkSnapshot? = null

    fun bind(service: XposedService) {
        snapshot = runCatching {
            XposedFrameworkSnapshot(
                name = service.frameworkName,
                version = service.frameworkVersion,
                versionCode = service.frameworkVersionCode,
                apiVersion = service.apiVersion,
                properties = service.frameworkProperties,
                scope = service.scope.sorted(),
            )
        }.getOrNull()
    }

    fun unbind() {
        snapshot = null
    }

    fun current(): XposedFrameworkSnapshot? = snapshot
}
