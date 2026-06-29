/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.eventbridge.batch.BatchBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
 * // Publish a batch to a topic partition
 * var positions = client.newBatch()
 *     .add("order-1", jsonBytes)
 *     .add("order-2", jsonBytes2)
 *     .publishToTopic("orders", 1)
 *     .join();
 *
 * // Fetch raw batches from a topic partition
 * var result = client.fetchFromTopic("orders", 1, 1, 64 * 1024).join();
 * for (var entry : result.entries(1)) {
 *   System.out.println(entry.getPosition() + ": " + new String(entry.getValueCopy()));
 * }
 * }</pre>
 *
 * <p>Consumer-group usage:
 *
 * <pre>{@code
 * var consumer = client.subscribe("my-group", "consumer-1", List.of("my-topic")).join();
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
        // Tolerate fields the gateway may add to responses — the client only reads what it needs.
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
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

  /** Publishes a single keyed entry to a partition of a topic. */
  public CompletableFuture<List<Long>> publishToTopic(
      final String topic, final int partitionId, final String key, final byte[] value) {
    return newBatch().add(key, value).publishToTopic(topic, partitionId);
  }

  /** Publishes a single keyless entry to a partition of a topic. */
  public CompletableFuture<List<Long>> publishToTopic(
      final String topic, final int partitionId, final byte[] value) {
    return newBatch().add(value).publishToTopic(topic, partitionId);
  }

  // -------------------------------------------------------------------------
  // Fetch

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
   * @param topics the topics the group subscribes to
   * @return a future resolving to a {@link Consumer} handle
   */
  public CompletableFuture<Consumer> subscribe(
      final String groupId, final String consumerId, final List<String> topics) {
    final var consumer = new Consumer(groupId, topics, consumerId, this);
    return consumer.joinGroup().handle((ignore, error) -> consumer);
  }

  /**
   * Creates a topic. Completes when the coordinator has accepted the request (the topic's Raft
   * group is provisioned asynchronously, so it is reported {@code CREATING} until ready).
   */
  public CompletableFuture<Void> createTopic(
      final String name, final int partitionCount, final int replicationFactor) {
    final var request =
        requestTo("/v1/topics")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    writeBody(
                        new CreateTopicRequest(name, partitionCount, replicationFactor),
                        "createTopic")))
            .build();
    return send(request)
        .thenApply(
            response -> {
              expectStatus(response, 201, "createTopic");
              return null;
            });
  }

  /** Deletes a topic. Completes when the coordinator has accepted the request. */
  public CompletableFuture<Void> deleteTopic(final String name) {
    final var request =
        requestTo("/v1/topics/" + URLEncoder.encode(name, StandardCharsets.UTF_8)).DELETE().build();
    return send(request)
        .thenApply(
            response -> {
              expectStatus(response, 204, "deleteTopic");
              return null;
            });
  }

  /** Lists the registered topics. */
  public CompletableFuture<List<TopicInfo>> listTopics() {
    final var request = requestTo("/v1/topics").GET().build();
    return send(request)
        .thenApply(
            response -> {
              expectStatus(response, 200, "listTopics");
              return List.of(readBody(response.body(), TopicInfo[].class, "listTopics"));
            });
  }

  /** A topic as reported by the registry. */
  public record TopicInfo(String name, int partitionCount, int replicationFactor, String status) {}

  /** Body of {@code POST /v1/topics}. */
  private record CreateTopicRequest(String name, int partitionCount, int replicationFactor) {}

  /** Body of a publish response ({@code logPositions} assigned by the broker). */
  private record PublishResponse(List<Long> logPositions) {}

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

  ScheduledExecutorService getExecutor() {
    return executor;
  }

  private List<Long> parsePublishResponse(final HttpResponse<String> response) {
    expectStatus(response, 200, "publish");
    return readBody(response.body(), PublishResponse.class, "publish").logPositions();
  }

  // -------------------------------------------------------------------------
  // HTTP helpers

  /** A request builder pre-pointed at {@code gatewayUrl + path} with the default timeout. */
  private HttpRequest.Builder requestTo(final String path) {
    return HttpRequest.newBuilder()
        .uri(URI.create(gatewayUrl + path))
        .timeout(Duration.ofSeconds(10));
  }

  /** Sends a request and returns its string-bodied response. */
  private CompletableFuture<HttpResponse<String>> send(final HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void expectStatus(
      final HttpResponse<String> response, final int expected, final String op) {
    if (response.statusCode() != expected) {
      throw new EventBridgeException(
          op + " failed: HTTP " + response.statusCode() + " — " + response.body());
    }
  }

  <T> T readBody(final String body, final Class<T> type, final String op) {
    try {
      return objectMapper.readValue(body, type);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse " + op + " response", e);
    }
  }

  String writeBody(final Object value, final String op) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to serialize " + op + " request", e);
    }
  }

  /** Fluent builder for publishing a batch of entries in a single request. */
  public final class BatchPublisher {

    private final BatchBuilder batchBuilder = new BatchBuilder();

    public BatchPublisher add(final String key, final byte[] value) {
      batchBuilder.add(key, value);
      return this;
    }

    public BatchPublisher add(final byte[] key, final byte[] value) {
      batchBuilder.add(key, value);
      return this;
    }

    public BatchPublisher add(final byte[] value) {
      batchBuilder.add(value);
      return this;
    }

    public BatchPublisher add(final String value) {
      batchBuilder.add(value.getBytes(StandardCharsets.UTF_8));
      return this;
    }

    /**
     * Publishes the batch to a partition of a topic ({@code POST /v1/topics/{topic}/...}).
     *
     * @return a future resolving to the log positions assigned by the broker. The POC gateway
     *     returns {@code [firstPosition, lastPosition]} for the batch rather than one position per
     *     entry.
     */
    public CompletableFuture<List<Long>> publishToTopic(final String topic, final int partitionId) {
      if (batchBuilder.entryCount() == 0) {
        return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
      }

      final var request =
          requestTo("/v1/topics/" + topic + "/partitions/" + partitionId)
              .header("Content-Type", "application/octet-stream")
              .POST(HttpRequest.BodyPublishers.ofByteArray(batchBuilder.build()))
              .build();
      return send(request).thenApply(EventBridgeClient.this::parsePublishResponse);
    }
  }
}
