import io.libraryloader.LibraryLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.jvm.Throws
import kotlin.math.log
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.asserter

class LibraryLoaderTests {

    lateinit var logger: Logger
    @BeforeTest
    fun before() {
        logger = LoggerFactory.getLogger("EasymlApp")
    }

    @Test
    fun testRemote() {
        val libraryLoader = LibraryLoader(logger, "libraries")
        val classloader = libraryLoader.loadRemoteLibraries("mysql:mysql-connector-java:8.0.33")

        val clazz = classloader?.loadClass("com.mysql.jdbc.Driver")?.name
        asserter.assertNotNull(clazz, "Mysql driver not found")
    }

    @Test
    fun testLocal() {
        val classloader = LibraryLoader.loadLocalLibraries(logger, (File("C:\\Users\\mehillid\\Documents\\gson-2.2.2.jar")))
        val clazz = classloader.loadClass("com.google.gson.Gson")?.name
        asserter.assertNotNull(clazz, "Gson library not found")

    }
}