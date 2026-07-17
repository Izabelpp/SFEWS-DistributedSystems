package com.sfews.drainage;

import com.sfews.drainage.DrainageControlProto.BatchGateResult;
import com.sfews.drainage.DrainageControlProto.DrainageAck;
import com.sfews.drainage.DrainageControlProto.DrainageCommand;
import com.sfews.drainage.DrainageControlProto.GateControlRequest;
import com.sfews.drainage.DrainageControlProto.GateControlResponse;
import com.sfews.drainage.DrainageControlProto.PumpRequest;
import com.sfews.drainage.DrainageControlProto.PumpStatusUpdate;
import io.grpc.stub.StreamObserver;
import java.util.Random;

/**
 * Implementation of the Drainage Control Service.
 * Handles all 4 gRPC communication types:
 * - Unary: SetDrainageGate
 * - Server Streaming: StreamPumpStatus
 * - Client Streaming: BatchUpdateGates
 * - Bidirectional Streaming: DrainageCoordination
 */
public class DrainageServiceImpl extends DrainageControlServiceGrpc.DrainageControlServiceImplBase {

    private final Random random = new Random();

    // -------------------------------------------------------------------------
    // UNARY RPC - SetDrainageGate
    // Client sends one gate command, server confirms execution
    // -------------------------------------------------------------------------
    @Override
    public void setDrainageGate(
            GateControlRequest request,
            StreamObserver<GateControlResponse> responseObserver) {

        System.out.println("[Drainage] Gate command received: " + request.getGateId()
                + " -> " + request.getAction());

        // Validate the open percentage (must be 0-100)
        boolean valid = request.getOpenPercentage() >= 0 && request.getOpenPercentage() <= 100;
        String message = valid
                ? "Gate " + request.getGateId() + " set to " + request.getAction()
                + " (" + request.getOpenPercentage() + "%)"
                : "Invalid percentage value. Must be between 0 and 100.";

        GateControlResponse response = GateControlResponse.newBuilder()
                .setGateId(request.getGateId())
                .setSuccess(valid)
                .setStatusMessage(message)
                .setTimestamp(java.time.Instant.now().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("[Drainage] Gate response sent: " + message);
    }

    // -------------------------------------------------------------------------
    // SERVER STREAMING RPC - StreamPumpStatus
    // Client requests a station, server streams live pump updates
    // -------------------------------------------------------------------------
    @Override
    public void streamPumpStatus(
            PumpRequest request,
            StreamObserver<PumpStatusUpdate> responseObserver) {

        System.out.println("[Drainage] Pump stream started for station: " + request.getStationId());

        // Stream 10 pump status updates every 2 seconds
        for (int i = 0; i < 10; i++) {
            float flowRate = 50 + random.nextFloat() * 200;
            float capacity = 20 + random.nextFloat() * 80;
            String status = capacity > 90 ? "FAULT" : "RUNNING";

            PumpStatusUpdate update = PumpStatusUpdate.newBuilder()
                    .setStationId(request.getStationId())
                    .setFlowRateLitresPerSec(flowRate)
                    .setCapacityPercent(capacity)
                    .setOperationalStatus(status)
                    .build();

            responseObserver.onNext(update);
            System.out.println("[Drainage] Pump update " + (i + 1) + ": "
                    + flowRate + " L/s, " + status);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        responseObserver.onCompleted();
        System.out.println("[Drainage] Pump stream completed.");
    }

    // -------------------------------------------------------------------------
    // CLIENT STREAMING RPC - BatchUpdateGates
    // Client streams multiple gate commands, server returns one summary
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<GateControlRequest> batchUpdateGates(
            StreamObserver<BatchGateResult> responseObserver) {

        System.out.println("[Drainage] Batch gate update stream started.");

        return new StreamObserver<GateControlRequest>() {

            int total = 0;
            int successful = 0;
            int failed = 0;
            java.util.List<String> failures = new java.util.ArrayList<>();

            @Override
            public void onNext(GateControlRequest request) {
                total++;
                // Validate each gate command
                if (request.getGateId().isEmpty()) {
                    failed++;
                    failures.add("Gate ID missing for command #" + total);
                    System.out.println("[Drainage] Gate command #" + total + " FAILED: missing ID");
                } else {
                    successful++;
                    System.out.println("[Drainage] Gate " + request.getGateId()
                            + " -> " + request.getAction() + " OK");
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[Drainage] Batch stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                BatchGateResult result = BatchGateResult.newBuilder()
                        .setTotalCommands(total)
                        .setSuccessful(successful)
                        .setFailed(failed)
                        .addAllFailureReasons(failures)
                        .build();

                responseObserver.onNext(result);
                responseObserver.onCompleted();

                System.out.println("[Drainage] Batch done. Total: " + total
                        + ", OK: " + successful + ", Failed: " + failed);
            }
        };
    }

    // -------------------------------------------------------------------------
    // BIDIRECTIONAL STREAMING RPC - DrainageCoordination
    // Client sends commands, server sends acknowledgements in real time
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<DrainageCommand> drainageCoordination(
            StreamObserver<DrainageAck> responseObserver) {

        System.out.println("[Drainage] Bidirectional coordination stream started.");

        return new StreamObserver<DrainageCommand>() {

            @Override
            public void onNext(DrainageCommand command) {
                System.out.println("[Drainage] Bidi command: gate " + command.getGateId()
                        + " -> " + command.getAction());

                // Simulate processing and send acknowledgement
                String currentStatus = command.getAction().equals("OPEN")
                        ? "OPEN (" + command.getValue() + "%)"
                        : command.getAction();

                DrainageAck ack = DrainageAck.newBuilder()
                        .setGateId(command.getGateId())
                        .setAccepted(true)
                        .setCurrentStatus(currentStatus)
                        .build();

                responseObserver.onNext(ack);
                System.out.println("[Drainage] Bidi ack sent for gate: " + command.getGateId());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[Drainage] Bidi error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                System.out.println("[Drainage] Bidirectional stream completed.");
            }
        };
    }
}