package com.baidu.aarcollect.kt

import com.baidu.aarcollect.structs.AndroidArchiveLibrary
import org.gradle.api.Project
import org.gradle.api.internal.file.copy.CopySpecWrapper
import java.io.File

fun processIntoJars(project: Project, androidLibraries: List<AndroidArchiveLibrary>,
                           jarFiles: List<File>, folderOut: File) {
    for (androidLibrary in androidLibraries) {
        if (!androidLibrary.getRootFolder().exists()) {
            aarLog("warning", "${androidLibrary.getRootFolder()} not found !!!!")
            continue
        }

        val prefix = "${androidLibrary.getName()}-${androidLibrary.getVersion()}"

        // copy class jar
        project.copy { copySpec ->
            val copyWrapper = CopySpecWrapper(copySpec)
            copyWrapper.from(androidLibrary.getClassesJarFile())
            copyWrapper.into(folderOut)
            copyWrapper.rename {
                "$prefix.jar"
            }
            aarLog("!!!!!!!!!!  copy    ${androidLibrary.getClassesJarFile()} && ${androidLibrary.getClassesJarFile
            ().exists()}")
        }

        // copy lib jar
        project.copy { copySpec ->
            val copyWrapper = CopySpecWrapper(copySpec)
            copyWrapper.from(androidLibrary.getLocalJars())
            copyWrapper.into(folderOut)
            copyWrapper.rename {
                "$prefix-$it"
            }
            aarLog("!!!!!!!!!!  copy    ${androidLibrary.getLocalJars()} && ${androidLibrary.getLocalJars().size}")
        }
    }
    for (jarFile in jarFiles) {
        if (!jarFile.exists()) {
            aarLog("warning", "$jarFile not found !!!!")
            continue
        }

        aarInfoLog("processIntoClasses   ======= ${jarFile.path}  copy to ${folderOut.path} ")

        project.copy { copySpec ->
            val copyWrapper = CopySpecWrapper(copySpec)
            copyWrapper.from(jarFile)
            copyWrapper.into(folderOut)
        }
    }
}


fun processIntoClasses(project: Project, androidLibraries: List<AndroidArchiveLibrary>, jarFiles: List<File>, folderOut: File)
{
    val allJarFiles:  ArrayList<File> = ArrayList()
    for (androidLibrary in androidLibraries) {
        if (!androidLibrary.getRootFolder().exists()) {
            aarWarningLog("${androidLibrary.getRootFolder()}  not found!!!!!!!")
            continue
        }
        allJarFiles.add(androidLibrary.getClassesJarFile())
        allJarFiles.addAll(androidLibrary.getLocalJars())
    }
    for (jarFile in jarFiles) {
        if (!jarFile.exists()) {
            aarWarningLog("$jarFile not found !!!!!")
            continue
        }
        allJarFiles.add(jarFile)
    }
    for (jarFile in allJarFiles) {

        aarInfoLog("processIntoClasses   ======= ${jarFile.path}  copy to ${folderOut.path} ")

        project.copy { copySpec ->
            val copyWrapper = CopySpecWrapper(copySpec)
            copyWrapper.from(project.zipTree(jarFile))
            copyWrapper.into(folderOut)
            copyWrapper.include("**/*.class")
            copyWrapper.exclude("META-INF/")
        }
    }
}

/**
 * 删除目录
 */
fun delFolder(file: File) : Boolean {

    if (file.exists()) {
        aarWarningLog("${file.path} not exists !!! ")
        return false
    }
    if (file.isDirectory) {
        file.listFiles().forEach {
            if (it.isDirectory) {
                delFolder(it)
                it.delete()
            } else {
                it.delete()
            }
        }

    } else {
        file.delete()
    }
    return true
}
