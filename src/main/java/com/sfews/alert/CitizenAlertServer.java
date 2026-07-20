package com.sfews.alert;

/**
 *
 * @author izabel
 */

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class CitizenAlertServer {

    private static final int PORT = 50053;
    private Server server;

    public void start() throws IOException {
        server = ServerBuilder.forPort(PORT)
                .addService(new CitizenAlertServiceImpl())
                .build()
                .start();

        System.out.println("[Alert] Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Alert] Shutting down server...");
            stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CitizenAlertServer alertServer = new CitizenAlertServer();
        alertServer.start();
        alertServer.blockUntilShutdown();
    }
}
