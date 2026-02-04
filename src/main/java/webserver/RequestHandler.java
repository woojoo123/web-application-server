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
import http.HttpMethod;
import http.HttpRequest;
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
        log.debug("New Client Connect! Connected IP : {}, Port : {}", 
                    connection.getInetAddress(), connection.getPort());

        try (InputStream in = connection.getInputStream();
             OutputStream out = connection.getOutputStream()) {

            HttpRequest request = new HttpRequest(in);
            String path = getDefaultPath(request.getPath());

            if (request.getMethod().isPost() && "/user/create".equals(path)) {
                User user = new User(
                    request.getParameter("userId"),
                    request.getParameter("password"),
                    request.getParameter("name"),
                    request.getParameter("email"));
                DataBase.addUser(user);
                log.debug("User : {}", user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");

            } else if (request.getMethod().isPost() && "/user/login".equals(path)) {
                User user = DataBase.findUserById(request.getParameter("userId"));
                if (user == null) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos, "user/login_failed.html");
                    return;
                }             
                DataOutputStream dos = new DataOutputStream(out);
                if (request.getParameter("password").equals(user.getPassword())) {
                    //로그인 성공 -> 홈으로 리다이렉트
                    response302HeaderWithCookie(dos,"/index.html", "logined=true");
                } else {
                    response302HeaderWithCookie(dos,"/user/login.failed.html", "logined=false");
                }
            } else if (request.getMethod().isGet() && "/user/list".equals(path)) {
                DataOutputStream dos = new DataOutputStream(out);
                if (!isLogin(request.getHeader("Cookie"))) {
                    response302Header(dos, "user/login.html");
                    return;
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

            } else if (path.endsWith(".css")) {
                DataOutputStream dos = new DataOutputStream(out);
                if (path.startsWith("/")) path = path.substring(1);
                byte[] body = Files.readAllBytes(Path.of("webapp", path));
                response200CssHeader(dos, body.length);
                responseBody(dos, body);

            } else {
                responseResource(out, path);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private String getDefaultPath(String path) {
        if (path.equals("/")) {
            return "/index.html";
        } else {
            return path;
        }
    }

    private boolean isLogin(String cookieValue) {
        Map<String, String> cookies = HttpRequestUtils.parseCookies(cookieValue);
        String value = cookies.get("logined");
        return Boolean.parseBoolean(value);
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
