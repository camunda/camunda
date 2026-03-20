/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer handle returned by {@link EventBridgeClient#subscribe(String, String)}.
 *
 * <p>Tracks the consumer's owned partitions, epoch, and per-partition {@code nextPosition}. Call
 * {@link #sendHeartbeat()} periodically to maintain group membership and receive partition
 * assignment deltas or full reconciliation signals from the coordinator.
 *
 * <p>All public methods are thread-safe.
 */
public final class Consumer {

  private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

  private final String groupId;
  private final String consumerId;
  private final EventBridgeClient client;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * The epoch last received from the coordinator. Starts at {@code 0} to signal "never received a
   * heartbeat response". The coordinator's epoch starts at {@code 1}, so the first heartbeat always
   * triggers a full reconciliation path.
   */
  private volatile long currentEpoch = 0;

  /**
   * Partition IDs currently owned by this consumer (sorted ascending). Updated on every heartbeat
   * that returns a delta or full-reconciliation signal.
   */
  private volatile List<Integer> ownedPartitions = List.of();

  /**
   * Per-partition fetch position. Initialized to {@code -1} (oldest retained) for newly assigned
   * partitions; updated after each successful {@link #poll(int, Duration)}.
   */
  private final ConcurrentHashMap<Integer, Long> nextPositions = new ConcurrentHashMap<>();

  /** Primary constructor. Consumer starts with no owned partitions and {@code currentEpoch = 0}. */
  Consumer(final String groupId, final String consumerId, final EventBridgeClient client) {
    this.groupId = groupId;
    this.consumerId = consumerId;
    this.client = client;
  }

  /**
   * Package-private convenience constructor for tests that need pre-seeded owned partitions and
   * epoch without going through a heartbeat round-trip.
   */
  Consumer(
      final String groupId,
      final String consumerId,
      final List<Integer> initialPartitions,
      final long initialEpoch,
      final EventBridgeClient client) {
    this.groupId = groupId;
    this.consumerId = consumerId;
    this.currentEpoch = initialEpoch;
    this.client = client;
    applyOwnedPartitions(initialPartitions);
  }

  // -------------------------------------------------------------------------
  // Public API

  /**
   * Sends a heartbeat to the coordinator, carrying the current epoch and owned partitions.
   *
   * <p>The coordinator responds with one of:
   *
   * <ul>
   *   <li><strong>Delta</strong> ({@code res.epoch == currentEpoch}): apply {@code revoke} and
   *       {@code assign} lists; send an ACK if non-empty.
   *   <li><strong>Full reconciliation</strong> ({@code res.epoch > currentEpoch}): replace {@code
   *       ownedPartitions} wholesale from {@code fullAssignment}; send an ACK.
   *   <li><strong>Stale</strong> ({@code res.epoch < currentEpoch}): ignore; no state change.
   * </ul>
   *
   * <p>ACK responses with a non-2xx status are logged and discarded; the coordinator's {@code
   * ackTimeoutMs} handles the case where no ACK arrives.
   *
   * @return a future that completes when the heartbeat (and any ACK) has been sent
   * @throws CoordinatorUnavailableException (exceptionally) if the coordinator returns HTTP 503 or
   *     is unreachable
   * @throws ConsumerClosedException directly (before the future is returned) if {@link #close()}
   *     has already been called, or wrapped in an {@link java.util.concurrent.ExecutionException}
   *     if {@code close()} races with the in-flight task
   */
  public CompletableFuture<Void> sendHeartbeat() {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();

          final long snapshotEpoch = currentEpoch;
          final List<Integer> snapshotOwned = ownedPartitions;

          final String requestBody;
          try {
            final var hbBody = new LinkedHashMap<String, Object>();
            hbBody.put("epoch", snapshotEpoch);
            hbBody.put("ownedPartitions", snapshotOwned);
            requestBody = client.getObjectMapper().writeValueAsString(hbBody);
          } catch (final JsonProcessingException e) {
            throw new EventBridgeException("Failed to serialize heartbeat request", e);
          }

          final var httpRequest =
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
                  .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                  .build();

          final HttpResponse<String> httpResponse;
          try {
            httpResponse =
                client.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CoordinatorUnavailableException(
                "Heartbeat HTTP request failed: " + e.getMessage());
          }

          if (httpResponse.statusCode() == 503) {
            throw new CoordinatorUnavailableException(
                "Heartbeat rejected — coordinator unavailable");
          }
          if (httpResponse.statusCode() != 200) {
            throw new EventBridgeException(
                "Heartbeat failed: HTTP "
                    + httpResponse.statusCode()
                    + " — "
                    + httpResponse.body());
          }

          final Map<String, Object> body;
          try {
            //noinspection unchecked
            body = client.getObjectMapper().readValue(httpResponse.body(), Map.class);
          } catch (final IOException e) {
            LOG.warn("Failed to parse heartbeat response body; skipping state update", e);
            return;
          }

          final long serverEpoch = ((Number) body.getOrDefault("epoch", snapshotEpoch)).longValue();

          if (serverEpoch < snapshotEpoch) {
            LOG.debug(
                "Ignoring stale heartbeat response (server epoch {} < client epoch {})",
                serverEpoch,
                snapshotEpoch);
            return;
          }

          if (serverEpoch > snapshotEpoch) {
            // Full reconciliation: replace owned partitions wholesale from fullAssignment.
            final List<Integer> fullAssignment = extractList(body, "fullAssignment");
            applyOwnedPartitions(fullAssignment);
            currentEpoch = serverEpoch;

            final List<Integer> revoked =
                snapshotOwned.stream().filter(p -> !fullAssignment.contains(p)).toList();
            sendAckQuietly(serverEpoch, revoked, fullAssignment);
          } else {
            // Delta path (serverEpoch == snapshotEpoch).
            final List<Integer> revoke = extractList(body, "revoke");
            final List<Integer> assign = extractList(body, "assign");

            if (!revoke.isEmpty() || !assign.isEmpty()) {
              final var newOwned = new ArrayList<>(snapshotOwned);
              newOwned.removeAll(revoke);
              newOwned.addAll(assign);
              applyOwnedPartitions(newOwned);
              sendAckQuietly(serverEpoch, revoke, assign);
            }
          }
        });
  }

  /**
   * Pulls the next batch of events from all currently owned partitions, in ascending partition ID
   * order.
   *
   * <p><strong>Note on latency:</strong> with N partitions and a {@code timeout} of T ms, the
   * worst-case wall-clock latency is N × T plus network overhead.
   *
   * @param maxRecords maximum number of records to fetch per partition
   * @param timeout per-partition server-side long-poll wait time
   * @return list of events fetched (may be empty if no new records available)
   * @throws ConsumerClosedException if {@link #close()} has been called
   */
  public List<Event> poll(final int maxRecords, final Duration timeout) {
    checkNotClosed();
    final int serverWaitMs = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
    // Snapshot epoch once so every partition in this sweep reports the same value to the server,
    // regardless of any concurrent sendHeartbeat() that may update currentEpoch mid-sweep.
    final long epochSnapshot = currentEpoch;
    final List<Event> allEvents = new ArrayList<>();

    final List<Integer> partitions = new ArrayList<>(ownedPartitions);
    Collections.sort(partitions);

    // Collect position updates locally; apply them only after a full successful sweep so
    // that a mid-sweep signal does not advance positions for already-polled partitions.
    final Map<Integer, Long> pendingPositions = new LinkedHashMap<>();

    for (final int partitionId : partitions) {
      checkNotClosed();
      final long fromPosition = nextPositions.getOrDefault(partitionId, -1L);
      final var response =
          doPoll(partitionId, fromPosition, maxRecords, serverWaitMs, epochSnapshot);

      if ("REBALANCE_IN_PROGRESS".equals(response.get("status"))) {
        // Rebalances are now signalled exclusively via heartbeat epoch advances.
        // Return the events collected so far; the next sendHeartbeat() will reconcile.
        break;
      }

      //noinspection unchecked
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

    nextPositions.putAll(pendingPositions);
    return allEvents;
  }

  /**
   * Commits the consumed offset for the given partition.
   *
   * <p>If the coordinator reports that this consumer is no longer registered (HTTP 404), the client
   * automatically sends a heartbeat to re-register and retries the commit exactly once. If the
   * single retry also fails, the exception is propagated to the caller.
   *
   * <p><strong>Note on threading:</strong> the retry path calls {@code sendHeartbeat().get()} from
   * inside a {@code CompletableFuture.runAsync} task, blocking a thread in {@link
   * java.util.concurrent.ForkJoinPool#commonPool()} while it waits for a second pool task. Under
   * high concurrency this risks pool starvation. The retry is intentionally simple given the
   * low-frequency nature of 404 re-registration; callers in high-throughput contexts should avoid
   * concurrent {@code commitOffset} calls that may all trigger retries simultaneously.
   *
   * @param partitionId partition to commit
   * @param position the log position that has been fully processed
   * @return a future that completes when the commit is acknowledged
   */
  public CompletableFuture<Void> commitOffset(final int partitionId, final long position) {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();
          try {
            doCommitOffset(partitionId, position);
          } catch (final ConsumerNotRegisteredException e) {
            LOG.warn(
                "Consumer not registered on commitOffset; sending heartbeat and retrying once: {}",
                e.getMessage());
            try {
              sendHeartbeat().get();
            } catch (final ExecutionException | InterruptedException hbEx) {
              if (hbEx instanceof InterruptedException) Thread.currentThread().interrupt();
              throw new EventBridgeException(
                  "Heartbeat failed during commitOffset retry: " + hbEx.getMessage(), hbEx);
            }
            // Retry exactly once; any exception (including ConsumerNotRegisteredException)
            // propagates to the caller.
            doCommitOffset(partitionId, position);
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

  /**
   * Returns the epoch from the last successful heartbeat response ({@code 0} if never received).
   */
  public long getCurrentEpoch() {
    return currentEpoch;
  }

  /** Returns an unmodifiable snapshot of the partitions currently owned by this consumer. */
  public List<Integer> getOwnedPartitions() {
    return Collections.unmodifiableList(ownedPartitions);
  }

  // -------------------------------------------------------------------------
  // Internal helpers

  private void doCommitOffset(final int partitionId, final long position) {
    final String url = client.getGatewayUrl() + "/v1/events/" + partitionId + "/commit";
    final String jsonBody;
    try {
      final var bodyMap = new LinkedHashMap<String, Object>();
      bodyMap.put("groupId", groupId);
      bodyMap.put("consumerId", consumerId);
      bodyMap.put("position", position);
      jsonBody = client.getObjectMapper().writeValueAsString(bodyMap);
    } catch (final JsonProcessingException e) {
      throw new EventBridgeException("Failed to serialize commitOffset request", e);
    }
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

    final HttpResponse<String> response;
    try {
      response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new EventBridgeException("commitOffset HTTP request failed", e);
    }

    if (response.statusCode() == 404) {
      throw new ConsumerNotRegisteredException(groupId, consumerId);
    }
    if (response.statusCode() != 204 && response.statusCode() != 200) {
      throw new EventBridgeException(
          "commitOffset failed: HTTP " + response.statusCode() + " — " + response.body());
    }
  }

  /**
   * Sends an ACK to the coordinator. Non-2xx responses are logged and discarded per spec; the
   * coordinator's {@code ackTimeoutMs} handles the case where no ACK is received.
   */
  private void sendAckQuietly(
      final long epoch, final List<Integer> revoked, final List<Integer> assigned) {
    final String requestBody;
    try {
      final var ackBody = new LinkedHashMap<String, Object>();
      ackBody.put("epoch", epoch);
      ackBody.put("revoked", revoked);
      ackBody.put("assigned", assigned);
      requestBody = client.getObjectMapper().writeValueAsString(ackBody);
    } catch (final JsonProcessingException e) {
      LOG.warn("Failed to serialize ACK request; skipping ACK", e);
      return;
    }

    final var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    client.getGatewayUrl()
                        + "/v1/consumers/"
                        + groupId
                        + "/"
                        + consumerId
                        + "/ack"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    try {
      final var response =
          client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOG.warn(
            "ACK returned non-200 status {}; coordinator will handle via ackTimeoutMs",
            response.statusCode());
      }
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      LOG.warn(
          "ACK HTTP request failed; coordinator will handle via ackTimeoutMs: {}", e.getMessage());
    }
  }

  private Map<String, Object> doPoll(
      final int partitionId,
      final long fromPosition,
      final int maxRecords,
      final int serverWaitMs,
      final long epoch) {
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
            + "&epoch="
            + epoch;

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
      //noinspection unchecked
      return client.getObjectMapper().readValue(response.body(), Map.class);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse poll response", e);
    }
  }

  /**
   * Atomically replaces {@code ownedPartitions} with a sorted copy of {@code partitions}, updating
   * {@code nextPositions} to drop revoked partitions and initialise newly assigned ones.
   */
  private void applyOwnedPartitions(final List<Integer> partitions) {
    final var sorted = new ArrayList<>(partitions);
    Collections.sort(sorted);
    nextPositions.keySet().retainAll(sorted);
    for (final int p : sorted) {
      nextPositions.putIfAbsent(p, -1L);
    }
    ownedPartitions = Collections.unmodifiableList(sorted);
  }

  @SuppressWarnings("unchecked")
  private static List<Integer> extractList(final Map<String, Object> body, final String key) {
    final Object val = body.get(key);
    if (val instanceof List<?>) {
      return (List<Integer>) val;
    }
    return List.of();
  }

  private void checkNotClosed() {
    if (closed.get()) {
      throw new ConsumerClosedException(
          "Consumer " + consumerId + " in group " + groupId + " is closed");
    }
  }
}
