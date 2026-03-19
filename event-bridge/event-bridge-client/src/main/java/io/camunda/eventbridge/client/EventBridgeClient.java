/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.eventbridge.core.EventDataBatch;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Java client for the Event Bridge gateway HTTP API.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var client = EventBridgeClient.create("http://localhost:8080");
 * var positions = client.publishBatch(0, List.of(new byte[]{1,2,3})).get();
 * var consumer = client.subscribe("my-group", "consumer-1").get();
 * var events = consumer.poll(100, Duration.ofMillis(1000));
 * consumer.commitOffset(0, events.get(events.size()-1).position()).get();
 * }</pre>
 */
public final class EventBridgeClient {

  static final int POLL_TIMEOUT_SLACK_MS = 5_000;
  static final int REBALANCE_RETRY_DELAY_MS = 1_000;

  private final String gatewayUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private EventBridgeClient(
      final String gatewayUrl, final HttpClient httpClient, final ObjectMapper objectMapper) {
    this.gatewayUrl =
        gatewayUrl.endsWith("/") ? gatewayUrl.substring(0, gatewayUrl.length() - 1) : gatewayUrl;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Creates a client with default settings pointing to the given gateway URL.
   *
   * @param gatewayUrl base URL of the Event Bridge gateway (e.g. {@code "http://localhost:8080"})
   */
  public static EventBridgeClient create(final String gatewayUrl) {
    return new EventBridgeClient(
        gatewayUrl,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        new ObjectMapper());
  }

  /**
   * Publishes a batch of raw binary events to the given partition.
   *
   * @param partitionId target partition
   * @param batch the event batch to publish
   * @return a future resolving to the list of log positions assigned to each event in order
   * @throws EventBridgeException (exceptionally) on HTTP 4xx/5xx
   */
  public CompletableFuture<List<Long>> publishBatch(
      final int partitionId, final EventDataBatch batch) {
    return CompletableFuture.supplyAsync(
        () -> {
          final byte[] body = batch.toBytes();

          final var request =
              HttpRequest.newBuilder()
                  .uri(URI.create(gatewayUrl + "/v1/events/" + partitionId))
                  .header("Content-Type", "application/octet-stream")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build();

          final HttpResponse<String> response;
          try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            throw new EventBridgeException("HTTP request failed", e);
          }

          if (response.statusCode() != 200) {
            throw new EventBridgeException(
                "Publish failed: HTTP " + response.statusCode() + " — " + response.body());
          }

          try {
            @SuppressWarnings("unchecked")
            final var result =
                (Map<String, Object>) objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            final List<Number> positions = (List<Number>) result.get("logPositions");
            final var longPositions = new ArrayList<Long>(positions.size());
            for (final Number n : positions) {
              longPositions.add(n.longValue());
            }
            return longPositions;
          } catch (final IOException e) {
            throw new EventBridgeException("Failed to parse publish response", e);
          }
        });
  }

  /**
   * Registers a consumer with the coordinator and returns a {@link Consumer} handle.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier within the group
   * @return a future resolving to a {@link Consumer} handle
   * @throws CoordinatorUnavailableException (exceptionally) if the coordinator returns HTTP 503
   */
  public CompletableFuture<Consumer> subscribe(final String groupId, final String consumerId) {
    return CompletableFuture.supplyAsync(
        () -> {
          final var request =
              HttpRequest.newBuilder()
                  .uri(
                      URI.create(
                          gatewayUrl
                              + "/v1/consumers/"
                              + groupId
                              + "/"
                              + consumerId
                              + "/subscribe"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build();

          final HttpResponse<String> response;
          try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            throw new EventBridgeException("HTTP request failed", e);
          }

          if (response.statusCode() == 503) {
            throw new CoordinatorUnavailableException(
                "Coordinator unavailable: " + response.body());
          }
          if (response.statusCode() != 200) {
            throw new EventBridgeException(
                "Subscribe failed: HTTP " + response.statusCode() + " — " + response.body());
          }

          try {
            @SuppressWarnings("unchecked")
            final var result =
                (Map<String, Object>) objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            final List<Integer> partitions = (List<Integer>) result.get("assignedPartitions");
            final long generation = ((Number) result.get("generation")).longValue();
            return new Consumer(groupId, consumerId, partitions, generation, this);
          } catch (final IOException e) {
            throw new EventBridgeException("Failed to parse subscribe response", e);
          }
        });
  }

  /**
   * Returns the highest committed log position on the given partition.
   *
   * @param partitionId target partition
   * @return the position, or 0 if the partition is empty
   */
  public long getLatestPosition(final int partitionId) {
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/v1/partitions/" + partitionId + "/latest-position"))
            .GET()
            .build();

    final HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EventBridgeException("HTTP request failed", e);
    }

    if (response.statusCode() != 200) {
      throw new EventBridgeException(
          "getLatestPosition failed: HTTP " + response.statusCode() + " — " + response.body());
    }

    try {
      @SuppressWarnings("unchecked")
      final var result = (Map<String, Object>) objectMapper.readValue(response.body(), Map.class);
      return ((Number) result.get("position")).longValue();
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse latestPosition response", e);
    }
  }

  String getGatewayUrl() {
    return gatewayUrl;
  }

  HttpClient getHttpClient() {
    return httpClient;
  }

  ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
