/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sfews;

/**
 *
 * @author izabel
 */

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles jmDNS service registration and discovery.
 * Each gRPC service registers itself on startup so the GUI
 * can find it automatically without hard-coded IP addresses.
 */
public class ServiceRegistry {

    // Service type names used for registration and discovery
    public static final String WATER_LEVEL_TYPE = "_waterlevel._tcp.local.";
    public static final String DRAINAGE_TYPE    = "_drainage._tcp.local.";
    public static final String ALERT_TYPE       = "_citizenalert._tcp.local.";

    private JmDNS jmdns;

    /**
     * Initialises jmDNS on the local network interface.
     */
    public void start() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
        System.out.println("[jmDNS] Started on: " + InetAddress.getLocalHost());
    }

    /**
     * Registers a gRPC service so it can be discovered by the GUI client.
     *
     * @param serviceType  The jmDNS service type (e.g. "_waterlevel._tcp.local.")
     * @param serviceName  A human-readable name (e.g. "WaterLevelMonitoringService")
     * @param port         The port the gRPC server is listening on
     * @param description  A short description of the service
     */
    public void registerService(String serviceType, String serviceName, int port, String description)
            throws IOException {

        Map<String, String> props = new HashMap<>();
        props.put("description", description);

        ServiceInfo serviceInfo = ServiceInfo.create(
                serviceType,
                serviceName,
                port,
                0, 0,
                props
        );

        jmdns.registerService(serviceInfo);
        System.out.println("[jmDNS] Registered: " + serviceName + " on port " + port);
    }

    /**
     * Discovers services of a given type on the local network.
     * Calls the provided listener when a service is found or removed.
     *
     * @param serviceType  The jmDNS service type to browse for
     * @param listener     Callback that handles discovered services
     */
    public void discoverServices(String serviceType, ServiceListener listener) {
        jmdns.addServiceListener(serviceType, listener);
        System.out.println("[jmDNS] Listening for: " + serviceType);
    }

    /**
     * Stops jmDNS and unregisters all services.
     */
    public void stop() {
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
                System.out.println("[jmDNS] Stopped and all services unregistered.");
            } catch (IOException e) {
                System.err.println("[jmDNS] Error stopping: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the underlying JmDNS instance (used by the GUI to resolve services).
     */
    public JmDNS getJmDNS() {
        return jmdns;
    }
}
