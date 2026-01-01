package com.finger.burp.utils;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 哈希计算工具类
 */
public class HashUtils {

    /**
     * 计算 MurmurHash3_32 (常用作 favicon 识别)
     * 逻辑参考 Shodan 的实现: 对内容进行 Base64 编码，每 76 个字符加一个换行符 \n，最后加一个 \n，再进行计算。
     * 
     * @param data 原始字节数组
     * @return 哈希值字符串
     */
    public static String calculateFaviconHash(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        
        // 1. Base64 编码
        String base64 = Base64.getEncoder().encodeToString(data);
        
        // 2. 模拟 Python 的 base64.encodebytes 行为: 每 76 字符换行，末尾换行
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i++) {
            sb.append(base64.charAt(i));
            if ((i + 1) % 76 == 0) {
                sb.append("\n");
            }
        }
        sb.append("\n");
        
        // 3. 计算 MurmurHash3_32 (Seed = 0)
        int hash = Hashing.murmur3_32_fixed().hashString(sb.toString(), StandardCharsets.UTF_8).asInt();
        return String.valueOf(hash);
    }

    /**
     * 计算 MD5 哈希
     * 
     * @param data 原始字节数组
     * @return MD5 字符串
     */
    public static String calculateMD5(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return Hashing.md5().hashBytes(data).toString();
    }
}
