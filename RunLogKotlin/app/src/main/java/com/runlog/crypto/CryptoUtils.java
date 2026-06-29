package com.runlog.crypto;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CryptoUtils {
    private CryptoUtils() {
    }

    public static String md5Lower(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    public static String requestSign(String platform, long utc, String uuid, String md5Key) throws Exception {
        return md5Lower("platform=" + platform + "&utc=" + utc + "&uuid=" + uuid + "&appsecret=" + md5Key);
    }

    public static String sm4EncryptBase64(String plain, String keyBase64) throws Exception {
        byte[] key = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT);
        byte[] input = plain.getBytes(StandardCharsets.UTF_8);
        return android.util.Base64.encodeToString(sm4(true, key, input), android.util.Base64.NO_WRAP);
    }

    public static String sm4EncryptBase64(byte[] input, String keyBase64) throws Exception {
        byte[] key = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT);
        return android.util.Base64.encodeToString(sm4(true, key, input), android.util.Base64.NO_WRAP);
    }

    public static String sm4DecryptBase64ToString(String encryptedBase64, String keyBase64) throws Exception {
        return new String(sm4DecryptBase64(encryptedBase64, keyBase64), StandardCharsets.UTF_8);
    }

    public static byte[] sm4DecryptBase64(String encryptedBase64, String keyBase64) throws Exception {
        byte[] key = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT);
        byte[] input = android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT);
        return sm4(false, key, input);
    }

    private static byte[] sm4(boolean encrypt, byte[] key, byte[] input) throws Exception {
        PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine(), new PKCS7Padding());
        cipher.init(encrypt, new KeyParameter(key));
        byte[] out = new byte[cipher.getOutputSize(input.length)];
        int len = cipher.processBytes(input, 0, input.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] exact = new byte[len];
        System.arraycopy(out, 0, exact, 0, len);
        return exact;
    }
}
