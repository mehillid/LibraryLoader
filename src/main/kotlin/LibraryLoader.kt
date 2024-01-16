package io.libraryloader

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResolutionException
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.slf4j.Logger
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader

class LibraryLoader(private val logger: Logger, localRepositoryDir : String) {

    companion object {
        @JvmStatic
        fun loadLocalLibraries(logger: Logger, vararg files : File) : ClassLoader {
            val jarFiles: MutableList<URL> = ArrayList()
            for (file in files) {
                var url: URL
                try {
                    url = file.toURI().toURL()
                } catch (ex: MalformedURLException) {
                    throw AssertionError(ex)
                }

                jarFiles.add(url)
                logger.info("Loaded external jar {}", file)
            }
            val loader = URLClassLoader(jarFiles.toTypedArray<URL>(), javaClass.classLoader)
            return loader
        }
    }

    private val repository : RepositorySystem
    private val session : DefaultRepositorySystemSession
    private val repositories : List<RemoteRepository>

    init {
        val locator = MavenRepositorySystemUtils.newServiceLocator().also {
            it.addService(/*type*/RepositoryConnectorFactory::class.java, /*impl*/BasicRepositoryConnectorFactory::class.java)
            it.addService(/*type*/TransporterFactory::class.java,         /*impl*/HttpTransporterFactory::class.java)
        }

        this.repository = locator.getService(RepositorySystem::class.java)
        this.session = MavenRepositorySystemUtils.newSession().also {
            it.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL)
            it.setLocalRepositoryManager(repository.newLocalRepositoryManager(it, LocalRepository(localRepositoryDir)))
            it.setTransferListener(object : AbstractTransferListener() {
                override fun transferStarted(event: TransferEvent) {
                    logger.info("Downloading {}", event.resource.repositoryUrl + event.resource.resourceName)
                }
            })
            it.setReadOnly()
        }

        this.repositories = repository.newResolutionRepositories(session, listOf(
            RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build()
        ))
    }

    fun loadRemoteLibraries(vararg libraries: String?): ClassLoader? {
        if (libraries.isEmpty()) {
            return null
        }
        logger.info("Loading {} libraries... please wait", libraries.size)

        val dependencies: MutableList<Dependency> = ArrayList()
        for (library in libraries) {
            val artifact = DefaultArtifact(library)
            dependencies.add(Dependency(artifact, /*scope*/null))
        }

        val result = try {
            repository.resolveDependencies(session,
                DependencyRequest(CollectRequest(/*root*/null as Dependency?, dependencies, repositories), /*filter*/null))
        } catch (ex: DependencyResolutionException) {
            throw RuntimeException("Error resolving libraries", ex)
        }

        val jarFiles: MutableList<URL> = ArrayList()
        for (artifact in result.artifactResults) {
            val file = artifact.artifact.file

            var url: URL
            try {
                url = file.toURI().toURL()
            } catch (ex: MalformedURLException) {
                throw AssertionError(ex)
            }

            jarFiles.add(url)
            logger.info("Loaded library {}", file)
        }

        val loader = URLClassLoader(jarFiles.toTypedArray<URL>(), javaClass.classLoader)

        return loader
    }


}