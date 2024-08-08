import Build_gradle.RepositoryConfiguration.Companion.CNAME
import Build_gradle.RepositoryConfiguration.Companion.ORIGIN
import Build_gradle.RepositoryConfiguration.Companion.REMOTE
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Git.init
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems.getDefault

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        val jacksonVersion = "2.15.2"
        val jgitVersion = "6.8.0.202311291450-r"
        val deps = listOf(
            "org.jbake:jbake-gradle-plugin:5.5.0",
            "org.slf4j:slf4j-simple:2.0.7",
            "commons-io:commons-io:2.13.0",
            "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion",
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion",
            "org.eclipse.jgit:org.eclipse.jgit:$jgitVersion",
            "org.eclipse.jgit:org.eclipse.jgit.archive:$jgitVersion",
            "org.eclipse.jgit:org.eclipse.jgit.ssh.jsch:$jgitVersion",
            "org.tukaani:xz:1.9"
        )
        deps.map { classpath(it) }
    }
}

plugins { id("org.jbake.site") }

//TODO: add readme-site-repository
tasks.register("publishSite") {
    group = "managed"
    description = "Publish site online."
    dependsOn("bake")
    doFirst { createCnameFile() }
    jbake {
        srcDirName = bakeSrcPath
        destDirName = bakeDestDirPath
    }
    doLast {
        pushPages(
            destPath = { "${layout.buildDirectory.get().asFile.absolutePath}${getDefault().separator}$bakeDestDirPath" },
            pathTo = { "${layout.buildDirectory.get().asFile.absolutePath}${getDefault().separator}${localConf.pushPage.to}" }
        )
    }
}

data class GitPushConfiguration(
    val from: String,
    val to: String,
    val repo: RepositoryConfiguration,
    val branch: String,
    val message: String,
)

data class RepositoryConfiguration(
    val name: String,
    val repository: String,
    val credentials: RepositoryCredentials,
) {
    companion object {
        const val ORIGIN = "origin"
        const val CNAME = "CNAME"
        const val REMOTE = "remote"
    }
}

data class RepositoryCredentials(val username: String, val password: String)

data class SiteConfiguration(
    val bake: BakeConfiguration,
    val pushPage: GitPushConfiguration,
    val pushSource: GitPushConfiguration? = null,
    val pushTemplate: GitPushConfiguration? = null,
)

data class BakeConfiguration(
    val srcPath: String,
    val destDirPath: String,
    val cname: String?,
)

sealed class FileOperationResult {
    sealed class GitOperationResult {
        data class Success(
            val commit: RevCommit, val pushResults: MutableIterable<PushResult>?
        ) : GitOperationResult()

        data class Failure(val error: String) : GitOperationResult()
    }

    object Success : FileOperationResult()
    data class Failure(val error: String) : FileOperationResult()
}


/*=================================================================================*/

val mapper: ObjectMapper
    get() = YAMLFactory()
        .let(::ObjectMapper)
        .disable(WRITE_DATES_AS_TIMESTAMPS)
        .registerKotlinModule()


/*=================================================================================*/


val localConf: SiteConfiguration
    get() = readSiteConfigurationFile {
        "$rootDir${getDefault().separator}${properties["managed_config_path"]}"
    }


fun readSiteConfigurationFile(
    configPath: () -> String
): SiteConfiguration = try {
    configPath().let(::File).let(mapper::readValue)
} catch (e: Exception) {
// Handle exception or log error
    SiteConfiguration(
        BakeConfiguration("", "", null),
        GitPushConfiguration(
            "", "", RepositoryConfiguration(
                "",
                "",
                RepositoryCredentials("", "")
            ), "", ""
        )
    )
}

/*=================================================================================*/


val bakeSrcPath: String get() = localConf.bake.srcPath


val bakeDestDirPath: String get() = localConf.bake.destDirPath


/*=================================================================================*/



fun createCnameFile() {
    when {
        localConf.bake.cname != null && localConf.bake.cname!!.isNotBlank() -> file(
            "${project.layout.buildDirectory.get().asFile.absolutePath}${
                getDefault().separator
            }${
                localConf.bake.destDirPath
            }${getDefault().separator}$CNAME"
        ).run {
            when {
                exists() && isDirectory -> deleteRecursively()
                exists() -> delete()
            }
//TODO: virer les assert et passer a des conditions
            assert(!exists())
            assert(createNewFile())
            appendText(localConf.bake.cname ?: "", UTF_8)
            assert(exists() && !isDirectory)
        }
    }
}

/*=================================================================================*/


fun createRepoDir(path: String): File = path
    .let(::File)
    .apply {
        if (exists() && !isDirectory) assert(delete())
        if (exists()) assert(deleteRecursively())
        assert(!exists())
        if (!exists()) assert(mkdir())
    }

/*=================================================================================*/


fun copyBakedFilesToRepo(
    bakeDirPath: String, repoDir: File
): FileOperationResult = try {
    bakeDirPath
//        .also { "bakeDirPath : $it".let(::println) }
        .let(::File)
        .apply {
            copyRecursively(repoDir, true)
            deleteRecursively()
        }
//        .let { "Est-ce que $it existe après copy & delete? ${it.exists()}" }
//        .let(::println)
    FileOperationResult.Success
} catch (e: Exception) {
    FileOperationResult.Failure(e.message ?: "An error occurred during file copy.")
}

/*=================================================================================*/


fun initAddCommit(
    repoDir: File,
    conf: SiteConfiguration,
): RevCommit {
//3) initialiser un repo dans le dossier cvs
    init().setDirectory(repoDir).call().run {
        assert(!repository.isBare)
        assert(repository.directory.isDirectory)
// add remote repo:
        remoteAdd().apply {
            setName(ORIGIN)
            setUri(URIish(conf.pushPage.repo.repository))
// you can add more settings here if needed
        }.call()
//4) ajouter les fichiers du dossier cvs à l'index
        add().addFilepattern(".").call()

//5) commit
        return commit().setMessage(conf.pushPage.message).call()
    }
}

/*=================================================================================*/


fun push(repoDir: File, conf: SiteConfiguration): MutableIterable<PushResult>? =
    FileRepositoryBuilder().setGitDir(
        "${repoDir.absolutePath}${getDefault().separator}.git".let(::File)
    ).readEnvironment()
        .findGitDir()
        .setMustExist(true)
        .build()
        .also {
            it.config.apply {
                getString(
                    REMOTE, ORIGIN, conf.pushPage.repo.repository
                )
            }.save()
            assert(it.isBare)
        }.let(::Git).run {
// push to remote:
            push().apply {
                setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(
                        conf.pushPage.repo.credentials.username, conf.pushPage.repo.credentials.password
                    )
                )
//you can add more settings here if needed
                remote = ORIGIN
                isForce = true
            }.call()
        }

/*=================================================================================*/


fun pushPages(
    destPath: () -> String,
    pathTo: () -> String
) = pathTo()
    .run(::createRepoDir)
    .let { it: File ->
        copyBakedFilesToRepo(destPath(), it)
            .takeIf { it is FileOperationResult.Success }
            ?.run {
                initAddCommit(it, localConf)
                push(it, localConf)
                it.deleteRecursively()
                destPath().let(::File).deleteRecursively()
            }
    }

/*=================================================================================*/
