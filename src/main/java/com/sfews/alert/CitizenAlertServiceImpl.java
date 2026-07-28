package com.sfews.alert;

/**
 *
 * @author izabel
 */

import com.sfews.alert.CitizenAlertProto.ActiveAlert;
import com.sfews.alert.CitizenAlertProto.AlertRequest;
import com.sfews.alert.CitizenAlertProto.AlertResponse;
import com.sfews.alert.CitizenAlertProto.AlertSubscription;
import com.sfews.alert.CitizenAlertProto.BroadcastAck;
import com.sfews.alert.CitizenAlertProto.BroadcastMessage;
import com.sfews.alert.CitizenAlertProto.CitizenReport;
import com.sfews.alert.CitizenAlertProto.ReportSummary;
import io.grpc.stub.StreamObserver;
import java.util.Random;
import java.util.UUID;


public class CitizenAlertServiceImpl extends CitizenAlertServiceGrpc.CitizenAlertServiceImplBase {

    private final Random random = new Random();

    // -------------------------------------------------------------------------
    // UNARY RPC - IssueAlert
    // Operator sends one alert request, server confirms and broadcasts
    // -------------------------------------------------------------------------
    @Override
    public void issueAlert(
            AlertRequest request,
            StreamObserver<AlertResponse> responseObserver) {

        System.out.println("[Alert] Alert received for zone: " + request.getZoneId()
                + " | Severity: " + request.getSeverity());

        // Validate that message and zone are not empty
        boolean valid = !request.getZoneId().isEmpty() && !request.getMessage().isEmpty();

        String alertId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int recipients = valid ? 100 + random.nextInt(900) : 0;

        AlertResponse response = AlertResponse.newBuilder()
                .setAlertId(alertId)
                .setAcknowledged(valid)
                .setRecipientsNotified(recipients)
                .setTimestamp(java.time.Instant.now().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("[Alert] Alert " + alertId + " sent to " + recipients + " recipients.");
    }

    // -------------------------------------------------------------------------
    // SERVER STREAMING RPC - StreamActiveAlerts
    // Client subscribes to zones, server pushes alerts in real time
    // -------------------------------------------------------------------------
    @Override
    public void streamActiveAlerts(
            AlertSubscription request,
            StreamObserver<ActiveAlert> responseObserver) {

        System.out.println("[Alert] Subscription started for: " + request.getSubscriberId()
                + " | Zones: " + request.getZonesOfInterestList());

        String[] severities = {"INFO", "WARNING", "DANGER", "EVACUATION"};

        // Stream 8 simulated alerts every 2 seconds
        for (int i = 0; i < 8; i++) {
            String zone = request.getZonesOfInterestCount() > 0
                    ? request.getZonesOfInterest(random.nextInt(request.getZonesOfInterestCount()))
                    : "ZONE-" + (i + 1);

            String severity = severities[random.nextInt(severities.length)];

            ActiveAlert alert = ActiveAlert.newBuilder()
                    .setAlertId(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .setZoneId(zone)
                    .setSeverity(severity)
                    .setMessage("Flood risk detected in " + zone + ". " + severity + " level issued.")
                    .setTimestamp(java.time.Instant.now().toString())
                    .build();

            responseObserver.onNext(alert);
            System.out.println("[Alert] Streamed alert: " + severity + " for " + zone);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        responseObserver.onCompleted();
        System.out.println("[Alert] Alert stream completed.");
    }

    // -------------------------------------------------------------------------
    // CLIENT STREAMING RPC - SubmitCitizenReports
    // Citizens stream reports, server returns a consolidated summary
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<CitizenReport> submitCitizenReports(
            StreamObserver<ReportSummary> responseObserver) {

        System.out.println("[Alert] Citizen report stream started.");

        return new StreamObserver<CitizenReport>() {

            int totalReports = 0;
            java.util.List<String> flaggedLocations = new java.util.ArrayList<>();

            @Override
            public void onNext(CitizenReport report) {
                totalReports++;
                System.out.println("[Alert] Report #" + totalReports + " from "
                        + report.getReportId() + " at " + report.getLocation()
                        + " | Severity: " + report.getSeverityEstimate());

                // Flag locations reported as SEVERE
                if (report.getSeverityEstimate().equalsIgnoreCase("SEVERE")) {
                    flaggedLocations.add(report.getLocation());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[Alert] Report stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                ReportSummary summary = ReportSummary.newBuilder()
                        .setReportsReceived(totalReports)
                        .addAllFlaggedLocations(flaggedLocations)
                        .build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();

                System.out.println("[Alert] Reports done. Total: " + totalReports
                        + ", Flagged: " + flaggedLocations.size());
            }
        };
    }

    // -------------------------------------------------------------------------
    // BIDIRECTIONAL STREAMING RPC - EmergencyBroadcast
    // Two-way emergency communication channel
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<BroadcastMessage> emergencyBroadcast(
            StreamObserver<BroadcastAck> responseObserver) {

        System.out.println("[Alert] Emergency broadcast channel opened.");

        return new StreamObserver<BroadcastMessage>() {

            int messageCount = 0;

            @Override
            public void onNext(BroadcastMessage message) {
                messageCount++;
                System.out.println("[Alert] Broadcast #" + messageCount
                        + " to " + message.getRecipientGroup()
                        + " | Priority: " + message.getPriority()
                        + " | Message: " + message.getMessage());

                // Simulate delivery to recipients
                int delivered = message.getPriority().equals("CRITICAL") ? 500 : 100 + random.nextInt(400);
                String status = delivered > 0 ? "DELIVERED" : "FAILED";

                BroadcastAck ack = BroadcastAck.newBuilder()
                        .setMessageId(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .setDeliveredCount(delivered)
                        .setStatus(status)
                        .build();

                responseObserver.onNext(ack);
                System.out.println("[Alert] Broadcast ack: " + delivered + " delivered - " + status);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[Alert] Broadcast error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                System.out.println("[Alert] Emergency broadcast channel closed.");
            }
        };
    }
}
