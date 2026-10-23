package com.trailnest.app.billing

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

const val MAX_LEASE_MS = 6 * 60 * 60 * 1000L
fun billingHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
data class Lease(val token: String, val receiptHash: String, val issuedAt: Long, val expiresAt: Long)
data class CachedLease(val token: String, val savedWall: Long, val savedElapsed: Long, val boot: Int)
interface BillingClock { fun wall(): Long; fun elapsed(): Long; fun boot(): Int }
interface LeaseStore { fun read(): CachedLease?; fun write(value: CachedLease): Boolean; fun clear() }

/** Only the server signing key can grant access. JSON booleans/SDK receipts cannot. */
class LeaseGuard(publicKeyPem: String, private val applicationId: String, private val environment: String,
    private val parentSku: String, private val termSku: String, private val clock: BillingClock) {
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(
        publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace(Regex("\\s"), ""))))
    init { require(key is java.security.interfaces.RSAPublicKey && key.modulus.bitLength() >= 2048) }
    fun verify(token: String, user: String, receipt: String? = null): Lease? = runCatching {
        require(token.length in 1..8192)
        val parts = token.split('.'); require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val verifier = Signature.getInstance("SHA256withRSA").apply { initVerify(key); update(parts[0].toByteArray(Charsets.US_ASCII)) }
        require(verifier.verify(Base64.getUrlDecoder().decode(parts[1])))
        val fields = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8).split('\n')
        require(fields.size == 9 && fields[0] == "TN1" && fields[1] == applicationId && fields[2] == environment)
        require(fields[3] == billingHash(user) && fields[4].matches(Regex("[a-f0-9]{64}")))
        require(receipt == null || fields[4] == billingHash(receipt))
        require(fields[5] == parentSku && fields[6] == termSku)
        val issued = fields[7].toLong(); val expires = fields[8].toLong(); val now = clock.wall()
        require(issued > 0 && expires > issued && expires - issued <= MAX_LEASE_MS && now >= issued && now < expires)
        Lease(token, fields[4], issued, expires)
    }.getOrNull()
    fun cached(value: CachedLease?, user: String): Lease? {
        if (value == null || value.boot != clock.boot() || clock.elapsed() < value.savedElapsed || clock.wall() < value.savedWall) return null
        val wallDelta = clock.wall() - value.savedWall; val elapsedDelta = clock.elapsed() - value.savedElapsed
        if (kotlin.math.abs(wallDelta - elapsedDelta) > 60_000) return null
        return verify(value.token, user)
    }
    fun checkpoint(lease: Lease) = CachedLease(lease.token, clock.wall(), clock.elapsed(), clock.boot())
}
