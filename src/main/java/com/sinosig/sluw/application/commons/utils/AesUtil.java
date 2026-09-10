package com.sinosig.sluw.application.commons.utils;

import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加密解密工具类。
 * <p>提供基于 AES-128 算法的加密与解密功能，结合 Base64 编码和十六进制转换。</p>
 * <p>适配 JDK 17，使用 {@link java.util.Base64} 替代过时的 sun.misc 包。</p>
 *
 * @author SinoSig AI Team
 */
public final class AesUtil {

    private static final Logger logger = LoggerFactory.getLogger(AesUtil.class);
    private static final String ENCODING = "UTF-8";
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 128;
    private static final String RNG_ALGORITHM = "SHA1PRNG";

    // 私有构造器，禁止实例化
    private AesUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * AES 加密。
     *
     * @param content 明文字符串
     * @param key     加密密钥
     * @return 密文（Base64 编码后的十六进制字符串），若加密失败返回 null
     */
    public static String encryptAES(String content, String key) {
        if (content == null || key == null) {
            logger.warn("加密参数为空，content={}, key={}", content, key);
            return null;
        }
        try {
            byte[] encryptResult = encrypt(content.getBytes(StandardCharsets.UTF_8), key);
            if (encryptResult == null) {
                return null;
            }
            String hexStr = parseByte2HexStr(encryptResult);
            return Base64.getEncoder().encodeToString(hexStr.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("AES 加密失败", e);
            return null;
        }
    }

    /**
     * AES 解密。
     *
     * @param encryptResultStr 密文（Base64 编码后的十六进制字符串）
     * @param key              解密密钥
     * @return 明文字符串，若解密失败返回 null
     */
    public static String decryptAES(String encryptResultStr, String key) {
        if (encryptResultStr == null || key == null) {
            logger.warn("解密参数为空，encryptResultStr={}, key={}", encryptResultStr, key);
            return null;
        }
        try {
            byte[] base64Decoded = Base64.getDecoder().decode(encryptResultStr);
            String hexStr = new String(base64Decoded, StandardCharsets.UTF_8);
            byte[] decryptFrom = parseHexStr2Byte(hexStr);
            if (decryptFrom == null) {
                return null;
            }
            byte[] decryptResult = decrypt(decryptFrom, key);
            if (decryptResult == null) {
                return null;
            }
            return new String(decryptResult, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("AES 解密失败，密文可能格式不正确", e);
            return null;
        }
    }

    /**
     * AES 加密核心逻辑。
     *
     * @param byteContent 明文字节数组
     * @param password    密钥字符串
     * @return 密文字节数组，失败返回 null
     */
    private static byte[] encrypt(byte[] byteContent, String password) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password));
            return cipher.doFinal(byteContent);
        } catch (Exception e) {
            logger.error("AES 加密核心逻辑异常", e);
            return null;
        }
    }

    /**
     * AES 解密核心逻辑。
     *
     * @param content  密文字节数组
     * @param password 密钥字符串
     * @return 明文字节数组，失败返回 null
     */
    private static byte[] decrypt(byte[] content, String password) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password));
            return cipher.doFinal(content);
        } catch (Exception e) {
            logger.error("AES 解密核心逻辑异常", e);
            return null;
        }
    }

    /**
     * 根据密码生成 AES 密钥规范。
     * <p>使用 SHA1PRNG 固定种子以保证跨平台一致性（与旧版本兼容）。</p>
     *
     * @param password 密码字符串
     * @return SecretKeySpec 对象
     * @throws Exception 密钥生成异常
     */
    private static SecretKeySpec getSecretKey(String password) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance(ALGORITHM);
        SecureRandom secureRandom = SecureRandom.getInstance(RNG_ALGORITHM);
        secureRandom.setSeed(password.getBytes(StandardCharsets.UTF_8));
        kgen.init(KEY_SIZE, secureRandom);
        SecretKey secretKey = kgen.generateKey();
        return new SecretKeySpec(secretKey.getEncoded(), ALGORITHM);
    }

    /**
     * 将二进制字节数组转换为十六进制字符串。
     *
     * @param buf 字节数组
     * @return 大写十六进制字符串
     */
    private static String parseByte2HexStr(byte[] buf) {
        if (buf == null || buf.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 将十六进制字符串转换为二进制字节数组。
     *
     * @param hexStr 十六进制字符串
     * @return 字节数组，若输入无效返回 null
     */
    private static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr == null || hexStr.isEmpty() || hexStr.length() % 2 != 0) {
            logger.warn("无效的十六进制字符串: {}", hexStr);
            return null;
        }
        int len = hexStr.length();
        byte[] result = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                result[i / 2] = (byte) Integer.parseInt(hexStr.substring(i, i + 2), 16);
            }
            return result;
        } catch (NumberFormatException e) {
            logger.error("解析十六进制字符串失败: {}", hexStr, e);
            return null;
        }
    }

    /**
     * 加密字符串
     * @param plaintext 明文
     * @return 加密后的Base64字符串
     * @throws Exception 加密异常
     */
    public static String encrypt(String plaintext) throws Exception {

        ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
        String SECRET_KEY = configReader.getProperty("aes", "key");
        // 创建密钥
        SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");

        // 创建GCM参数
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, iv);

        // 加密
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        if(SpringCtxUtil.getSpringContext()==null){
            logger.info("222SpringCtxUtil.getSpringContext(): 空");
        }
        // 合并IV和密文
        byte[] encryptedData = new byte[12 + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, 12);
        System.arraycopy(ciphertext, 0, encryptedData, 12, ciphertext.length);
        // 返回Base64编码
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * 解密字符串
     * @param encryptedData Base64编码的加密数据
     * @return 解密后的明文
     * @throws Exception 解密异常
     */
    public static String decrypt(String encryptedData) throws Exception {
        ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
        String SECRET_KEY = configReader.getProperty("aes", "key");
        // 解码Base64
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);

        // 分离IV和密文
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[decodedData.length - 12];
        System.arraycopy(decodedData, 0, iv, 0, 12);
        System.arraycopy(decodedData, 12, ciphertext, 0, ciphertext.length);

        // 创建密钥和GCM参数
        SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, iv);

        // 解密
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);

        // 返回明文
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * 测试入口（仅用于开发调试）。
     */
    public static void main(String[] args) {
        test();
    }

    private static void test() {
        String key = "TEST_ONLY_KEY_01";
        System.out.println("加密解密测试：");
        String content = "{\"balaDate\":\"2023-05-01\"}";
        System.out.println("原内容：" + content);
        String encryContent = encryptAES(content, key);
        System.out.println("加密后：" + encryContent);
        String decryContent = decryptAES(encryContent, key);
        System.out.println("解密后：" + decryContent);
    }
}