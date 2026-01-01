package com.finger.burp.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;
import com.finger.burp.ui.FingerTableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class ActiveScannerTest {
    private MontoyaApi api;
    private FingerTableModel tableModel;
    private ActiveScanner scanner;
    private Fingerprint testFp;

    @BeforeEach
    void setUp() {
        api = mock(MontoyaApi.class, RETURNS_DEEP_STUBS);
        tableModel = mock(FingerTableModel.class);
        
        testFp = new Fingerprint();
        testFp.setName("TestCMS");
        testFp.setType("CMS");
        
        Rule rule = new Rule();
        rule.setLocation("hash");
        rule.setHash("1155597304"); // "hello" 的 hash
        testFp.setRules(Collections.singletonList(rule));
        
        // 使用同步执行器，确保测试时立即执行
        scanner = new ActiveScanner(api, Collections.singletonList(testFp), tableModel, Runnable::run);
    }

    @Test
    void testActiveScanWithHash() throws InterruptedException {
        HttpService service = mock(HttpService.class);
        when(service.toString()).thenReturn("http://example.com");
        
        HttpResponse response = mock(HttpResponse.class, RETURNS_DEEP_STUBS);
        when(response.body().getBytes()).thenReturn("hello".getBytes());
        when(response.bodyToString()).thenReturn("hello");
        when(response.statusCode()).thenReturn((short)200);
        
        // 模拟发送请求
        HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
        when(api.http().sendRequest(any(HttpRequest.class))).thenReturn(reqResp);
        when(reqResp.response()).thenReturn(response);

        // 使用 mockStatic 模拟 HttpRequest.httpRequest
        try (MockedStatic<HttpRequest> mockedHttpRequest = mockStatic(HttpRequest.class)) {
            HttpRequest mockReq = mock(HttpRequest.class);
            when(mockReq.path()).thenReturn("/favicon.ico");
            mockedHttpRequest.when(() -> HttpRequest.httpRequest(any(), anyString())).thenReturn(mockReq);

            scanner.scan(service);
            
            // 使用 ArgumentCaptor 捕获请求
            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(api.http(), atLeastOnce()).sendRequest(requestCaptor.capture());
            
            // 验证捕获到的请求中是否包含 /favicon.ico
            boolean foundFavicon = requestCaptor.getAllValues().stream()
                    .anyMatch(req -> req != null && "/favicon.ico".equals(req.path()));
            assertTrue(foundFavicon, "Should have requested /favicon.ico");
        }
        
        // 验证是否更新了 UI
        verify(tableModel, atLeastOnce()).addResult(any());
    }
}
