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
import http.HttpResponse;
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
            HttpResponse response = new HttpResponse(out);

            String path = getDefaultPath(request.getPath());

            if (request.getMethod().isPost() && "/user/create".equals(path)) {
                createUser(request, response);

            } else if (request.getMethod().isPost() && "/user/login".equals(path)) {
                login(request, response);

            } else if (request.getMethod().isGet() && "/user/list".equals(path)) {
                listUser(request, response);
            } else {
                response.forward(path);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void listUser(HttpRequest request, HttpResponse response) {
        if (!isLogin(request, request.getHeader("Cookie"))) {
                    response.sendRedirect("/user/login.html");
                    return;
                }

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
                    try {
                        response.forwardBody(sb.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
    }

    private void login(HttpRequest request, HttpResponse response) {
        User user = DataBase.findUserById(request.getParameter("userId"));

        if (user != null) {
            if (user.login(request.getParameter("password"))) {
                response.addHeader("Set-Cookie", "logined=ture");
                response.sendRedirect("index.html");
            } else {
                response.sendRedirect("user/login_failed.html");
            }
        } else {
            response.sendRedirect("user/login_failed.html");
        }
    }

    private void createUser(HttpRequest request, HttpResponse response) {
        User user = new User(
            request.getParameter("userId"),
            request.getParameter("password"),
            request.getParameter("name"),
            request.getParameter("email"));
        DataBase.addUser(user);
        log.debug("User : {}", user);
        response.sendRedirect("/index.html");
    }

    private String getDefaultPath(String path) {
        if (path.equals("/")) {
            return "/index.html";
        } else {
            return path;
        }
    }

    private boolean isLogin(HttpRequest request, String cookieValue) {
        String value = request.parseHeaderCookie(cookieValue);
        return Boolean.parseBoolean(value);
    }
}
