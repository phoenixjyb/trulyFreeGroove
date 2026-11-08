package com.trulyfreemusic.opengroove.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted, excluded from Android backup and transfer via noBackupFilesDir. */
class HomeMusicStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "home-music-account"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): HomeMusicConfig? = runCatching {
        val bytes = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        HomeMusicConfig(json.getString("server"), json.getString("username"), json.getString("password"))
    }.getOrNull()

    @Synchronized fun save(config: HomeMusicConfig) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().put("server", config.server).put("username", config.username).put("password", config.password)
        val output = file.startWrite()
        try {
            output.write(cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)))
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
    }
    @Synchronized fun clear() { file.delete() }
    private companion object { const val ALIAS = "opengroove.home-music.v1" }
}
