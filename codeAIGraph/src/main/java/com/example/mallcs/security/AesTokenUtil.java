package com.example.mallcs.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 自研 AES-256/CBC Token 工具类，不依赖任何第三方 JWT 库。
 *
 * <h3>Token 结构</h3>
 * <pre>
 *   Base64Url( IV[16 字节] || AES-256-CBC( JSON 载荷 ) )
 * </pre>
 *
 * <h3>JSON 载荷字段</h3>
 * <pre>
 *   { "u": userId, "n": username, "r": role, "e": expireTimestamp(ms) }
 * </pre>
 *
 * <h3>密钥派生</h3>
 * 将配置中的 {@code token.secret} 字符串做 SHA-256，得到 32 字节 AES-256 密钥。
 * 每次生成 Token 时随机生成 IV，保证相同载荷每次密文不同。
 */
@Component
public class AesTokenUtil {

    private static final String CIPHER_ALGO = "AES/CBC/PKCS5Padding";
    private static final int    IV_LEN      = 16;

    @Value("${token.secret}")
    private String secret;

    @Value("${token.expiration:86400000}")
    private long expiration;

    private final ObjectMapper  objectMapper = new ObjectMapper();
    private final SecureRandom  secureRandom = new SecureRandom();

    // ----------------------------------------------------------------
    // 公开 API
    // ----------------------------------------------------------------

    /**
     * 生成 Token。
     *
     * @param userId   用户 ID（字符串）
     * @param username 用户名
     * @param role     角色（USER / ADMIN）
     * @return Base64Url 编码的 AES 加密 Token
     */
    public String generateToken(String userId, String username, String role) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("u", userId);
            payload.put("n", username);
            payload.put("r", role);
            payload.put("e", System.currentTimeMillis() + expiration);

            byte[] plaintext  = objectMapper.writeValueAsBytes(payload);
            byte[] iv         = randomIv();
            byte[] ciphertext = encrypt(plaintext, iv);

            // token = Base64Url(IV || ciphertext)
            byte[] combined = concat(iv, ciphertext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);

        } catch (Exception e) {
            throw new IllegalStateException("Token 生成失败", e);
        }
    }

    /** 解析 Token，返回载荷 Map。Token 非法或已过期均抛出异常。 */
    public Map<String, Object> parseToken(String token) {
        try {
            byte[] combined   = Base64.getUrlDecoder().decode(token);
            if (combined.length <= IV_LEN) {
                throw new IllegalArgumentException("Token 格式错误");
            }

            byte[] iv         = Arrays.copyOfRange(combined, 0, IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LEN, combined.length);
            byte[] plaintext  = decrypt(ciphertext, iv);

            Map<String, Object> payload = objectMapper.readValue(
                    plaintext, new TypeReference<Map<String, Object>>() {});

            long expireAt = ((Number) payload.get("e")).longValue();
            if (System.currentTimeMillis() > expireAt) {
                throw new IllegalStateException("Token 已过期");
            }

            return payload;

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token 解析失败: " + e.getMessage(), e);
        }
    }

    public String getUserId(String token) {
        return String.valueOf(parseToken(token).get("u"));
    }

    public String getUsername(String token) {
        return String.valueOf(parseToken(token).get("n"));
    }

    public String getRole(String token) {
        return String.valueOf(parseToken(token).get("r"));
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // 内部工具
    // ----------------------------------------------------------------

    /** 对明文做 AES-256/CBC/PKCS5 加密，返回密文 */
    private byte[] encrypt(byte[] plaintext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new IvParameterSpec(iv));
        return cipher.doFinal(plaintext);
    }

    /** 对密文做 AES-256/CBC/PKCS5 解密，返回明文 */
    private byte[] decrypt(byte[] ciphertext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }

    /** 从 token.secret 派生 AES-256 密钥（SHA-256，32 字节） */
    private SecretKeySpec aesKey() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_LEN];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
