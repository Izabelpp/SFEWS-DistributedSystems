package com.sfews.client;

/**
 *
 * @author izabel
 */


import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.concurrent.Executor;

/**
 * Attaches an authorization token to every gRPC call as Metadata.
 * This simulates device authentication across all 3 services.
 */
public class TokenCredentials extends CallCredentials {

    private final String token;

    public TokenCredentials(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
        executor.execute(() -> {
            try {
                Metadata metadata = new Metadata();
                metadata.put(
                        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                        "Bearer " + token
                );
                applier.apply(metadata);
            } catch (Exception e) {
                applier.fail(Status.UNAUTHENTICATED.withCause(e));
            }
        });
    }
}