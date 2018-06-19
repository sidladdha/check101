package net.corda.core.serialization

import sun.misc.Unsafe
import sun.security.util.Password
import java.io.*
import java.lang.invoke.*
import java.lang.reflect.*
import java.net.*
import java.security.*
import java.sql.Connection
import java.util.*
import java.util.logging.Handler
import java.util.zip.ZipFile
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * This is a [ClassWhitelist] implementation where everything is whitelisted except for blacklisted classes and interfaces.
 * In practice, as flows are arbitrary code in which it is convenient to do many things,
 * we can often end up pulling in a lot of objects that do not make sense to put in a checkpoint.
 * Thus, by blacklisting classes/interfaces we don't expect to be serialised, we can better handle/monitor the aforementioned behaviour.
 * Inheritance works for blacklisted items, but one can specifically exclude classes from blacklisting as well.
 */
object AllButBlacklisted : ClassWhitelist {

    private val blacklistedClasses = hashSetOf<String>(

            // Known blacklisted classes.
            Thread::class.java.name,
            HashSet::class.java.name,
            HashMap::class.java.name,
            ClassLoader::class.java.name,
            Handler::class.java.name, // MemoryHandler, StreamHandler
            Runtime::class.java.name,
            Unsafe::class.java.name,
            ZipFile::class.java.name,
            Provider::class.java.name,
            SecurityManager::class.java.name,
            Random::class.java.name,

            // Known blacklisted interfaces.
            Connection::class.java.name,
            // TODO: AutoCloseable::class.java.name,

            // java.security.
            KeyStore::class.java.name,
            Password::class.java.name,
            AccessController::class.java.name,
            Permission::class.java.name,

            // java.net.
            DatagramSocket::class.java.name,
            ServerSocket::class.java.name,
            Socket::class.java.name,
            URLConnection::class.java.name,
            // TODO: add more from java.net.

            // java.io.
            Console::class.java.name,
            File::class.java.name,
            FileDescriptor::class.java.name,
            FilePermission::class.java.name,
            RandomAccessFile::class.java.name,
            Reader::class.java.name,
            Writer::class.java.name,
            // TODO: add more from java.io.

            // java.lang.invoke classes.
            CallSite::class.java.name, // for all CallSites eg MutableCallSite, VolatileCallSite etc.
            LambdaMetafactory::class.java.name,
            MethodHandle::class.java.name,
            MethodHandleProxies::class.java.name,
            MethodHandles::class.java.name,
            MethodHandles.Lookup::class.java.name,
            MethodType::class.java.name,
            SerializedLambda::class.java.name,
            SwitchPoint::class.java.name,

            // java.lang.invoke interfaces.
            MethodHandleInfo::class.java.name,

            // java.lang.invoke exceptions.
            LambdaConversionException::class.java.name,
            WrongMethodTypeException::class.java.name,

            // java.lang.reflect.
            AccessibleObject::class.java.name, // For Executable, Field, Method, Constructor.
            Modifier::class.java.name,
            Parameter::class.java.name,
            ReflectPermission::class.java.name
            // TODO: add more from java.lang.reflect.
    )

    // Specifically exclude classes from the blacklist,
    // even if any of their superclasses and/or implemented interfaces are blacklisted.
    private val forciblyAllowedClasses = hashSetOf<String>(
            LinkedHashSet::class.java.name,
            LinkedHashMap::class.java.name,
            InputStream::class.java.name,
            BufferedInputStream::class.java.name,
            Class.forName("sun.net.www.protocol.jar.JarURLConnection\$JarURLInputStream").name
    )

    /**
     * This implementation supports inheritance; thus, if a superclass or superinterface is blacklisted, so is the input class.
     */
    override fun hasListed(type: Class<*>): Boolean {
        // Check if excluded.
        if (type.name !in forciblyAllowedClasses) {
            // Check if listed.
            if (type.name in blacklistedClasses)
                throw IllegalStateException("Class ${type.name} is blacklisted, so it cannot be used in serialization.")
            // Inheritance check.
            else {
                val aMatch = blacklistedClasses.firstOrNull { Class.forName(it).isAssignableFrom(type) }
                if (aMatch != null) {
                    // TODO: blacklistedClasses += type.name // add it, so checking is faster next time we encounter this class.
                    val matchType = if (Class.forName(aMatch).isInterface) "superinterface" else "superclass"
                    throw IllegalStateException("The $matchType $aMatch of ${type.name} is blacklisted, so it cannot be used in serialization.")
                }
            }
        }
        return true
    }
}
