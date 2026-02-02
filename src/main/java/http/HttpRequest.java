package http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import util.HttpRequestUtils;
import util.IOUtils;
import util.HttpRequestUtils.Pair;

public class HttpRequest {
    private String method;
    private String url;
    private String path;
    private String queryString;
    private Map<String, String> headers;
    private String body;
    private Map<String, String> parameter;
    
    public HttpRequest(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        
        // 1) 요청 라인 파싱
        String requestLine = br.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            throw new IOException("Empty request line");
        }
        String[] parts = requestLine.split(" ");
        this.method = parts[0];
        this.url = parts[1];

        // 2) URL에서 path/query 분리
        int idx = url.indexOf('?');
        if (idx >= 0) {
            this.path = url.substring(0, idx);
            this.queryString = url.substring(idx + 1);
        } else {
            this.path = url;
            this.queryString = null;
        }

        // 3) 헤더 파싱
        Map<String, String> headerMap = new HashMap<>();
        String line = br.readLine();
        while (line != null && !line.isEmpty()) {
            Pair header = HttpRequestUtils.parseHeader(line);
            if (header != null) {
                headerMap.put(header.getKey(), header.getValue());
            }
            line = br.readLine();
        }
        this.headers = Collections.unmodifiableMap(headerMap);

        
        // 4) 본문 읽기
        int contentLength = 0;
        String contentLengthValue = headerMap.get("Content-Length");
        if (contentLengthValue != null) {
            contentLength = Integer.parseInt(contentLengthValue);
        }
        this.body = (contentLength > 0) ? IOUtils.readData(br, contentLength) : "";
        
        // 5) 파라미터 파싱 (GET/POST 구분)
        if ("GET".equals(method)) {
            this.parameter = HttpRequestUtils.parseQueryString(queryString);
        } else if ("POST".equals(method)) {
            this.parameter = HttpRequestUtils.parseQueryString(body);
        } else {
            this.parameter = new HashMap<>();
        }

    }

    public String getMethod() {
        return this.method;
    }

    public String getPath() {
        return this.path;
    }

    public String getHeader(String string) {
        return headers.get(string);
    }

    public String getParameter(String string) {
        return parameter.get(string);
    }
}
