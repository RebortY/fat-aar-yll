package com.baidu.aarcollect

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import com.baidu.aarcollect.kt.aarLog
import com.baidu.aarcollect.structs.AndroidArchiveLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact
import java.util.*


class AarCollectPlugin : Plugin<Project> {

    lateinit var project: Project
    // embed配置
    lateinit var embedConf: Configuration
    // 依赖的embed的集合
    var artifacts: Set<ResolvedArtifact>? = null

    override fun apply(p0: Project?) {
        this.project = p0!!

        checkAndroidPlugin()
        createConfiguration()
        project.afterEvaluate {
            // 手机需要处理打包的依赖
            resolveArtifacts()
            // 获取 android 配置
            val appExtension: LibraryExtension = project.extensions.getByName("android") as LibraryExtension
            // 处理 android library 库
            appExtension.libraryVariants.forEach { variant ->
                processVariant(variant, appExtension)
            }
        }
    }


    private fun checkAndroidPlugin() {
        if (!project.plugins.hasPlugin("com.android.library")) {
            throw ProjectConfigurationException("fat-aar-plugin must be applied in project that has android library plugin!", null)
        }
    }

    private fun createConfiguration() {
        embedConf = project.configurations.create("embedded")
        embedConf.isVisible = false

        project.gradle.addListener(object : DependencyResolutionListener {
            override fun beforeResolve(p0: ResolvableDependencies?) {
                embedConf.dependencies.forEach { dependency ->
                    /**
                     * use provided instead of compile.
                     * advantage:
                     *   1. prune dependency node in generated pom file when upload aar library archives.
                     *   2. make invisible to the android application module, thus to avoid some duplicated processes.
                     * side effect:
                     *   1. [Fixed]incorrect R.txt in bundle. I fixed it by another way.
                     *   2. [Fixed]loss R.java that is supposed to be generated. I make it manually.
                     *   3. [Fixed]proguard.txt of embedded dependency is excluded when proguard.
                     *   4. any other...
                     */
                    project.dependencies.add("compile", dependency)
                }
                project.gradle.removeListener(this)
            }

            override fun afterResolve(p0: ResolvableDependencies?) {
                aarLog("createConfiguration")
            }
        })
    }

    private fun resolveArtifacts() {
        val set = HashSet<ResolvedArtifact>()
        embedConf.resolvedConfiguration.resolvedArtifacts
        embedConf.resolvedConfiguration.firstLevelModuleDependencies.forEach {
            it.moduleArtifacts.forEach { artifact ->
                // jar file wouldn't be here
                if ("aar" == artifact.type || "jar" == artifact.type) {
                    aarLog("resolveArtifacts", "fat-aar ---> embed : ${artifact.name} : ${artifact.type} : ${artifact
                            .id}")
                } else {
                    throw ProjectConfigurationException("Only support embed aar and jar dependencies!", null)
                }
                set.add(artifact)
            }
        }
        artifacts = Collections.unmodifiableSet(set)
    }

    private fun processVariant(variant: LibraryVariant, extension: LibraryExtension) {
        val processor = AarProcessor(project, variant, extension)
        for (artifact in artifacts!!.iterator()) {
            if ("aar" == artifact.type) {
                val archiveLibrary = AndroidArchiveLibrary(project, artifact)
                processor.addAndroidArchiveLibrary(archiveLibrary)
            }
            if ("jar" == artifact.type) {
                processor.addJarFile(artifact.file)
            }
        }
        processor.processVariant()
    }


}