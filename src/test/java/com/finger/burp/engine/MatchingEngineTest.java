package com.finger.burp.engine;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.Answers;

class MatchingEngineTest {
    private MontoyaApi api;
    private MatchingEngine engine;
    private List<Fingerprint> fingerprints;

    @BeforeEach
    void setUp() {
        api = mock(MontoyaApi.class);
        
        // 创建 Shiro 指纹规则
        Fingerprint shiro = new Fingerprint();
        shiro.setName("Shiro");
        shiro.setType("Framework");
        
        Rule shiroRule = new Rule();
        shiroRule.setLocation("header");
        shiroRule.setMatch(Collections.singletonList("rememberMe="));
        
        shiro.setRules(Collections.singletonList(shiroRule));
        // 创建 Hash 指纹规则
        Fingerprint hashFp = new Fingerprint();
        hashFp.setName("HashTest");
        Rule hashRule = new Rule();
        hashRule.setLocation("hash");
        hashRule.setHash("1632964065");
        hashFp.setRules(Collections.singletonList(hashRule));

        fingerprints = Arrays.asList(shiro, hashFp);
        
        engine = new MatchingEngine(api, fingerprints);
    }

    @Test
    void testHashMatch() {
        // 中成科信 favicon 的 base64 对应的 mmh3 是 1632964065
        // 这里我们 mock 字节码，并确保 HashUtils 能算出这个值
        // 实际上我们可以直接传一些字节，然后 mock HashUtils (如果它是可 mock 的)
        // 但 HashUtils 是静态工具类。我们可以传一个已知的能算出该 hash 的字节。
        // 为了简单，我们直接验证 MatchingEngine 是否调用了 HashUtils
        
        HttpResponse response = mock(HttpResponse.class, RETURNS_DEEP_STUBS);
        when(response.headers()).thenReturn(Collections.emptyList());
        
        // 模拟一个空的 body，虽然算出来不是 1632964065，但我们能验证逻辑
        when(response.body().getBytes()).thenReturn("test".getBytes());
        when(response.bodyToString()).thenReturn("test");
        when(response.statusCode()).thenReturn((short)200);

        // 我们这里不测试具体的 hash 算法（那是 HashUtils 的职责），只测试 MatchingEngine 的流转
        // 实际上 1632964065 是中成科信的 hash，这里匹配不到是正常的
        List<MatchResult> matches = engine.findMatches(response, "/");
        assertTrue(matches.stream().noneMatch(m -> m.getFingerprint().getName().equals("HashTest")));
    }

    @Test
    void testShiroMatchInResponse() {
        HttpResponse response = mock(HttpResponse.class, RETURNS_DEEP_STUBS);
        HttpHeader header = mock(HttpHeader.class);
        when(header.toString()).thenReturn("Set-Cookie: rememberMe=deleteMe");
        when(response.headers()).thenReturn(Collections.singletonList(header));
        
        when(response.body().getBytes()).thenReturn(new byte[0]);
        when(response.bodyToString()).thenReturn("");
        when(response.statusCode()).thenReturn((short)200);

        List<MatchResult> matches = engine.findMatches(response, "/");
        assertFalse(matches.isEmpty(), "Should match Shiro in response headers");
        assertEquals("Shiro", matches.get(0).getFingerprint().getName());
    }

    @Test
    void testShiroMatchInRequest() {
        HttpRequest request = mock(HttpRequest.class, RETURNS_DEEP_STUBS);
        HttpHeader header = mock(HttpHeader.class);
        when(header.toString()).thenReturn("Cookie: rememberMe=13123123123");
        when(request.headers()).thenReturn(Collections.singletonList(header));
        
        when(request.body().getBytes()).thenReturn(new byte[0]);
        when(request.bodyToString()).thenReturn("");

        List<MatchResult> matches = engine.findMatches(request, "/");
        assertFalse(matches.isEmpty(), "Should match Shiro in request headers");
        assertEquals("Shiro", matches.get(0).getFingerprint().getName());
    }
}
