package com.sfews.client;

import com.sfews.ServiceRegistry;
import com.sfews.alert.CitizenAlertProto.*;
import com.sfews.alert.CitizenAlertServiceGrpc;
import com.sfews.drainage.DrainageControlProto.*;
import com.sfews.drainage.DrainageControlServiceGrpc;
import com.sfews.waterLevel.WaterLevelProto.*;
import com.sfews.waterLevel.WaterLevelMonitoringServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.swing.*;

/**
 * Main GUI client for the Smart Flood Early Warning System.
 * Discovers all 3 gRPC services via jmDNS and provides
 * a control dashboard for operators.
 */
public class SFEWSGui extends JFrame {

    // gRPC channels and stubs
    private ManagedChannel waterChannel;
    private ManagedChannel drainageChannel;
    private ManagedChannel alertChannel;

    private WaterLevelMonitoringServiceGrpc.WaterLevelMonitoringServiceBlockingStub waterStub;
    private WaterLevelMonitoringServiceGrpc.WaterLevelMonitoringServiceStub waterAsyncStub;
    private DrainageControlServiceGrpc.DrainageControlServiceBlockingStub drainageStub;
    private DrainageControlServiceGrpc.DrainageControlServiceStub drainageAsyncStub;
    private CitizenAlertServiceGrpc.CitizenAlertServiceBlockingStub alertStub;
    private CitizenAlertServiceGrpc.CitizenAlertServiceStub alertAsyncStub;

    // Service discovery
    private ServiceRegistry registry;

    // Status labels for discovered services
    private JLabel waterStatusLabel;
    private JLabel drainageStatusLabel;
    private JLabel alertStatusLabel;

    // Output areas
    private JTextArea waterOutput;
    private JTextArea drainageOutput;
    private JTextArea alertOutput;
    private JTextArea discoveryLog;

    public SFEWSGui() {
        setTitle("SFEWS - Smart Flood Early Warning System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        buildUI();
    }

    // -------------------------------------------------------------------------
    // UI CONSTRUCTION
    // -------------------------------------------------------------------------
    private void buildUI() {
        setLayout(new BorderLayout());

        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(new Color(30, 90, 150));
        JLabel title = new JLabel("Smart Flood Early Warning System — City Control Dashboard");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 16));
        titlePanel.add(title);
        add(titlePanel, BorderLayout.NORTH);

        // Main tabbed pane
        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Service Discovery", buildDiscoveryPanel());
        tabs.addTab("Water Level Monitor", buildWaterPanel());
        tabs.addTab("Drainage Control", buildDrainagePanel());
        tabs.addTab("Citizen Alerts", buildAlertPanel());

        add(tabs, BorderLayout.CENTER);

        // Bottom status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBackground(new Color(240, 240, 240));
        waterStatusLabel  = new JLabel("Water Level: Not connected");
        drainageStatusLabel = new JLabel("  |  Drainage: Not connected");
        alertStatusLabel  = new JLabel("  |  Alerts: Not connected");
        statusBar.add(waterStatusLabel);
        statusBar.add(drainageStatusLabel);
        statusBar.add(alertStatusLabel);
        add(statusBar, BorderLayout.SOUTH);
    }

    // -------------------------------------------------------------------------
    // DISCOVERY PANEL
    // -------------------------------------------------------------------------
    private JPanel buildDiscoveryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel info = new JLabel("Click 'Discover Services' to find all SFEWS services on the local network via jmDNS.");
        panel.add(info, BorderLayout.NORTH);

        discoveryLog = new JTextArea();
        discoveryLog.setEditable(false);
        discoveryLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(discoveryLog), BorderLayout.CENTER);

        JButton discoverBtn = new JButton("Discover Services");
        discoverBtn.setBackground(new Color(30, 90, 150));
        discoverBtn.setForeground(Color.WHITE);
        discoverBtn.setFont(new Font("Arial", Font.BOLD, 13));
        discoverBtn.addActionListener(e -> discoverServices());
        panel.add(discoverBtn, BorderLayout.SOUTH);

        return panel;
    }

    // -------------------------------------------------------------------------
    // WATER LEVEL PANEL
    // -------------------------------------------------------------------------
    private JPanel buildWaterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel locLabel = new JLabel("Location ID:");
        JTextField locField = new JTextField("LOC-001", 10);

        JButton unaryBtn   = new JButton("Get Water Level (Unary)");
        JButton streamBtn  = new JButton("Stream Levels (Server Stream)");
        JButton batchBtn   = new JButton("Report Sensors (Client Stream)");
        JButton bidiBtn    = new JButton("Monitor Conditions (Bidi)");

        controls.add(locLabel);
        controls.add(locField);
        controls.add(unaryBtn);
        controls.add(streamBtn);
        controls.add(batchBtn);
        controls.add(bidiBtn);
        panel.add(controls, BorderLayout.NORTH);

        waterOutput = new JTextArea();
        waterOutput.setEditable(false);
        waterOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(waterOutput), BorderLayout.CENTER);

        // Button actions
        unaryBtn.addActionListener(e -> getWaterLevel(locField.getText()));
        streamBtn.addActionListener(e -> streamWaterLevels(locField.getText()));
        batchBtn.addActionListener(e -> reportSensors());
        bidiBtn.addActionListener(e -> monitorFloodConditions(locField.getText()));

        return panel;
    }

    // -------------------------------------------------------------------------
    // DRAINAGE PANEL
    // -------------------------------------------------------------------------
    private JPanel buildDrainagePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel gateLabel = new JLabel("Gate ID:");
        JTextField gateField = new JTextField("GATE-001", 8);
        JLabel actionLabel = new JLabel("Action:");
        JComboBox<String> actionBox = new JComboBox<>(new String[]{"OPEN", "CLOSE", "PARTIAL"});
        JLabel pctLabel = new JLabel("Open %:");
        JTextField pctField = new JTextField("75", 4);

        JButton unaryBtn  = new JButton("Set Gate (Unary)");
        JButton streamBtn = new JButton("Stream Pump (Server Stream)");
        JButton batchBtn  = new JButton("Batch Gates (Client Stream)");
        JButton bidiBtn   = new JButton("Coordinate (Bidi)");

        controls.add(gateLabel); controls.add(gateField);
        controls.add(actionLabel); controls.add(actionBox);
        controls.add(pctLabel); controls.add(pctField);
        controls.add(unaryBtn); controls.add(streamBtn);
        controls.add(batchBtn); controls.add(bidiBtn);
        panel.add(controls, BorderLayout.NORTH);

        drainageOutput = new JTextArea();
        drainageOutput.setEditable(false);
        drainageOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(drainageOutput), BorderLayout.CENTER);

        unaryBtn.addActionListener(e -> setDrainageGate(
                gateField.getText(),
                (String) actionBox.getSelectedItem(),
                Integer.parseInt(pctField.getText())
        ));
        streamBtn.addActionListener(e -> streamPumpStatus(gateField.getText()));
        batchBtn.addActionListener(e -> batchUpdateGates());
        bidiBtn.addActionListener(e -> drainageCoordination(gateField.getText()));

        return panel;
    }

    // -------------------------------------------------------------------------
    // ALERT PANEL
    // -------------------------------------------------------------------------
    private JPanel buildAlertPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel zoneLabel = new JLabel("Zone ID:");
        JTextField zoneField = new JTextField("ZONE-A", 8);
        JLabel sevLabel = new JLabel("Severity:");
        JComboBox<String> sevBox = new JComboBox<>(new String[]{"INFO", "WARNING", "DANGER", "EVACUATION"});

        JButton unaryBtn  = new JButton("Issue Alert (Unary)");
        JButton streamBtn = new JButton("Stream Alerts (Server Stream)");
        JButton reportsBtn = new JButton("Submit Reports (Client Stream)");
        JButton broadcastBtn = new JButton("Emergency Broadcast (Bidi)");

        controls.add(zoneLabel); controls.add(zoneField);
        controls.add(sevLabel); controls.add(sevBox);
        controls.add(unaryBtn); controls.add(streamBtn);
        controls.add(reportsBtn); controls.add(broadcastBtn);
        panel.add(controls, BorderLayout.NORTH);

        alertOutput = new JTextArea();
        alertOutput.setEditable(false);
        alertOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(alertOutput), BorderLayout.CENTER);

        unaryBtn.addActionListener(e -> issueAlert(zoneField.getText(), (String) sevBox.getSelectedItem()));
        streamBtn.addActionListener(e -> streamActiveAlerts(zoneField.getText()));
        reportsBtn.addActionListener(e -> submitCitizenReports());
        broadcastBtn.addActionListener(e -> emergencyBroadcast(zoneField.getText()));

        return panel;
    }

    // -------------------------------------------------------------------------
    // SERVICE DISCOVERY via jmDNS
    // -------------------------------------------------------------------------
    private void discoverServices() {
        discoveryLog.setText("");
        log(discoveryLog, "Starting jmDNS service discovery...\n");

        new Thread(() -> {
            try {
                registry = new ServiceRegistry();
                registry.start();

                // Discover Water Level Service
                registry.discoverServices(ServiceRegistry.WATER_LEVEL_TYPE, new ServiceListener() {
                    public void serviceAdded(ServiceEvent e) {
                        log(discoveryLog, "[Found] " + e.getName());
                    }
                    public void serviceRemoved(ServiceEvent e) {
                        log(discoveryLog, "[Removed] " + e.getName());
                    }
                    public void serviceResolved(ServiceEvent e) {
                        String host = e.getInfo().getHostAddresses()[0];
                        int port = e.getInfo().getPort();
                        log(discoveryLog, "[Connected] WaterLevelMonitoringService at " + host + ":" + port);
                        connectWaterLevel(host, port);
                    }
                });

                // Discover Drainage Service
                registry.discoverServices(ServiceRegistry.DRAINAGE_TYPE, new ServiceListener() {
                    public void serviceAdded(ServiceEvent e) {
                        log(discoveryLog, "[Found] " + e.getName());
                    }
                    public void serviceRemoved(ServiceEvent e) {
                        log(discoveryLog, "[Removed] " + e.getName());
                    }
                    public void serviceResolved(ServiceEvent e) {
                        String host = e.getInfo().getHostAddresses()[0];
                        int port = e.getInfo().getPort();
                        log(discoveryLog, "[Connected] DrainageControlService at " + host + ":" + port);
                        connectDrainage(host, port);
                    }
                });

                // Discover Alert Service
                registry.discoverServices(ServiceRegistry.ALERT_TYPE, new ServiceListener() {
                    public void serviceAdded(ServiceEvent e) {
                        log(discoveryLog, "[Found] " + e.getName());
                    }
                    public void serviceRemoved(ServiceEvent e) {
                        log(discoveryLog, "[Removed] " + e.getName());
                    }
                    public void serviceResolved(ServiceEvent e) {
                        String host = e.getInfo().getHostAddresses()[0];
                        int port = e.getInfo().getPort();
                        log(discoveryLog, "[Connected] CitizenAlertService at " + host + ":" + port);
                        connectAlert(host, port);
                    }
                });

                log(discoveryLog, "\nListening for services... (make sure all 3 servers are running)");
                Thread.sleep(5000);
                log(discoveryLog, "\nDiscovery complete.");

            } catch (IOException | InterruptedException ex) {
                log(discoveryLog, "Discovery error: " + ex.getMessage());
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // CONNECT TO SERVICES
    // -------------------------------------------------------------------------
    private void connectWaterLevel(String host, int port) {
        // Add auth token via Metadata
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer sfews-token-waterlevel");

        waterChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        waterStub = WaterLevelMonitoringServiceGrpc.newBlockingStub(waterChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-waterlevel"));
        waterAsyncStub = WaterLevelMonitoringServiceGrpc.newStub(waterChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-waterlevel"));

        SwingUtilities.invokeLater(() ->
                waterStatusLabel.setText("Water Level: Connected (" + host + ":" + port + ")"));
    }

    private void connectDrainage(String host, int port) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer sfews-token-drainage");

        drainageChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        drainageStub = DrainageControlServiceGrpc.newBlockingStub(drainageChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-drainage"));
        drainageAsyncStub = DrainageControlServiceGrpc.newStub(drainageChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-drainage"));

        SwingUtilities.invokeLater(() ->
                drainageStatusLabel.setText("  |  Drainage: Connected (" + host + ":" + port + ")"));
    }

    private void connectAlert(String host, int port) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer sfews-token-alert");

        alertChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        alertStub = CitizenAlertServiceGrpc.newBlockingStub(alertChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-alert"));
        alertAsyncStub = CitizenAlertServiceGrpc.newStub(alertChannel)
        .withCallCredentials(new TokenCredentials("sfews-token-alert"));

        SwingUtilities.invokeLater(() ->
                alertStatusLabel.setText("  |  Alerts: Connected (" + host + ":" + port + ")"));
    }

    // -------------------------------------------------------------------------
    // WATER LEVEL RPC CALLS
    // -------------------------------------------------------------------------
    private void getWaterLevel(String locationId) {
        if (waterStub == null) { log(waterOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                LocationRequest request = LocationRequest.newBuilder()
                        .setLocationId(locationId).setZoneName("City Centre").build();
                WaterLevelResponse response = waterStub
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .getCurrentWaterLevel(request);
                log(waterOutput, "[Unary] Location: " + response.getLocationId()
                        + " | Level: " + String.format("%.1f", response.getWaterLevelCm()) + "cm"
                        + " | Rainfall: " + String.format("%.1f", response.getRainfallMmPerHour()) + "mm/h"
                        + " | Risk: " + response.getRiskLevel()
                        + " | Time: " + response.getTimestamp());
            } catch (Exception e) {
                log(waterOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void streamWaterLevels(String locationId) {
        if (waterStub == null) { log(waterOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                LocationRequest request = LocationRequest.newBuilder()
                        .setLocationId(locationId).setZoneName("City Centre").build();
                log(waterOutput, "[Server Stream] Starting live feed for " + locationId + "...");
                waterStub.streamWaterLevels(request).forEachRemaining(response ->
                        log(waterOutput, "[Stream] Level: " + String.format("%.1f", response.getWaterLevelCm())
                                + "cm | Risk: " + response.getRiskLevel()
                                + " | " + response.getTimestamp())
                );
                log(waterOutput, "[Server Stream] Complete.");
            } catch (Exception e) {
                log(waterOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void reportSensors() {
        if (waterAsyncStub == null) { log(waterOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(waterOutput, "[Client Stream] Sending sensor batch...");
            StreamObserver<BatchSummary> responseObserver = new StreamObserver<BatchSummary>() {
                public void onNext(BatchSummary s) {
                    log(waterOutput, "[Batch Result] Total: " + s.getTotalSensors()
                            + " | Critical: " + s.getCriticalCount()
                            + " | Avg: " + String.format("%.1f", s.getAverageLevel()) + "cm"
                            + " | Status: " + s.getOverallStatus());
                }
                public void onError(Throwable t) { log(waterOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(waterOutput, "[Client Stream] Complete."); }
            };

            StreamObserver<SensorReading> requestObserver = waterAsyncStub.reportMultipleSensors(responseObserver);
            String[] sensors = {"S-001", "S-002", "S-003", "S-004", "S-005"};
            for (String id : sensors) {
                float level = 20 + (float)(Math.random() * 80);
                requestObserver.onNext(SensorReading.newBuilder()
                        .setSensorId(id).setWaterLevelCm(level)
                        .setLatitude(53.3f).setLongitude(-6.2f).build());
                log(waterOutput, "[Sent] Sensor " + id + ": " + String.format("%.1f", level) + "cm");
            }
            requestObserver.onCompleted();
        }).start();
    }

    private void monitorFloodConditions(String locationId) {
        if (waterAsyncStub == null) { log(waterOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(waterOutput, "[Bidi] Starting flood monitoring for " + locationId + "...");
            StreamObserver<WaterLevelResponse> responseObserver = new StreamObserver<WaterLevelResponse>() {
                public void onNext(WaterLevelResponse r) {
                    log(waterOutput, "[Bidi] " + r.getLocationId()
                            + " | Level: " + String.format("%.1f", r.getWaterLevelCm()) + "cm"
                            + " | Risk: " + r.getRiskLevel());
                }
                public void onError(Throwable t) { log(waterOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(waterOutput, "[Bidi] Complete."); }
            };

            StreamObserver<LocationRequest> requestObserver = waterAsyncStub.monitorFloodConditions(responseObserver);
            String[] locations = {locationId, "LOC-002", "LOC-003"};
            for (String loc : locations) {
                requestObserver.onNext(LocationRequest.newBuilder()
                        .setLocationId(loc).setZoneName("Zone " + loc).build());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            requestObserver.onCompleted();
        }).start();
    }

    // -------------------------------------------------------------------------
    // DRAINAGE RPC CALLS
    // -------------------------------------------------------------------------
    private void setDrainageGate(String gateId, String action, int pct) {
        if (drainageStub == null) { log(drainageOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                GateControlRequest request = GateControlRequest.newBuilder()
                        .setGateId(gateId).setAction(action)
                        .setOpenPercentage(pct).setOperatorId("GUI-OPERATOR").build();
                GateControlResponse response = drainageStub
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .setDrainageGate(request);
                log(drainageOutput, "[Unary] Gate: " + response.getGateId()
                        + " | Success: " + response.getSuccess()
                        + " | " + response.getStatusMessage()
                        + " | " + response.getTimestamp());
            } catch (Exception e) {
                log(drainageOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void streamPumpStatus(String stationId) {
        if (drainageStub == null) { log(drainageOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                PumpRequest request = PumpRequest.newBuilder().setStationId(stationId).build();
                log(drainageOutput, "[Server Stream] Pump status for " + stationId + "...");
                drainageStub.streamPumpStatus(request).forEachRemaining(u ->
                        log(drainageOutput, "[Pump] Station: " + u.getStationId()
                                + " | Flow: " + String.format("%.1f", u.getFlowRateLitresPerSec()) + " L/s"
                                + " | Capacity: " + String.format("%.1f", u.getCapacityPercent()) + "%"
                                + " | Status: " + u.getOperationalStatus())
                );
                log(drainageOutput, "[Server Stream] Complete.");
            } catch (Exception e) {
                log(drainageOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void batchUpdateGates() {
        if (drainageAsyncStub == null) { log(drainageOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(drainageOutput, "[Client Stream] Sending batch gate commands...");
            StreamObserver<BatchGateResult> responseObserver = new StreamObserver<BatchGateResult>() {
                public void onNext(BatchGateResult r) {
                    log(drainageOutput, "[Batch] Total: " + r.getTotalCommands()
                            + " | OK: " + r.getSuccessful() + " | Failed: " + r.getFailed());
                }
                public void onError(Throwable t) { log(drainageOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(drainageOutput, "[Client Stream] Complete."); }
            };

            StreamObserver<GateControlRequest> requestObserver = drainageAsyncStub.batchUpdateGates(responseObserver);
            String[] gates = {"GATE-001", "GATE-002", "GATE-003"};
            for (String g : gates) {
                requestObserver.onNext(GateControlRequest.newBuilder()
                        .setGateId(g).setAction("OPEN").setOpenPercentage(80)
                        .setOperatorId("GUI-BATCH").build());
                log(drainageOutput, "[Sent] Command for " + g);
            }
            requestObserver.onCompleted();
        }).start();
    }

    private void drainageCoordination(String gateId) {
        if (drainageAsyncStub == null) { log(drainageOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(drainageOutput, "[Bidi] Drainage coordination started...");
            StreamObserver<DrainageAck> responseObserver = new StreamObserver<DrainageAck>() {
                public void onNext(DrainageAck a) {
                    log(drainageOutput, "[Bidi Ack] Gate: " + a.getGateId()
                            + " | Accepted: " + a.getAccepted()
                            + " | Status: " + a.getCurrentStatus());
                }
                public void onError(Throwable t) { log(drainageOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(drainageOutput, "[Bidi] Complete."); }
            };

            StreamObserver<DrainageCommand> requestObserver = drainageAsyncStub.drainageCoordination(responseObserver);
            requestObserver.onNext(DrainageCommand.newBuilder().setGateId(gateId).setAction("OPEN").setValue(75).build());
            requestObserver.onNext(DrainageCommand.newBuilder().setGateId("GATE-002").setAction("PARTIAL").setValue(50).build());
            requestObserver.onNext(DrainageCommand.newBuilder().setGateId("GATE-003").setAction("CLOSE").setValue(0).build());
            requestObserver.onCompleted();
        }).start();
    }

    // -------------------------------------------------------------------------
    // ALERT RPC CALLS
    // -------------------------------------------------------------------------
    private void issueAlert(String zoneId, String severity) {
        if (alertStub == null) { log(alertOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                AlertRequest request = AlertRequest.newBuilder()
                        .setZoneId(zoneId).setSeverity(severity)
                        .setMessage("Flood risk detected in " + zoneId)
                        .addAffectedAreas(zoneId).setIssuedBy("GUI-OPERATOR").build();
                AlertResponse response = alertStub
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .issueAlert(request);
                log(alertOutput, "[Unary] Alert ID: " + response.getAlertId()
                        + " | Acknowledged: " + response.getAcknowledged()
                        + " | Recipients: " + response.getRecipientsNotified()
                        + " | " + response.getTimestamp());
            } catch (Exception e) {
                log(alertOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void streamActiveAlerts(String zoneId) {
        if (alertStub == null) { log(alertOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            try {
                AlertSubscription sub = AlertSubscription.newBuilder()
                        .setSubscriberId("GUI-DASHBOARD").addZonesOfInterest(zoneId).build();
                log(alertOutput, "[Server Stream] Subscribing to alerts for " + zoneId + "...");
                alertStub.streamActiveAlerts(sub).forEachRemaining(a ->
                        log(alertOutput, "[Alert] " + a.getSeverity() + " in " + a.getZoneId()
                                + ": " + a.getMessage() + " | " + a.getTimestamp())
                );
                log(alertOutput, "[Server Stream] Complete.");
            } catch (Exception e) {
                log(alertOutput, "[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void submitCitizenReports() {
        if (alertAsyncStub == null) { log(alertOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(alertOutput, "[Client Stream] Submitting citizen reports...");
            StreamObserver<ReportSummary> responseObserver = new StreamObserver<ReportSummary>() {
                public void onNext(ReportSummary s) {
                    log(alertOutput, "[Summary] Reports: " + s.getReportsReceived()
                            + " | Flagged: " + s.getFlaggedLocationsList());
                }
                public void onError(Throwable t) { log(alertOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(alertOutput, "[Client Stream] Complete."); }
            };

            StreamObserver<CitizenReport> requestObserver = alertAsyncStub.submitCitizenReports(responseObserver);
            String[][] reports = {
                {"R-001", "North Quay", "Water rising fast", "SEVERE"},
                {"R-002", "Main Street", "Minor flooding", "MODERATE"},
                {"R-003", "River Walk", "Road flooded", "SEVERE"}
            };
            for (String[] r : reports) {
                requestObserver.onNext(CitizenReport.newBuilder()
                        .setReportId(r[0]).setLocation(r[1])
                        .setDescription(r[2]).setSeverityEstimate(r[3]).build());
                log(alertOutput, "[Sent] Report " + r[0] + " from " + r[1]);
            }
            requestObserver.onCompleted();
        }).start();
    }

    private void emergencyBroadcast(String zoneId) {
        if (alertAsyncStub == null) { log(alertOutput, "Not connected. Run discovery first."); return; }
        new Thread(() -> {
            log(alertOutput, "[Bidi] Emergency broadcast started...");
            StreamObserver<BroadcastAck> responseObserver = new StreamObserver<BroadcastAck>() {
                public void onNext(BroadcastAck a) {
                    log(alertOutput, "[Bidi Ack] ID: " + a.getMessageId()
                            + " | Delivered: " + a.getDeliveredCount()
                            + " | Status: " + a.getStatus());
                }
                public void onError(Throwable t) { log(alertOutput, "[ERROR] " + t.getMessage()); }
                public void onCompleted() { log(alertOutput, "[Bidi] Complete."); }
            };

            StreamObserver<BroadcastMessage> requestObserver = alertAsyncStub.emergencyBroadcast(responseObserver);
            requestObserver.onNext(BroadcastMessage.newBuilder()
                    .setChannelId("CH-001").setRecipientGroup("CITIZENS")
                    .setMessage("Flood warning for " + zoneId + ". Move to higher ground.")
                    .setPriority("CRITICAL").build());
            requestObserver.onNext(BroadcastMessage.newBuilder()
                    .setChannelId("CH-001").setRecipientGroup("EMERGENCY_SERVICES")
                    .setMessage("Deploy flood response teams to " + zoneId)
                    .setPriority("HIGH").build());
            requestObserver.onCompleted();
        }).start();
    }

    // -------------------------------------------------------------------------
    // HELPER - thread-safe logging to JTextArea
    // -------------------------------------------------------------------------
    private void log(JTextArea area, String message) {
        SwingUtilities.invokeLater(() -> {
            area.append(message + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    // -------------------------------------------------------------------------
    // MAIN
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SFEWSGui().setVisible(true);
        });
    }
}
