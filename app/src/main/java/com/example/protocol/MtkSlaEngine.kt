package com.example.protocol

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

data class MtkSlaKey(
    val vendor: String,
    val daCodes: List<Int>,
    val name: String,
    val d: BigInteger,
    val n: BigInteger,
    val e: BigInteger = BigInteger.valueOf(65537)
)

/**
 * MediaTek Serial Link Authentication (SLA) & Download Agent Authorization (DAA) Keyrings
 * Ported from mtkclient config/sla_keys.py, Library/Auth/sla.py, and Library/DA/xflash/xflash_lib.py
 */
object MtkSlaKeyDatabase {

    val DA_SLA_KEYS: List<MtkSlaKey> = MtkSlaKeysData.DA_SLA_KEYS
    val BROM_SLA_KEYS: List<MtkSlaKey> = MtkSlaKeysData.BROM_SLA_KEYS

    /**
     * Converts RSA modulus BigInteger to big-endian unsigned byte array (Python Crypto.Util.number.long_to_bytes).
     */
    fun modulusBytes(key: MtkSlaKey): ByteArray {
        val raw = key.n.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) {
            raw.copyOfRange(1, raw.size)
        } else {
            raw
        }
    }

    /**
     * Searches a binary buffer (DA2 or BROM) for an embedded SLA RSA modulus.
     */
    fun findMatchingKey(binaryData: ByteArray, preferDaKeys: Boolean = true): MtkSlaKey? {
        val ordered = if (preferDaKeys) DA_SLA_KEYS + BROM_SLA_KEYS else BROM_SLA_KEYS + DA_SLA_KEYS
        for (key in ordered) {
            val mod = modulusBytes(key)
            if (MtkDaPatcher.findBinary(binaryData, mod) != null) {
                return key
            }
        }
        return null
    }

    /**
     * Generates DA SLA signature via RSA/ECB/OAEPPadding with SHA-256 and MGF1(SHA-256).
     * Exact equivalent of Python mtkclient:
     * PKCS1_OAEP.new(rsa_key, SHA256, mgfunc=lambda x, y: PKCS1_OAEP.MGF1(x, y, SHA256)).encrypt(data)
     */
    fun generateDaSlaSignature(data: ByteArray, key: MtkSlaKey): ByteArray {
        val pubSpec = RSAPublicKeySpec(key.n, key.e)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(pubSpec)
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepSpec)
        return cipher.doFinal(data)
    }

    /**
     * Customized RSA PKCS1-style challenge signature computation for BROM SLA.
     */
    private fun customizedSign(d: BigInteger, n: BigInteger, msg: ByteArray): ByteArray {
        val modBits = n.bitLength()
        val k = (modBits + 7) / 8
        val padSize = k - msg.size - 3

        val em = ByteArray(k)
        em[0] = 0x00
        em[1] = 0x01
        for (i in 2 until 2 + padSize) {
            em[i] = 0xFF.toByte()
        }
        em[2 + padSize] = 0x00
        System.arraycopy(msg, 0, em, 3 + padSize, msg.size)

        val emInt = BigInteger(1, em)
        val mInt = emInt.modPow(d, n)
        val rawSign = mInt.toByteArray()

        val signature = ByteArray(k)
        if (rawSign.size >= k) {
            System.arraycopy(rawSign, rawSign.size - k, signature, 0, k)
        } else {
            System.arraycopy(rawSign, 0, signature, k - rawSign.size, rawSign.size)
        }
        return signature
    }

    /**
     * Generates MediaTek BROM SLA Challenge Response (pairwise byte-swap, customized RSA sign, pairwise swap)
     */
    fun generateBromSlaChallenge(challenge: ByteArray, key: MtkSlaKey): ByteArray {
        val data = challenge.copyOf()
        for (i in 0 until data.size - 1 step 2) {
            val tmp = data[i]
            data[i] = data[i + 1]
            data[i + 1] = tmp
        }

        val signed = customizedSign(key.d, key.n, data)
        val msg = signed.copyOf()
        for (i in 0 until msg.size - 1 step 2) {
            val tmp = msg[i]
            msg[i] = msg[i + 1]
            msg[i + 1] = tmp
        }
        return msg
    }
}