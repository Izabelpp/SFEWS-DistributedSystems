package com.sfews.waterlevel;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

/**
 * Starts the Water Level Monitoring gRPC server on port 50051.
 * Run this class to start the service.
 */
public class WaterLevelServer {

    private static final int PORT = 50051;
    private Server server;

    public void start() throws IOException {
        // Build and start the gRPC server with our service implementation
        server = ServerBuilder.forPort(PORT)
                .addService(new WaterLevelServiceImpl())
                .build()
                .start();

        System.out.println("[WaterLevel] Server started on port " + PORT);

        // Add a shutdown hook so the server stops cleanly when the program exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[WaterLevel] Shutting down server...");
            stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    // Keep the server running until it is terminated
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        WaterLevelServer waterLevelServer = new WaterLevelServer();
        waterLevelServer.start();
        waterLevelServer.blockUntilShutdown();
    }
}