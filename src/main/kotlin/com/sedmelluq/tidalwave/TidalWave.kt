package com.sedmelluq.tidalwave

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TidalWave : AutoCloseable {
  enum class Quality {
    LOSSLESS
  }

  fun download(trackId: String, token: String, quality: Quality): ByteArray {
    val playbackInfoResponse = downloadBytes(HttpGet(
        "https://desktop.tidal.com/v1/tracks/$trackId/playbackinfopostpaywall?" +
            "audioquality=$quality&playbackmode=STREAM&assetpresentation=FULL"
    ).apply {
      setHeader("Authorization", token)
    }, "playback info")

    val playbackInfo = decodePlaybackInfo(playbackInfoResponse)
    val decryptedKey = decryptKey(playbackInfo.encryptedKey)

    val encryptedTrack = downloadBytes(HttpGet(playbackInfo.url).apply {
      setHeader("User-Agent", "TIDAL_NATIVE_PLAYER/WIN/3.0.2.27")
    }, "track data")

    return decryptTrack(decryptedKey, encryptedTrack)
  }

  override fun close() {
    httpClient.close()
  }

  private companion object {
    private val mapper = ObjectMapper()
  }

  private val httpClient = HttpClientBuilder.create().build()

  private val masterSecretKey = SecretKeySpec(
      base64Decode("=4574gmu/ofRTgGdM6W54H2x06CU/owoGfLmmMETTlIU".reversed()),
      "AES"
  )

  private fun downloadBytes(request: HttpUriRequest, type: String): ByteArray {
    httpClient.execute(request).use { response ->
      val code = response.statusLine.statusCode

      if (code != HttpStatus.SC_OK) {
        throw RuntimeException("Received response code $code from $type request")
      }

      return EntityUtils.toByteArray(response.entity)
    }
  }

  private data class PlaybackInfo(
      val url: String,
      val encryptedKey: String
  )

  private fun decodePlaybackInfo(playbackInfoResponse: ByteArray): PlaybackInfo {
    val playbackInfo = mapper.readTree(playbackInfoResponse)
    val encodedManifest = playbackInfo["manifest"]?.textValue()
        ?: throw RuntimeException("Found no manifest in response")
    val decodedManifest = String(base64Decode(encodedManifest))
    val manifest = mapper.readTree(decodedManifest)

    return PlaybackInfo(
        manifest["urls"]?.firstOrNull()?.textValue()
            ?: throw RuntimeException("Found no url in manifest"),
        manifest["keyId"]?.textValue()
            ?: throw RuntimeException("Found no key in manifest")
    )
  }

  private fun decryptTrack(cipherKey: ByteArray, encrypted: ByteArray): ByteArray {
    val secretKey = SecretKeySpec(cipherKey.copyOfRange(0, 16), "AES")

    val ivBytes = cipherKey.copyOfRange(16, 32)
    ByteBuffer.wrap(ivBytes).putLong(8, 0)

    Cipher.getInstance("AES/CTR/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
      return doFinal(encrypted)
    }
  }

  private fun decryptKey(key: String): ByteArray {
    val infoBytes = base64Decode(key)

    val ivBytes = infoBytes.copyOfRange(0, 16)
    val encrypted = infoBytes.copyOfRange(16, 48)

    Cipher.getInstance("AES/CBC/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, masterSecretKey, IvParameterSpec(ivBytes))
      return doFinal(encrypted)
    }
  }

  private fun base64Decode(input: String): ByteArray {
    return Base64.getDecoder().decode(input)
  }
}
