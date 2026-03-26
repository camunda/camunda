/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.eventbridge.protocol.EventBridgeBatchBuilder;
import io.camunda.eventbridge.protocol.EventBridgeRecord.ContentType;
import io.camunda.eventbridge.protocol.EventBridgeRecordBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client for publishing events to EventBridge partitions.
 *
 * <p>Usage:
 *
 * <pre>
 * try (var client = new EventBridgeClient("http://gateway:8080")) {
 *   var result = client.newBatch()
 *       .addRecord(ContentType.JSON, "order-123", jsonBytes)
 *       .publish(partitionId)
 *       .join();
 *
 *   if (result.isSuccess()) {
 *     System.out.println("First: " + result.firstPosition());
 *     System.out.println("Last: " + result.lastPosition());
 *   }
 * }
 * </pre>
 */
public final class EventBridgePublishClient implements AutoCloseable {

  private final HttpClient httpClient;
  private final String gatewayBaseUrl;
  private final Duration requestTimeout;
  private final ObjectMapper objectMapper;

  public EventBridgePublishClient(final String gatewayBaseUrl) {
    this(gatewayBaseUrl, Duration.ofSeconds(10));
  }

  public EventBridgePublishClient(final String gatewayBaseUrl, final Duration requestTimeout) {
    this.gatewayBaseUrl = gatewayBaseUrl;
    this.requestTimeout = requestTimeout;
    httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    objectMapper = new ObjectMapper();
  }

  public BatchPublisher newBatch() {
    return new BatchPublisher();
  }

  public CompletableFuture<PublishResult> publish(
      final int partitionId,
      final ContentType contentType,
      final String key,
      final byte[] payload) {
    return newBatch().addRecord(contentType, key, payload).publish(partitionId);
  }

  @Override
  public void close() {
    httpClient.close();
  }

  private PublishResult parseResponse(final HttpResponse<String> response) {
    try {
      final var json = objectMapper.readTree(response.body());

      final var status = json.get("status").asText();

      if ("SUCCESS".equals(status)) {
        final List<Long> values = new ArrayList<>();
        json.get("logPositions").elements().forEachRemaining(n -> values.add(n.asLong()));
        return PublishResult.success(values);
      }

      return PublishResult.error(
          response.statusCode(),
          json.has("rejectionReason") ? json.get("rejectionReason").asText() : "UNKNOWN",
          json.has("message") ? json.get("message").asText() : "Unknown error");

    } catch (final Exception e) {
      return PublishResult.error(
          response.statusCode(), "UNKNOWN", "Failed to parse response: " + response.body());
    }
  }

  public record PublishResult(
      boolean success,
      List<Long> logPositions,
      int statusCode,
      String rejectionReason,
      String error) {

    static PublishResult success(final List<Long> logPositions) {
      return new PublishResult(true, logPositions, 200, null, null);
    }

    static PublishResult error(
        final int statusCode, final String rejectionReason, final String error) {
      return new PublishResult(false, List.of(-1L), statusCode, rejectionReason, error);
    }
  }

  public final class BatchPublisher {

    private final EventBridgeBatchBuilder batchBuilder = new EventBridgeBatchBuilder();
    private final EventBridgeRecordBuilder recordBuilder = new EventBridgeRecordBuilder();

    public BatchPublisher addRecord(
        final ContentType contentType, final String key, final byte[] payload) {
      batchBuilder.addEntry(
          recordBuilder.reset().contentType(contentType).key(key).payload(payload).build());
      return this;
    }

    public BatchPublisher addRecord(final ContentType contentType, final byte[] payload) {
      batchBuilder.addEntry(
          recordBuilder.reset().contentType(contentType).payload(payload).build());
      return this;
    }

    public BatchPublisher addEntry(final byte[] entryBytes) {
      batchBuilder.addEntry(entryBytes);
      return this;
    }

    public CompletableFuture<PublishResult> publish(final int partitionId) {
      if (batchBuilder.getEntryCount() == 0) {
        return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
      }

      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayBaseUrl + "/v1/events/" + partitionId))
              .header("Content-Type", "application/octet-stream")
              .timeout(requestTimeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(batchBuilder.build()))
              .build();

      return httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(EventBridgePublishClient.this::parseResponse);
    }
  }
}
