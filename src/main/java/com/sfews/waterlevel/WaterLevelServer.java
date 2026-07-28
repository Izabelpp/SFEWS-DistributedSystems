package com.sfews.waterlevel;

import com.sfews.ServiceRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

/**
 * Starts the Water Level Monitoring gRPC server on port 50051.
 * Registers itself with jmDNS so the GUI can discover it automatically.
 */
public class WaterLevelServer {

    private static final int PORT = 50051;
    private Server server;
    private ServiceRegistry registry;

    public void start() throws IOException {
        // Start jmDNS and register this service
        registry = new ServiceRegistry();
        registry.start();
        registry.registerService(
                ServiceRegistry.WATER_LEVEL_TYPE,
                "WaterLevelMonitoringService",
                PORT,
                "Monitors water levels and flood risk across city zones"
        );

        // Start the gRPC server
        server = ServerBuilder.forPort(PORT)
                .addService(new WaterLevelServiceImpl())
                .build()
                .start();

        System.out.println("[WaterLevel] Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[WaterLevel] Shutting down...");
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
        WaterLevelServer waterLevelServer = new WaterLevelServer();
        waterLevelServer.start();
        waterLevelServer.blockUntilShutdown();
    }
}