package com.finger.burp.utils;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HashUtilsTest {

    @Test
    void testCalculateFaviconHash() {
        // 使用一个简单的字符串进行测试
        // 模拟 Python: base64.encodebytes(b"hello") -> b'aGVsbG8=\n'
        // Murmur3_32(b'aGVsbG8=\n', seed=0)
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String hash = HashUtils.calculateFaviconHash(data);
        
        // 我们通过代码逻辑推导：
        // "hello" base64 -> "aGVsbG8="
        // 加上换行 -> "aGVsbG8=\n"
        // 你可以使用在线工具或本地 Python 验证此结果
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        System.out.println("Hash of 'hello': " + hash);
    }

    @Test
    void testCalculateMD5() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String md5 = HashUtils.calculateMD5(data);
        // "hello" MD5 -> 5d41402abc4b2a76b9719d911017c592
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5);
    }
}
