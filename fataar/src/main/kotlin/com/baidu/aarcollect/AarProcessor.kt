package com.baidu.aarcollect

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.tasks.InvokeManifestMerger
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.baidu.aarcollect.kt.aarInfoLog
import com.baidu.aarcollect.kt.processIntoClasses
import com.baidu.aarcollect.kt.processIntoJars
import com.baidu.aarcollect.kt.resolveBundleDir
import com.baidu.aarcollect.structs.AndroidArchiveLibrary
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.file.copy.CopySpecWrapper
import java.io.File

class AarProcessor(private val project: Project, private val variant: LibraryVariant, extension: LibraryExtension) {

    private val libExtension: LibraryExtension = extension
    private val mAndroidArchiveLibraries: ArrayList<AndroidArchiveLibrary> = ArrayList()

    private val mJarFiles: ArrayList<File> = ArrayList()

    fun addAndroidArchiveLibrary(library: AndroidArchiveLibrary) {
        aarInfoLog("aar === library ${library.getName()}")
        mAndroidArchiveLibraries.add(library)
    }

    fun addJarFile(jar: File) {
        aarInfoLog("aar ${jar.name}")
        mJarFiles.add(jar)
    }

    fun processVariant() {

        val taskPath = "prepare${variant.name.capitalize()}Dependencies"
        val prepareTask: Task = project.tasks.findByPath(taskPath) ?: throw  RuntimeException("Can not find task $taskPath!")
        processClassesAndJars()
        if (mAndroidArchiveLibraries.isEmpty()) {
            return
        }

        processAar()
        processManifest()
        processResourcesAndR()
        processRSources()
        processAssets()
        processJniLibs()
        processProguardTxt(prepareTask)
    }

    /**
     * 在clean项目后copy aar 包
     */
    private fun processAar() {

        val prepareDependencies = "prepare${variant.name.capitalize()}Dependencies"

        val preBuildTask = project.tasks.findByPath(prepareDependencies) ?: throw RuntimeException("no find " +
                "prepareDependencies")
        preBuildTask.doFirst {
            mAndroidArchiveLibraries.forEach { library ->
                val file: File = library.getArtifactFile()
                aarInfoLog("aar ${file.path}   ${file.isFile}  exist = ${file.exists()}")
                if (file.isFile) {
                    project.copy { copySpace ->
                        // 先清除在copy
                        val f = File("${library.getRootFolder()}")
                        val copyWrapper = CopySpecWrapper(copySpace)
                        copyWrapper.from(project.zipTree(file))
                        copyWrapper.into(f)
                    }
                    aarInfoLog("aar ${file.path} ======  copy  aar ")
                }
            }
        }

    }

    /**
     * merge manifest
     *
     * TODO process each variant.getOutputs()
     * TODO "InvokeManifestMerger" deserve more android plugin version check
     * TODO add setMergeReportFile
     * TODO a better temp manifest file location
     */
    private fun processManifest() {

        val processManifestTask = variant.outputs[0].processManifest
        val manifestOutput = project.file("${project.buildDir.path}/intermediates/fat-aar/${variant
                .dirName}/AndroidManifest.xml")

        val manifestOutputBackup = processManifestTask.manifestOutputFile
        processManifestTask.manifestOutputFile = manifestOutput

        val manifestsMergeTask = project.tasks.create("merge${variant.name.capitalize()}Manifest",
                InvokeManifestMerger::class.java)

        manifestsMergeTask.variantName = variant.name
        manifestsMergeTask.mainManifestFile = manifestOutput

        var list: ArrayList<File> = ArrayList()
        mAndroidArchiveLibraries.forEach {
            if (it.getManifest().exists()) {
                list.add(it.getManifest())
            }
        }
        manifestsMergeTask.secondaryManifestFiles = list
        manifestsMergeTask.outputFile = manifestOutputBackup
        manifestsMergeTask.dependsOn(processManifestTask)
        processManifestTask.finalizedBy(manifestsMergeTask)
    }

    private fun processClassesAndJars() {

        aarInfoLog("!!!!!!!!!!!!!!!!! processClassesAndJars !!!!!")

        if (variant.buildType.isMinifyEnabled) {
            // 添加混淆内容
            mAndroidArchiveLibraries
                    .map { it.getProguardRules() }
                    .filter { it.exists() }
                    .forEach { libExtension.defaultConfig.proguardFile(it) }
            // 处理编译jar
            val javacTask: Task = variant.javaCompiler ?: return
            javacTask.doLast {
                aarInfoLog("=========== processClassesAndJars     javacTask ")
                val dustDir = project.file("${project.buildDir.path}/intermediates/classes/${variant.dirName}")
                processIntoClasses(project, mAndroidArchiveLibraries, mJarFiles, dustDir)
            }
        } else {

            // 系统处理jar的task
            val taskJarsFor = "transformClassesAndResourcesWithSyncLibJarsFor${variant.name.capitalize()}"
            val syncLibTask: Task = project.tasks.findByPath(taskJarsFor) ?: throw RuntimeException("Can not find task $taskJarsFor!")

            // 自定义为了 自动生成ApiManager代码的transform
            val taskPath = "transformClassesAndResourcesWithInsertComCodeFor${variant.name.capitalize()}"
            var comCodeTask: Task? = project.tasks.findByPath(taskPath)

            aarInfoLog("=========== processClassesAndJars     transformClassesAndResourcesWithSyncLibJars ")

            // 检查如果没有自定的transform则执行系统的处理
            if (comCodeTask == null) {
                syncLibTask.doLast { handleJars() }
            } else {
                comCodeTask.doFirst { handleJars() }
            }
        }
    }

    private fun handleJars() {
        val dustDir = project.file("${resolveBundleDir(project, variant)!!.path}/libs")
        aarInfoLog("!!!!!!!!!!!!!!!!! dustDir  $dustDir !!!!!")
        processIntoJars(project, mAndroidArchiveLibraries, mJarFiles, dustDir)
    }


    /**
     * merge R.txt(actually is to fix issue caused by provided configuration) and res
     *
     * Here I have to inject res into "main" instead of "variant.name".
     * To avoid the res from embed dependencies being used, once they have the same res Id with main res.
     *
     * Now the same res Id will cause a build exception: Duplicate resources, to encourage you to change res Id.
     * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
     */
    private fun processResourcesAndR() {
        val taskPath = "generate${variant.name.capitalize()}Resources"
        val resourceGenTask = project.tasks.findByPath(taskPath) ?: throw RuntimeException("Can not find task $taskPath!")
        resourceGenTask.doFirst {
            aarInfoLog("processResourcesAndR")
            for (archiveLibrary in mAndroidArchiveLibraries) {
                aarInfoLog(" find resFolder ${archiveLibrary.getResFolder()}")
                getAndroidSourceSet().res.srcDir(archiveLibrary.getResFolder())
            }
        }
    }

    /**
     * generate R.java
     */
    private fun processRSources() {
        val processResourcesTask: ProcessAndroidResources = variant.outputs[0].processResources
        processResourcesTask.doLast {
            aarInfoLog("generate  R sources  ${processResourcesTask.sourceOutputDir}")
            for (archiveLibrary in mAndroidArchiveLibraries) {
                RSourceGenerator.generate(processResourcesTask.sourceOutputDir, archiveLibrary)
            }
        }
    }

    /**
     * merge assets
     *
     * AaptOptions.setIgnoreAssets and AaptOptions.setIgnoreAssetsPattern will work as normal
     */
    private fun processAssets() {
        val assetsTask: Task = variant.mergeAssets ?: throw RuntimeException("Can not find task in variant.getMergeAssets()!")

        for (archiveLibrary in mAndroidArchiveLibraries) {
            assetsTask.inputs.dir(archiveLibrary.getAssetsFolder())
        }
        assetsTask.doFirst {
            aarInfoLog("processAssets")
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                aarInfoLog("processAssets  ${archiveLibrary.getAssetsFolder()}")
                getAndroidSourceSet().assets.srcDir(archiveLibrary.getAssetsFolder())
            }
        }
    }

    /**
     * merge jniLibs
     */
    private fun processJniLibs() {
        val taskPath = "merge${variant.name.capitalize()}JniLibFolders"
        val mergeJniLibsTask = project.tasks.findByPath(taskPath) ?: throw RuntimeException("Can not find task $taskPath")

        for (archiveLibrary in mAndroidArchiveLibraries) {
            mergeJniLibsTask.inputs.dir(archiveLibrary.getJniFolder())
        }
        mergeJniLibsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                getAndroidSourceSet().jniLibs.srcDir(archiveLibrary.getJniFolder())
            }
        }
    }

    /**
     * merge proguard.txt
     */
    private fun processProguardTxt(preparestask: Task) {

        val taskPath = "merge${variant.name.capitalize()}ProguardFiles"
        val mergeFileTask: MergeFileTask = project.tasks.findByPath(taskPath) as MergeFileTask?
                ?: throw RuntimeException("Can not find task $taskPath!")

        mAndroidArchiveLibraries
                .map { it.getProguardRules() }
                .filter { it.exists() }
                .forEach { mergeFileTask.inputs.file(it) }

        // 添加混淆merge文件
        mergeFileTask.doFirst {
            val proguardFiles = mergeFileTask.inputFiles
            mAndroidArchiveLibraries.map {
                it.getProguardRules()
            }.filter {
                it.exists()
            }.forEach {
                proguardFiles.add(it)
            }
        }
        mergeFileTask.dependsOn(preparestask)
    }


    private fun getAndroidSourceSet(): AndroidSourceSet {
        return libExtension.sourceSets.getByName("main")
    }

}