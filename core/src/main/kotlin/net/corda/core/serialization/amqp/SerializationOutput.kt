package net.corda.core.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Main entry point for serializing an object to AMQP.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
open class SerializationOutput(internal val serializerFactory: SerializerFactory = SerializerFactory()) {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableMap<Any, Int> = IdentityHashMap()
    private val serializerHistory: MutableSet<AMQPSerializer<*>> = LinkedHashSet()
    private val schemaHistory: MutableSet<TypeNotation> = LinkedHashSet()

    /**
     * Serialize the given object to AMQP, wrapped in our [Envelope] wrapper which carries an AMQP 1.0 schema, and prefixed
     * with a header to indicate that this is serialized with AMQP and not [Kryo], and what version of the Corda implementation
     * of AMQP serialization contructed the serialized form.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        try {
            val data = Data.Factory.create()
            data.withDescribed(Envelope.DESCRIPTOR_OBJECT) {
                withList {
                    // Our object
                    writeObject(obj, this)
                    // The schema
                    putObject(Schema(schemaHistory.toList()))
                }
            }
            val bytes = ByteArray(data.encodedSize().toInt() + 8)
            val buf = ByteBuffer.wrap(bytes)
            buf.put(AmqpHeaderV1_0.bytes)
            data.encode(buf)
            return SerializedBytes(bytes)
        } finally {
            objectHistory.clear()
            serializerHistory.clear()
            schemaHistory.clear()
        }
    }

    internal fun writeObject(obj: Any, data: Data) {
        writeObject(obj, data, obj.javaClass)
    }

    internal fun writeObjectOrNull(obj: Any?, data: Data, type: Type) {
        if (obj == null) {
            data.putNull()
        } else {
            writeObject(obj, data, if (type == SerializerFactory.AnyType) obj.javaClass else type)
        }
    }

    internal fun writeObject(obj: Any, data: Data, type: Type) {
        val serializer = serializerFactory.get(obj.javaClass, type)
        if (serializer !in serializerHistory) {
            serializerHistory.add(serializer)
            serializer.writeClassInfo(this)
        }
        serializer.writeObject(obj, data, type, this)
    }

    open internal fun writeTypeNotations(vararg typeNotation: TypeNotation): Boolean {
        return schemaHistory.addAll(typeNotation)
    }

    open internal fun requireSerializer(type: Type) {
        if (type != SerializerFactory.AnyType && type != Object::class.java) {
            val serializer = serializerFactory.get(null, type)
            if (serializer !in serializerHistory) {
                serializerHistory.add(serializer)
                serializer.writeClassInfo(this)
            }
        }
    }
}

