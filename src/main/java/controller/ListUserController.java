package controller;

import java.io.IOException;
import java.util.Collection;

import db.DataBase;
import http.HttpRequest;
import http.HttpResponse;
import model.User;

public class ListUserController extends AbstractController {

    @Override
    public void doGet(HttpRequest request, HttpResponse response) {
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

    private boolean isLogin(HttpRequest request, String cookieValue) {
        String value = request.parseHeaderCookie(cookieValue);
        return Boolean.parseBoolean(value);
    }
}
