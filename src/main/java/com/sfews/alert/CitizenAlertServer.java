package com.sfews.alert;

import com.sfews.ServiceRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

/**
 * Starts the Citizen Alert gRPC server on port 50053.
 * Registers itself with jmDNS so the GUI can discover it automatically.
 */
public class CitizenAlertServer {

    private static final int PORT = 50053;
    private Server server;
    private ServiceRegistry registry;

    public void start() throws IOException {
        // Start jmDNS and register this service
        registry = new ServiceRegistry();
        registry.start();
        registry.registerService(
                ServiceRegistry.ALERT_TYPE,
                "CitizenAlertService",
                PORT,
                "Issues flood alerts and emergency broadcasts to citizens"
        );

        // Start the gRPC server
        server = ServerBuilder.forPort(PORT)
                .addService(new CitizenAlertServiceImpl())
                .build()
                .start();

        System.out.println("[Alert] Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Alert] Shutting down...");
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
        CitizenAlertServer alertServer = new CitizenAlertServer();
        alertServer.start();
        alertServer.blockUntilShutdown();
    }
}
