package com.sfews.drainage;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

/**
 * Starts the Drainage Control gRPC server on port 50052.
 * Run this class to start the service.
 */
public class DrainageServer {

    private static final int PORT = 50052;
    private Server server;

    public void start() throws IOException {
        server = ServerBuilder.forPort(PORT)
                .addService(new DrainageServiceImpl())
                .build()
                .start();

        System.out.println("[Drainage] Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Drainage] Shutting down server...");
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
        DrainageServer drainageServer = new DrainageServer();
        drainageServer.start();
        drainageServer.blockUntilShutdown();
    }
}