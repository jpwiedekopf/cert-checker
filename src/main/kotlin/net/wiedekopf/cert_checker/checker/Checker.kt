package net.wiedekopf.cert_checker.checker

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import net.wiedekopf.cert_checker.model.CertificateDetails
import net.wiedekopf.cert_checker.model.CertificateDetailsTable
import net.wiedekopf.cert_checker.model.Endpoint
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.KeyStore
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private val logger = KotlinLogging.logger {}

class Checker {

    private val defaultTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())!!.apply {
        init(null as KeyStore?)
    }.trustManagers.filterIsInstance<X509TrustManager>().first()

    private fun getClient(hostname: String, onResult: (ChainCheckResult) -> Unit) = HttpClient(CIO) {
        install(Logging)
        followRedirects = false
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return defaultTrustManager.acceptedIssuers
                    }

                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        return defaultTrustManager.checkClientTrusted(chain, authType)
                    }

                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        if (chain != null) {
                            val checkResult = extractCertificateData(chain.toList(), authType, hostname)
                            onResult(checkResult)
                        }
                        return defaultTrustManager.checkServerTrusted(chain, authType)
                    }
                }
            }
        }
    }

    suspend fun checkHost(endpoint: Endpoint, onResult: (ChainCheckResult) -> Unit) {
        return checkHost(endpoint.name, endpoint.port, onResult)
    }

    private suspend fun checkHost(hostname: String, port: Int, onResult: (ChainCheckResult) -> Unit) {
        val client = getClient(hostname, onResult)
        val endpointUri = "https://$hostname:$port"
        val result = client.get(endpointUri)
        logger.info { "Got result with status code ${result.status} for $endpointUri" }
    }

    private fun extractCertificateData(chain: List<X509Certificate>, authType: String?, hostname: String): ChainCheckResult {
        logger.info { "Received certificate chain with ${chain.size} certificates, authType=$authType" }

        val allCerts = chain.map { cert ->
            val dn = cert.subjectX500Principal.name
            val isValid = try {
                cert.checkValidity()
                true
            } catch (e: CertificateNotYetValidException) {
                logger.error(e) { "Certificate is not yet valid: $dn" }
                false
            } catch (e: CertificateExpiredException) {
                logger.error(e) { "Certificate is expired: $dn" }
                false
            }
            SingleCheckResult(
                isValid = isValid,
                authType = authType,
                dn = cert.subjectX500Principal.name,
                notBefore = cert.notBefore.toInstant().toKotlinInstant(),
                notAfter = cert.notAfter.toInstant().toKotlinInstant(),
                issuerDn = cert.issuerX500Principal.name,
                serialNumber = cert.serialNumber.toString(16),
                signatureAlgorithm = cert.sigAlgName,
                san = cert.subjectAlternativeNames?.mapNotNull { it[1]?.toString() },
                certificateInfo = cert.toString()
            )
        }
        val clientCert = allCerts.firstOrNull {
            it.dn.contains(hostname) or it.san.matches(hostname)
        }
        when (clientCert) {
            null -> {
                logger.error { "No matching certificate found for $hostname" }
                return ChainCheckResult(null, allCerts)
            }

            else -> {
                logger.info {
                    buildString {
                        append("Leaf certificate was issued to ${clientCert.dn}")
                        if (!clientCert.san.isNullOrEmpty()) {
                            append(" with SANs ${clientCert.san}")
                        }
                    }
                }
                return ChainCheckResult(clientCert, allCerts - clientCert)
            }
        }
    }
}

private fun List<String>?.matches(hostname: String): Boolean {
    return this?.any {
        val regex = Regex(it.replace("*", ".*"))
        regex.matches(hostname)
    } ?: false
}

data class SingleCheckResult(
    val isValid: Boolean,
    val authType: String?,
    val dn: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val issuerDn: String,
    val serialNumber: String,
    val signatureAlgorithm: String,
    val san: List<String>?,
    val certificateInfo: String
) {
    fun writeToDb(endpoint: Endpoint): CertificateDetails {
        return transaction {
            val previous = CertificateDetails.find {
                CertificateDetailsTable.endpointId eq endpoint.id
            }
            previous.forEach { it.delete() }
            val newEntry = CertificateDetails.new {
                this.endpoint = endpoint
                this.authType = this@SingleCheckResult.authType
                this.dn = this@SingleCheckResult.dn
                this.notBefore = this@SingleCheckResult.notBefore
                this.notAfter = this@SingleCheckResult.notAfter
                this.issuer = this@SingleCheckResult.issuerDn
                this.serialNumber = this@SingleCheckResult.serialNumber
                this.signatureAlgorithm = this@SingleCheckResult.signatureAlgorithm
                this.subjectAlternativeNames = this@SingleCheckResult.san?.joinToString()
                this.certificateInfo = this@SingleCheckResult.certificateInfo
            }
            endpoint.lastCheck = Clock.System.now()
            return@transaction newEntry
        }

    }
}

data class ChainCheckResult(
    val clientCert: SingleCheckResult?,
    val chain: List<SingleCheckResult>
)