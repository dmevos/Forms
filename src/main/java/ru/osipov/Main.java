package ru.osipov;

import java.io.BufferedOutputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Server server = new Server();

        server.addHandler("GET", "/messages",
                (request, out) -> sendResponse("Hello from GET /messages", out));

        server.addHandler("POST", "/messages",
                (request, out) -> sendResponse("Hello from POST /messages", out));

        server.listen(9999);
    }

    private static void sendResponse(String response, BufferedOutputStream out) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + response.length() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        ).getBytes());
        out.write(response.getBytes());
        out.flush();
    }
}