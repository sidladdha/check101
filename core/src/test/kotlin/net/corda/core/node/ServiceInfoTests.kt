package net.corda.core.node

import net.corda.core.crypto.X509Utilities
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.testing.getTestX509Name
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceInfoTests {
    val serviceType = ServiceType.getServiceType("test", "service").getSubType("subservice")
    val name = getTestX509Name("service.name")

    @Test
    fun `type and name encodes correctly`() {
        assertEquals(ServiceInfo(serviceType, name).toString(), "$serviceType|$name")
    }

    @Test
    fun `type and name parses correctly`() {
        assertEquals(ServiceInfo.parse("$serviceType|$name"), ServiceInfo(serviceType, name))
    }

    @Test
    fun `type only encodes correctly`() {
        assertEquals(ServiceInfo(serviceType).toString(), "$serviceType")
    }

    @Test
    fun `type only parses correctly`() {
        assertEquals(ServiceInfo.parse("$serviceType"), ServiceInfo(serviceType))
    }

    @Test
    fun `invalid encoding throws`() {
        assertFailsWith<IllegalArgumentException> { ServiceInfo.parse("$serviceType|$name|something") }
    }
}
