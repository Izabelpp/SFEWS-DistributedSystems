package com.sfews.waterlevel;

import com.sfews.waterLevel.WaterLevelMonitoringServiceGrpc;
import com.sfews.waterLevel.WaterLevelProto.LocationRequest;
import com.sfews.waterLevel.WaterLevelProto.WaterLevelResponse;
import com.sfews.waterLevel.WaterLevelProto.SensorReading;
import com.sfews.waterLevel.WaterLevelProto.BatchSummary;
import io.grpc.stub.StreamObserver;
import java.util.Random;

/**
 * Implementation of the Water Level Monitoring Service.
 * Handles all 4 gRPC communication types.
 */
public class WaterLevelServiceImpl extends WaterLevelMonitoringServiceGrpc.WaterLevelMonitoringServiceImplBase {

    private final Random random = new Random();

    // -------------------------------------------------------------------------
    // UNARY RPC - GetCurrentWaterLevel
    // -------------------------------------------------------------------------
    @Override
    public void getCurrentWaterLevel(
            LocationRequest request,
            StreamObserver<WaterLevelResponse> responseObserver) {

        System.out.println("[WaterLevel] Unary request for: " + request.getLocationId());

        float waterLevel = 20 + random.nextFloat() * 80;
        float rainfall = random.nextFloat() * 15;
        String riskLevel = getRiskLevel(waterLevel);

        WaterLevelResponse response = WaterLevelResponse.newBuilder()
                .setLocationId(request.getLocationId())
                .setWaterLevelCm(waterLevel)
                .setRainfallMmPerHour(rainfall)
                .setRiskLevel(riskLevel)
                .setTimestamp(java.time.Instant.now().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("[WaterLevel] Unary response sent. Risk: " + riskLevel);
    }

    // -------------------------------------------------------------------------
    // SERVER STREAMING RPC - StreamWaterLevels
    // -------------------------------------------------------------------------
    @Override
    public void streamWaterLevels(
            LocationRequest request,
            StreamObserver<WaterLevelResponse> responseObserver) {

        System.out.println("[WaterLevel] Server stream started for: " + request.getLocationId());

        for (int i = 0; i < 10; i++) {
            float waterLevel = 20 + random.nextFloat() * 80;
            float rainfall = random.nextFloat() * 15;
            String riskLevel = getRiskLevel(waterLevel);

            WaterLevelResponse response = WaterLevelResponse.newBuilder()
                    .setLocationId(request.getLocationId())
                    .setWaterLevelCm(waterLevel)
                    .setRainfallMmPerHour(rainfall)
                    .setRiskLevel(riskLevel)
                    .setTimestamp(java.time.Instant.now().toString())
                    .build();

            responseObserver.onNext(response);
            System.out.println("[WaterLevel] Stream reading " + (i + 1) + ": " + waterLevel + "cm");

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        responseObserver.onCompleted();
        System.out.println("[WaterLevel] Server stream completed.");
    }

    // -------------------------------------------------------------------------
    // CLIENT STREAMING RPC - ReportMultipleSensors
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<SensorReading> reportMultipleSensors(
            StreamObserver<BatchSummary> responseObserver) {

        System.out.println("[WaterLevel] Client stream started.");

        return new StreamObserver<SensorReading>() {

            int totalSensors = 0;
            int criticalCount = 0;
            float totalLevel = 0;

            @Override
            public void onNext(SensorReading reading) {
                totalSensors++;
                totalLevel += reading.getWaterLevelCm();
                if (reading.getWaterLevelCm() > 80) {
                    criticalCount++;
                }
                System.out.println("[WaterLevel] Sensor " + reading.getSensorId()
                        + ": " + reading.getWaterLevelCm() + "cm");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[WaterLevel] Client stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                float avg = totalSensors > 0 ? totalLevel / totalSensors : 0;
                String status = criticalCount > 0 ? "CRITICAL" : getRiskLevel(avg);

                BatchSummary summary = BatchSummary.newBuilder()
                        .setTotalSensors(totalSensors)
                        .setCriticalCount(criticalCount)
                        .setAverageLevel(avg)
                        .setOverallStatus(status)
                        .build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();
                System.out.println("[WaterLevel] Batch done. Total: " + totalSensors
                        + ", Critical: " + criticalCount);
            }
        };
    }

    // -------------------------------------------------------------------------
    // BIDIRECTIONAL STREAMING RPC - MonitorFloodConditions
    // -------------------------------------------------------------------------
    @Override
    public StreamObserver<LocationRequest> monitorFloodConditions(
            StreamObserver<WaterLevelResponse> responseObserver) {

        System.out.println("[WaterLevel] Bidirectional stream started.");

        return new StreamObserver<LocationRequest>() {

            @Override
            public void onNext(LocationRequest request) {
                float waterLevel = 20 + random.nextFloat() * 80;
                float rainfall = random.nextFloat() * 15;
                String riskLevel = getRiskLevel(waterLevel);

                WaterLevelResponse response = WaterLevelResponse.newBuilder()
                        .setLocationId(request.getLocationId())
                        .setWaterLevelCm(waterLevel)
                        .setRainfallMmPerHour(rainfall)
                        .setRiskLevel(riskLevel)
                        .setTimestamp(java.time.Instant.now().toString())
                        .build();

                responseObserver.onNext(response);
                System.out.println("[WaterLevel] Bidi response for " + request.getLocationId()
                        + ": " + riskLevel);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[WaterLevel] Bidi error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                System.out.println("[WaterLevel] Bidi stream completed.");
            }
        };
    }

    // -------------------------------------------------------------------------
    // HELPER - determines risk level from water level in cm
    // -------------------------------------------------------------------------
    private String getRiskLevel(float waterLevelCm) {
        if (waterLevelCm < 30) return "NORMAL";
        if (waterLevelCm < 50) return "ELEVATED";
        if (waterLevelCm < 80) return "HIGH";
        return "CRITICAL";
    }
}