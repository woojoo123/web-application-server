package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream();
             OutputStream out = connection.getOutputStream()) {

            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            
            // 요청 첫 줄 읽기
            String requestLine = br.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String url = parts[1];

            int contentLength = 0;
            String cookieHeader = null;
            requestLine = br.readLine();
            while (requestLine != null && !requestLine.isEmpty()) {
                if (requestLine.startsWith("Content-Length:")) {
                    contentLength = getContentLength(requestLine);
                } else if (requestLine.startsWith("Cookie:")) {
                    cookieHeader = requestLine.substring("Cookie:".length()).trim();
                }
                requestLine = br.readLine();
            }

            Map<String, String> cookies = HttpRequestUtils.parseCookies(cookieHeader);
            boolean logined = "true".equals(cookies.get("logined"));

            
            // 정적 파일 응답
            if ("/".equals(url)) url = "/index.html";

            if ("POST".equals(method) && url.startsWith("/user/create")) {
                String requestBody = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                User user = new User (
                    params.get("userId"),
                    params.get("password"),
                    params.get("name"),
                    params.get("email")
                );
                DataBase.addUser(user);
                log.debug("User : {}", user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");

            } else if ("POST".equals(method) && url.startsWith("/user/login")) {
                String requestBody = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                String userId = params.get( "userId");
                String password = params.get("password");
                User user = DataBase.findUserById(userId);

                if (user == null) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos, "user/login_failed.html");
                    return;
                }
                
                DataOutputStream dos = new DataOutputStream(out);
                if (password.equals(user.getPassword())) {
                    //로그인 성공 -> 홈으로 리다이렉트
                    response302HeaderWithCookie(dos,"/index.html", "logined=true");
                } else {
                    response302HeaderWithCookie(dos,"/user/login.failed.html", "logined=false");
                }
            } else if ("GET".equals(method) && url.startsWith("/user/list")) {
                DataOutputStream dos = new DataOutputStream(out);
                if (!logined) {
                    response302Header(dos, "user/login.html");
                } else {
                    Collection<User> users = DataBase.findAll();
                    StringBuilder sb = new StringBuilder();
                    sb.append("<table border='1'>");
                    sb.append("<tr><th>userId</th><th>name</th><th>email</th></tr>");

                    for (User user : users) {
                        sb.append("<tr>");
                        sb.append("<td>").append(user.getUserId()).append("</td>");
                        sb.append("<td>").append(user.getName()).append("</td>");
                        sb.append("<td>").append(user.getEmail()).append("</td>");
                        sb.append("</tr>");
                    }
                    sb.append("</table>");
                    byte[] body = sb.toString().getBytes("UTF-8");
                    response200Header(dos, body.length);
                    responseBody(dos, body);
                }
            } else if (url.endsWith(".css")) {
                DataOutputStream dos = new DataOutputStream(out);
                if (url.startsWith("/")) url = url.substring(1);
                byte[] body = Files.readAllBytes(Path.of("webapp", url));
                response200CssHeader(dos, body.length);
                responseBody(dos, body);
            } else {
                responseResource(out, url);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseResource(OutputStream out, String url) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        if (url.startsWith("/")) url = url.substring(1);
        byte[] body = Files.readAllBytes(Path.of("webapp", url));
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void response302Header(DataOutputStream dos, String location) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String location, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private int getContentLength(String line) {
        String headTokens[] = line.split(":");
        return Integer.parseInt(headTokens[1].trim());
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
