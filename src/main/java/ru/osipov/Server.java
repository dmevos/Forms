package ru.osipov;

import java.io.*;
import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final static int THREAD_NUMBER = 64;
    private ExecutorService executor;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlers = new ConcurrentHashMap<>();

    //Запуск сервера
    public void listen(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Сервер стартовал!");
        } catch (IOException e) {
            System.out.println("[ERROR] Ошибка запуска сервера: " + e.getMessage());
        }
        executor = Executors.newFixedThreadPool(THREAD_NUMBER);
        listenConnection();
    }

    public void listenConnection() {
        while (true) {
            try {
                final var socket = serverSocket.accept();
                executor.submit(() -> new ConnectionHandler(socket, handlers).handle());
            } catch (IOException e) {
                System.out.println("Косяк с новым подключенинем:   " + e.getMessage());
            }
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) handlers.put(method, new ConcurrentHashMap<>());

        handlers.get(method).put(path, handler);
    }
}