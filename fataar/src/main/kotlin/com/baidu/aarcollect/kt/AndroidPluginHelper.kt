package com.baidu.aarcollect.kt

import com.android.build.gradle.api.LibraryVariant
import com.google.common.base.Strings
import org.gradle.api.Project
import org.gradle.api.Task
import org.joor.Reflect
import org.gradle.util.VersionNumber
import java.io.File


/**
 * Resolve from com.android.builder.Version#ANDROID_GRADLE_PLUGIN_VERSION
 *
 * Throw exception if can not found
 */
fun getAndroidPluginVersion(): String {
    return Reflect.on("com.android.builder.Version").get("ANDROID_GRADLE_PLUGIN_VERSION")
}

/**
 * return bundle dir of specific variant
 */
fun resolveBundleDir(project: Project, variant: Any): File? {
    return if (VersionNumber.parse(getAndroidPluginVersion()) < VersionNumber.parse("2.3.0")) {
        val dirName = Reflect.on(variant).call("getDirName").get<String>()
        if (Strings.isNullOrEmpty(dirName)) {
            null
        } else project.file("${project.buildDir}/intermediates/bundles/$dirName")
    } else {
        // do the trick getting assets task output
        val mergeAssetsTask = Reflect.on(variant).call("getMergeAssets").get<Task>()
        val assetsDir: File = Reflect.on(mergeAssetsTask).call("getOutputDir").get<File>()
        assetsDir.parentFile
    }
}
