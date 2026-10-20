package com.dsh.mail;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Пароли почты шифруются ключом из Android Keystore (AES/GCM).
 * Если Keystore недоступен — пароль сохраняется как есть с префиксом raw:.
 */
public final class Crypto {

    private static final String ALIAS = "dsh_mail_key_v1";
    private static final String PREFIX_ENC = "enc:";
    private static final String PREFIX_RAW = "raw:";

    private Crypto() {
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        kg.init(spec);
        return kg.generateKey();
    }

    public static String encrypt(String plain) {
        if (plain == null) return "";
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return PREFIX_ENC + Base64.encodeToString(all, Base64.NO_WRAP);
        } catch (Exception e) {
            return PREFIX_RAW + plain;
        }
    }

    public static String decrypt(String stored) {
        if (stored == null) return "";
        if (stored.startsWith(PREFIX_RAW)) return stored.substring(PREFIX_RAW.length());
        if (!stored.startsWith(PREFIX_ENC)) return stored;
        try {
            byte[] all = Base64.decode(stored.substring(PREFIX_ENC.length()), Base64.NO_WRAP);
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
