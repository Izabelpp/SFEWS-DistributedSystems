package com.sfews.drainage;

import com.sfews.ServiceRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

/**
 * Starts the Drainage Control gRPC server on port 50052.
 * Registers itself with jmDNS so the GUI can discover it automatically.
 */
public class DrainageServer {

    private static final int PORT = 50052;
    private Server server;
    private ServiceRegistry registry;

    public void start() throws IOException {
        // Start jmDNS and register this service
        registry = new ServiceRegistry();
        registry.start();
        registry.registerService(
                ServiceRegistry.DRAINAGE_TYPE,
                "DrainageControlService",
                PORT,
                "Controls drainage gates and pump stations across the city"
        );

        // Start the gRPC server
        server = ServerBuilder.forPort(PORT)
                .addService(new DrainageServiceImpl())
                .build()
                .start();

        System.out.println("[Drainage] Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Drainage] Shutting down...");
            registry.stop();
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