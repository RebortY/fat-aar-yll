package com.baidu.aarcollect.structs

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File


class AndroidArchiveLibrary(private val project: Project, private val artifact: ResolvedArtifact) {

    fun getArtifactFile(): File {
        return artifact.file
    }

    fun getGroup(): String {
        return artifact.moduleVersion.id.group
    }

    fun getName(): String {
        return artifact.moduleVersion.id.name
    }

    fun getVersion(): String {
        return artifact.moduleVersion.id.version
    }

    fun getRootFolder(): File {
        val explodedRootDir = project.file("${project.buildDir}/intermediates/exploded-aar/")
//        val explodedRootDir = project.file("${project.rootDir}/fat-aar/exploded-aar/")
        val id = artifact.moduleVersion.id
        return project.file("$explodedRootDir/${id.group}/${id.name}/${id.version}")
    }

    fun getJarsRootFolder(): File {
        return File(getRootFolder(), "jars")
    }

    fun getAidlFolder(): File {
        return File(getRootFolder(), "aidl")
    }

    fun getAssetsFolder(): File {
        return File(getRootFolder(), "assets")
    }

//    fun getClassesJarFile(): File {
//        return File(getJarsRootFolder(), "classes.jar")
//    }
    fun getClassesJarFile(): File {
        return File(getRootFolder(), "classes.jar")
    }

    fun getLocalJars(): Collection<File> {
        val localJars = ArrayList<File>()
        val jarList = File(getRootFolder(), "libs").listFiles()
        if (jarList != null) {
            for (jars in jarList) {
                if (jars.isFile && jars.name.endsWith(".jar")) {
                    localJars.add(jars)
                }
            }
        }

        return localJars
    }

    fun getJniFolder(): File {
        return File(getRootFolder(), "jni")
    }

    fun getResFolder(): File {
        return File(getRootFolder(), "res")
    }

    fun getManifest(): File {
        return File(getRootFolder(), "AndroidManifest.xml")
    }

    fun getLintJar(): File {
        return File(getRootFolder(), "lint.jar")
    }

    fun getProguardRules(): File {
        return File(getRootFolder(), "proguard.txt")
    }

    fun getSymbolFile(): File {
        return File(getRootFolder(), "R.txt")
    }

    init {
        if ("aar" != artifact.type) {
            throw IllegalArgumentException("artifact must be aar type!");
        }
    }


}