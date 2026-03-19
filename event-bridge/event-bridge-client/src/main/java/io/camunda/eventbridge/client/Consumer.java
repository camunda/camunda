/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle returned by {@link EventBridgeClient#subscribe(String, String)}.
 *
 * <p>Tracks the consumer's assigned partitions, generation, and per-partition {@code nextPosition}.
 * All methods are safe to call from multiple threads except where noted.
 */
public final class Consumer {

  private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

  private final String groupId;
  private final String consumerId;
  private final EventBridgeClient client;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** Generation returned by the last successful subscribe or poll response. */
  private volatile long generation;

  /** Partitions currently assigned to this consumer (sorted ascending). */
  private volatile List<Integer> assignedPartitions;

  /**
   * Per-partition fetch position. Initialized to {@code -1} (= oldest retained position) for each
   * newly assigned partition.
   */
  private final ConcurrentHashMap<Integer, Long> nextPositions = new ConcurrentHashMap<>();

  Consumer(
      final String groupId,
      final String consumerId,
      final List<Integer> assignedPartitions,
      final long generation,
      final EventBridgeClient client) {
    this.groupId = groupId;
    this.consumerId = consumerId;
    this.generation = generation;
    this.client = client;
    updateAssignment(assignedPartitions);
  }

  /**
   * Pulls the next batch of events from all currently assigned partitions, in ascending partition
   * ID order.
   *
   * <p><strong>Callers are responsible for being aware of worst-case latency:</strong> with N
   * partitions and a {@code timeout} of T ms, the worst-case wall-clock latency of {@code poll()}
   * is N × T plus network overhead.
   *
   * @param maxRecords maximum number of records to fetch per partition
   * @param timeout per-partition server-side long-poll wait time; passed as {@code serverWaitMs}
   * @return list of events in the order they were fetched (may be empty if no new records)
   * @throws RebalanceInProgressException if any partition signals a rebalance; all partial results
   *     from this call are discarded and the caller must retry after updating its partition set
   * @throws ConsumerClosedException if {@link #close()} has been called
   */
  public List<Event> poll(final int maxRecords, final Duration timeout) {
    checkNotClosed();
    final int serverWaitMs = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
    final List<Event> allEvents = new ArrayList<>();

    final List<Integer> partitions = new ArrayList<>(assignedPartitions);
    Collections.sort(partitions);

    // Collect position updates locally; only commit them after the full loop succeeds.
    // If REBALANCE_IN_PROGRESS is encountered, the pending map is discarded and positions
    // are NOT advanced — ensuring re-delivery of events from partitions polled before the
    // rebalance-triggering partition.
    final Map<Integer, Long> pendingPositions = new HashMap<>();

    for (final int partitionId : partitions) {
      checkNotClosed();
      final long fromPosition = nextPositions.getOrDefault(partitionId, -1L);
      final var response = doPoll(partitionId, fromPosition, maxRecords, serverWaitMs);

      if ("REBALANCE_IN_PROGRESS".equals(response.get("status"))) {
        // Discard all events collected so far and pending position updates, then throw.
        @SuppressWarnings("unchecked")
        final List<Integer> newPartitions = (List<Integer>) response.get("assignedPartitions");
        final long newGeneration = ((Number) response.get("generation")).longValue();
        this.generation = newGeneration;
        updateAssignment(newPartitions);
        throw new RebalanceInProgressException(new ArrayList<>(newPartitions));
      }

      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> events =
          (List<Map<String, Object>>) response.getOrDefault("events", List.of());
      for (final Map<String, Object> evt : events) {
        final long position = ((Number) evt.get("position")).longValue();
        final String base64Payload = (String) evt.get("payload");
        final byte[] payload = Base64.getDecoder().decode(base64Payload);
        allEvents.add(new Event(position, partitionId, payload));
      }

      final Object nextPosObj = response.get("nextPosition");
      if (nextPosObj != null) {
        pendingPositions.put(partitionId, ((Number) nextPosObj).longValue());
      }
    }

    // Commit all position advances atomically only after a full successful sweep.
    nextPositions.putAll(pendingPositions);

    return allEvents;
  }

  /**
   * Commits the consumed offset for the given partition.
   *
   * @param partitionId the partition to commit
   * @param position the position that has been processed
   * @return a future that completes when the commit is acknowledged
   */
  public CompletableFuture<Void> commitOffset(final int partitionId, final long position) {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();
          final var httpClient = client.getHttpClient();
          final String url =
              client.getGatewayUrl()
                  + "/v1/events/"
                  + partitionId
                  + "/commit"
                  + "?groupId="
                  + groupId
                  + "&consumerId="
                  + consumerId
                  + "&position="
                  + position
                  + "&generation="
                  + generation;
          final var request =
              HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build();

          final HttpResponse<String> response;
          try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new EventBridgeException("commitOffset HTTP request failed", e);
          }

          if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new EventBridgeException(
                "commitOffset failed: HTTP " + response.statusCode() + " — " + response.body());
          }
        });
  }

  /**
   * Sends a heartbeat to the coordinator.
   *
   * @return a future that completes when the heartbeat is acknowledged
   * @throws CoordinatorUnavailableException (exceptionally) if the coordinator is unreachable
   * @throws ConsumerNotRegisteredException (exceptionally) if this consumer has been marked dead
   */
  public CompletableFuture<Void> sendHeartbeat() {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();
          final var httpClient = client.getHttpClient();
          final var request =
              HttpRequest.newBuilder()
                  .uri(
                      URI.create(
                          client.getGatewayUrl()
                              + "/v1/consumers/"
                              + groupId
                              + "/"
                              + consumerId
                              + "/heartbeat"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build();

          final HttpResponse<String> response;
          try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CoordinatorUnavailableException(
                "Heartbeat HTTP request failed: " + e.getMessage());
          }

          if (response.statusCode() == 503) {
            throw new CoordinatorUnavailableException(
                "Heartbeat rejected — coordinator unavailable");
          }
          if (response.statusCode() == 400) {
            throw new ConsumerNotRegisteredException(groupId, consumerId);
          }
          if (response.statusCode() != 200) {
            throw new EventBridgeException(
                "Heartbeat failed: HTTP " + response.statusCode() + " — " + response.body());
          }

          // Parse the generation from the response; a changed generation signals a rebalance.
          try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> body =
                client.getObjectMapper().readValue(response.body(), Map.class);
            final Object genObj = body.get("generation");
            if (genObj != null) {
              generation = ((Number) genObj).longValue();
            }
          } catch (final IOException e) {
            // Non-fatal: if we can't parse the generation we skip the update.
            LOG.warn("Failed to parse heartbeat response body", e);
          }
        });
  }

  /**
   * Closes this consumer handle. Idempotent. After close, all method calls throw {@link
   * ConsumerClosedException}.
   */
  public void close() {
    closed.set(true);
  }

  public String getGroupId() {
    return groupId;
  }

  public String getConsumerId() {
    return consumerId;
  }

  public long getGeneration() {
    return generation;
  }

  public List<Integer> getAssignedPartitions() {
    return Collections.unmodifiableList(assignedPartitions);
  }

  // -------------------------------------------------------------------------
  // Internal helpers

  private Map<String, Object> doPoll(
      final int partitionId,
      final long fromPosition,
      final int maxRecords,
      final int serverWaitMs) {
    final int socketTimeoutMs = serverWaitMs + EventBridgeClient.POLL_TIMEOUT_SLACK_MS;
    final String url =
        client.getGatewayUrl()
            + "/v1/events/"
            + partitionId
            + "/poll"
            + "?groupId="
            + groupId
            + "&consumerId="
            + consumerId
            + "&fromPosition="
            + fromPosition
            + "&maxRecords="
            + maxRecords
            + "&serverWaitMs="
            + serverWaitMs
            + "&generation="
            + generation;

    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(socketTimeoutMs))
            .GET()
            .build();

    final HttpResponse<String> response;
    try {
      response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new EventBridgeException("poll HTTP request failed for partition " + partitionId, e);
    }

    if (response.statusCode() == 409) {
      // Stale generation — re-subscribe transparently
      throw new EventBridgeException(
          "Stale generation detected during poll; re-subscribe required");
    }

    if (response.statusCode() != 200) {
      throw new EventBridgeException(
          "poll failed on partition "
              + partitionId
              + ": HTTP "
              + response.statusCode()
              + " — "
              + response.body());
    }

    try {
      @SuppressWarnings("unchecked")
      final Map<String, Object> result =
          client.getObjectMapper().readValue(response.body(), Map.class);
      return result;
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse poll response", e);
    }
  }

  private void updateAssignment(final List<Integer> partitions) {
    final var sorted = new ArrayList<>(partitions);
    Collections.sort(sorted);
    // Initialize nextPosition for any newly assigned partition
    for (final int p : sorted) {
      nextPositions.putIfAbsent(p, -1L);
    }
    assignedPartitions = Collections.unmodifiableList(sorted);
  }

  private void checkNotClosed() {
    if (closed.get()) {
      throw new ConsumerClosedException(
          "Consumer " + consumerId + " in group " + groupId + " is closed");
    }
  }
}
