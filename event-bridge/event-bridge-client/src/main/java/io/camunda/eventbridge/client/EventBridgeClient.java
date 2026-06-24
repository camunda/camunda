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
import io.camunda.eventbridge.protocol.EventBridgeEntryBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Single Java client for the Event Bridge gateway HTTP API: publish, fetch, and consumer-group
 * consumption.
 *
 * <p>Publish/fetch usage:
 *
 * <pre>{@code
 * var client = EventBridgeClient.create("http://localhost:8080");
 *
 * // Publish a batch
 * var positions = client.newBatch()
 *     .add("order-1", jsonBytes)
 *     .add("order-2", jsonBytes2)
 *     .publish(0)
 *     .join();
 *
 * // Fetch raw batches from a partition
 * var result = client.fetch(0, 1, 64 * 1024).join();
 * for (var entry : result.entries(1)) {
 *   System.out.println(entry.getPosition() + ": " + new String(entry.getValueCopy()));
 * }
 * }</pre>
 *
 * <p>Consumer-group usage:
 *
 * <pre>{@code
 * var consumer = client.subscribe("my-group", "consumer-1", "my-topic").join();
 * consumer.sendHeartbeat().join();        // receives an initial partition assignment
 * var events = consumer.poll(100, Duration.ofMillis(1000));
 * }</pre>
 */
public final class EventBridgeClient implements AutoCloseable {

  private final ScheduledExecutorService executor;
  private final String gatewayUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final OffsetResetPolicy offsetResetPolicy;

  private EventBridgeClient(
      final String gatewayUrl,
      final HttpClient httpClient,
      final ObjectMapper objectMapper,
      final OffsetResetPolicy offsetResetPolicy) {
    this.gatewayUrl =
        gatewayUrl.endsWith("/") ? gatewayUrl.substring(0, gatewayUrl.length() - 1) : gatewayUrl;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.offsetResetPolicy = offsetResetPolicy;
    executor = Executors.newScheduledThreadPool(4);
  }

  /**
   * Creates a client with default settings (offset reset {@link OffsetResetPolicy#EARLIEST}).
   *
   * @param gatewayUrl base URL of the Event Bridge gateway (e.g. {@code "http://localhost:8080"})
   */
  public static EventBridgeClient create(final String gatewayUrl) {
    return create(gatewayUrl, OffsetResetPolicy.EARLIEST);
  }

  /**
   * Creates a client with an explicit {@link OffsetResetPolicy} for newly assigned partitions that
   * have no committed offset.
   */
  public static EventBridgeClient create(
      final String gatewayUrl, final OffsetResetPolicy offsetResetPolicy) {
    return new EventBridgeClient(
        gatewayUrl,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        new ObjectMapper(),
        offsetResetPolicy);
  }

  OffsetResetPolicy getOffsetResetPolicy() {
    return offsetResetPolicy;
  }

  // -------------------------------------------------------------------------
  // Publish

  /** Creates a new batch builder for publishing multiple entries in a single request. */
  public BatchPublisher newBatch() {
    return new BatchPublisher();
  }

  /** Publishes a single keyed entry to the given partition. */
  public CompletableFuture<List<Long>> publish(
      final int partitionId, final String key, final byte[] value) {
    return newBatch().add(key, value).publish(partitionId);
  }

  /** Publishes a single keyless entry to the given partition. */
  public CompletableFuture<List<Long>> publish(final int partitionId, final byte[] value) {
    return newBatch().add(value).publish(partitionId);
  }

  // -------------------------------------------------------------------------
  // Fetch

  /**
   * Fetches complete batches from a partition starting at {@code offset} (inclusive).
   *
   * @param partitionId partition to fetch from
   * @param offset position to start reading from; {@code <= 0} reads from the start
   * @param maxBytes soft ceiling on returned batch bytes
   * @return a future resolving to the fetch result
   */
  public CompletableFuture<FetchResult> fetch(
      final int partitionId, final long offset, final int maxBytes) {
    return fetch(partitionId, offset, maxBytes, 0, 0);
  }

  /**
   * Fetches complete batches with long-poll support.
   *
   * @param partitionId partition to fetch from
   * @param offset position to start reading from; {@code <= 0} reads from the start
   * @param maxBytes soft ceiling on returned batch bytes
   * @param minBytes minimum bytes before the broker responds (long-poll threshold)
   * @param maxWaitMs maximum time the broker waits for {@code minBytes} (0 = respond immediately)
   * @return a future resolving to the fetch result
   */
  public CompletableFuture<FetchResult> fetch(
      final int partitionId,
      final long offset,
      final int maxBytes,
      final int minBytes,
      final long maxWaitMs) {
    final var uri =
        URI.create(
            gatewayUrl
                + "/v1/events/"
                + partitionId
                + "/fetch?offset="
                + offset
                + "&maxBytes="
                + maxBytes
                + "&minBytes="
                + minBytes
                + "&maxWaitMs="
                + maxWaitMs);

    final var request =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/octet-stream")
            .timeout(Duration.ofMillis(maxWaitMs + 10_000))
            .GET()
            .build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> FetchResult.parse(response.statusCode(), response.body()));
  }

  /** Fetches batches from a partition of a topic ({@code GET /v1/topics/{topic}/.../fetch}). */
  public CompletableFuture<FetchResult> fetchFromTopic(
      final String topic, final int partitionId, final long offset, final int maxBytes) {
    final var uri =
        URI.create(
            gatewayUrl
                + "/v1/topics/"
                + topic
                + "/partitions/"
                + partitionId
                + "/fetch?offset="
                + offset
                + "&maxBytes="
                + maxBytes);

    final var request =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/octet-stream")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> FetchResult.parse(response.statusCode(), response.body()));
  }

  // -------------------------------------------------------------------------
  // Consume (consumer groups)

  /**
   * Returns a {@link Consumer} handle for the given group and consumer IDs.
   *
   * <p>The consumer auto-registers with the coordinator on {@link Consumer#joinGroup()} (invoked
   * here) and maintains membership via periodic heartbeats.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier within the group
   * @param topic the topic the group subscribes to
   * @return a future resolving to a {@link Consumer} handle
   */
  public CompletableFuture<Consumer> subscribe(
      final String groupId, final String consumerId, final String topic) {
    final var consumer = new Consumer(groupId, topic, consumerId, this);
    return consumer.joinGroup().handle((ignore, error) -> consumer);
  }

  /**
   * Creates a topic. Completes when the coordinator has accepted the request (the topic's Raft
   * group is provisioned asynchronously, so it is reported {@code CREATING} until ready).
   */
  public CompletableFuture<Void> createTopic(
      final String name, final int partitionCount, final int replicationFactor) {
    final String body;
    try {
      body =
          objectMapper.writeValueAsString(
              Map.<String, Object>of(
                  "name", name,
                  "partitionCount", partitionCount,
                  "replicationFactor", replicationFactor));
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to serialize createTopic request", e);
    }
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/v1/topics"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              if (response.statusCode() != 201) {
                throw new EventBridgeException(
                    "createTopic failed: HTTP " + response.statusCode() + " — " + response.body());
              }
              return null;
            });
  }

  /** Deletes a topic. Completes when the coordinator has accepted the request. */
  public CompletableFuture<Void> deleteTopic(final String name) {
    final var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    gatewayUrl + "/v1/topics/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
            .DELETE()
            .build();
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              if (response.statusCode() != 204) {
                throw new EventBridgeException(
                    "deleteTopic failed: HTTP " + response.statusCode() + " — " + response.body());
              }
              return null;
            });
  }

  /** Lists the registered topics. */
  public CompletableFuture<List<TopicInfo>> listTopics() {
    final var request =
        HttpRequest.newBuilder().uri(URI.create(gatewayUrl + "/v1/topics")).GET().build();
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              if (response.statusCode() != 200) {
                throw new EventBridgeException(
                    "listTopics failed: HTTP " + response.statusCode() + " — " + response.body());
              }
              try {
                @SuppressWarnings("unchecked")
                final List<Map<String, Object>> raw =
                    objectMapper.readValue(response.body(), List.class);
                final List<TopicInfo> topics = new ArrayList<>(raw.size());
                for (final var t : raw) {
                  topics.add(
                      new TopicInfo(
                          (String) t.get("name"),
                          ((Number) t.get("partitionCount")).intValue(),
                          ((Number) t.get("replicationFactor")).intValue(),
                          (String) t.get("status")));
                }
                return topics;
              } catch (final IOException e) {
                throw new EventBridgeException("Failed to parse listTopics response", e);
              }
            });
  }

  /** A topic as reported by the registry. */
  public record TopicInfo(String name, int partitionCount, int replicationFactor, String status) {}

  /**
   * Shuts down the client's scheduler and HTTP client. After close, scheduled consumer heartbeats
   * stop and in-flight requests are abandoned. Idempotent.
   */
  @Override
  public void close() {
    executor.shutdownNow();
    httpClient.close();
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

  ScheduledExecutorService getExecutor() {
    return executor;
  }

  private List<Long> parsePublishResponse(final HttpResponse<String> response) {
    if (response.statusCode() != 200) {
      throw new EventBridgeException(
          "Publish failed: HTTP " + response.statusCode() + " — " + response.body());
    }
    try {
      @SuppressWarnings("unchecked")
      final var result = (Map<String, Object>) objectMapper.readValue(response.body(), Map.class);
      @SuppressWarnings("unchecked")
      final List<Number> positions = (List<Number>) result.get("logPositions");
      if (positions == null) {
        throw new EventBridgeException(
            "Publish response missing 'logPositions': " + response.body());
      }
      final var longPositions = new ArrayList<Long>(positions.size());
      for (final Number n : positions) {
        longPositions.add(n.longValue());
      }
      return longPositions;
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse publish response", e);
    }
  }

  /** Fluent builder for publishing a batch of entries in a single request. */
  public final class BatchPublisher {

    private final EventBridgeBatchBuilder batchBuilder = new EventBridgeBatchBuilder();
    private final EventBridgeEntryBuilder entryBuilder = new EventBridgeEntryBuilder();

    public BatchPublisher add(final String key, final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().key(key).value(value).build());
      return this;
    }

    public BatchPublisher add(final byte[] key, final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().key(key).value(value).build());
      return this;
    }

    public BatchPublisher add(final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().value(value).build());
      return this;
    }

    public BatchPublisher add(final String value) {
      batchBuilder.addEntry(entryBuilder.reset().value(value).build());
      return this;
    }

    /**
     * Publishes the batch to the given partition.
     *
     * @return a future resolving to the log positions assigned by the broker. The POC gateway
     *     returns {@code [firstPosition, lastPosition]} for the batch rather than one position per
     *     entry.
     */
    public CompletableFuture<List<Long>> publish(final int partitionId) {
      if (batchBuilder.getEntryCount() == 0) {
        return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
      }

      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayUrl + "/v1/events/" + partitionId))
              .header("Content-Type", "application/octet-stream")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofByteArray(batchBuilder.build()))
              .build();

      return httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(EventBridgeClient.this::parsePublishResponse);
    }

    /** Publishes the batch to a partition of a topic ({@code POST /v1/topics/{topic}/...}). */
    public CompletableFuture<List<Long>> publishToTopic(final String topic, final int partitionId) {
      if (batchBuilder.getEntryCount() == 0) {
        return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
      }

      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayUrl + "/v1/topics/" + topic + "/partitions/" + partitionId))
              .header("Content-Type", "application/octet-stream")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofByteArray(batchBuilder.build()))
              .build();

      return httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(EventBridgeClient.this::parsePublishResponse);
    }
  }
}
