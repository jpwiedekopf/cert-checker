package net.wiedekopf.cert_checker.model

import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EndpointTable : IntIdTable() {
    val name = varchar("name", 255)
    val port = integer("port")
    val lastCheck = timestamp("last_check").nullable()
}

object CertificateDetailsTable : IntIdTable() {
    val endpointId = reference("endpoint_id", EndpointTable)
    val authType = text("auth_type").nullable()
    val dn = text("dn")
    val notBefore = timestamp("not_before")
    val notAfter = timestamp("not_after")
    val issuer = text("issuer")
    val serialNumber = text("serial_number")
    val signatureAlgorithm = text("signature_algorithm")
    val subjectAlternativeNames = text("subject_alternative_names").nullable()
    val certificateInfo = text("certificate_info")
}

class Endpoint(
    id: EntityID<Int>
) : IntEntity(id) {
    companion object : IntEntityClass<Endpoint>(EndpointTable)

    var name: String by EndpointTable.name
    var port: Int by EndpointTable.port
    var lastCheck: Instant? by EndpointTable.lastCheck

    val details by CertificateDetails referrersOn CertificateDetailsTable.endpointId
}

class CertificateDetails(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CertificateDetails>(CertificateDetailsTable)

    var endpoint: Endpoint by Endpoint referencedOn CertificateDetailsTable.endpointId
    var authType: String? by CertificateDetailsTable.authType
    var notBefore: Instant by CertificateDetailsTable.notBefore
    var notAfter: Instant by CertificateDetailsTable.notAfter
    var dn: String by CertificateDetailsTable.dn
    var issuer: String by CertificateDetailsTable.issuer
    var serialNumber: String by CertificateDetailsTable.serialNumber
    var signatureAlgorithm: String by CertificateDetailsTable.signatureAlgorithm
    var subjectAlternativeNames: String? by CertificateDetailsTable.subjectAlternativeNames
    var certificateInfo: String by CertificateDetailsTable.certificateInfo
}