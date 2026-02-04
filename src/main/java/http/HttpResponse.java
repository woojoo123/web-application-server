package http;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpResponse {
    private static final Logger log = LoggerFactory.getLogger(HttpResponse.class);
    
    private final DataOutputStream dos;
    
    private final Map<String, String> headers = new LinkedHashMap<>();
    
    public HttpResponse(OutputStream out) {
        this.dos = new DataOutputStream(out);
    }

    // 200 OK 응답
    public void forward(String url) {
        try {
            if (url.startsWith("/")) url = url.substring(1);
            byte[] body = Files.readAllBytes(Path.of("webapp", url));

            if (url.endsWith(".css")) {
                addHeader("Content-Type", "text/css");
            } else if (url.endsWith(".js")) {
                addHeader("Content-Type", "application/javascript");
            } else {
                addHeader("Content-Type", "text/html;charset=utf-8");
            }
            addHeader("Content-Length", body.length + "");
            response200Header();
            responseBody(body);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    public void forwardBody(String body) throws IOException {
        byte[] contents = body.getBytes("UTF-8");
        addHeader("Content-Type", "text/html;charset=utf-8");
        addHeader("Content-Length", contents.length + "");

        response200Header();
        responseBody(contents);
    }

    // 302 리다이렉트
    public void sendRedirect(String redirectUrl) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("\r\n");
    } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
    } catch (IOException e) {
        log.error(e.getMessage());
    }
}

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    private void response200Header() {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n" );
            processHeaders();
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void processHeaders() throws IOException {
        for (Map.Entry<String, String> h : headers.entrySet()) {
            dos.writeBytes(h.getKey() + ": " + h.getValue() + "\r\n");
            }
    }
}
