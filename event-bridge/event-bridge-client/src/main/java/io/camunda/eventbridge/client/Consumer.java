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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

  /** Sentinel for a newly assigned partition whose start offset has not been resolved yet. */
  private static final long UNSET_POSITION = Long.MIN_VALUE;

  private final ScheduledExecutorService executor;
  private final OffsetResetPolicy offsetResetPolicy;
  private final String groupId;
  private final String instanceId;
  // Written from executor threads (join/heartbeat/leave), read from caller threads (poll, commit) —
  // must be volatile for visibility, since this class is documented as thread-safe.
  private volatile String memberId;
  private volatile long memberEpoch = 0;
  private final EventBridgeClient client;
  private final AtomicBoolean closed = new AtomicBoolean(false);

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

  private volatile ScheduledFuture<?> scheduledHeartbeat;

  /** Primary constructor. Consumer starts with no owned partitions and {@code currentEpoch = 0}. */
  Consumer(final String groupId, final String instanceId, final EventBridgeClient client) {
    this.groupId = groupId;
    this.client = client;
    this.instanceId = instanceId;
    executor = client.getExecutor();
    offsetResetPolicy = client.getOffsetResetPolicy();
  }

  /**
   * Package-private convenience constructor for tests that need pre-seeded owned partitions and
   * epoch without going through a heartbeat round-trip.
   */
  Consumer(
      final String groupId,
      final String consumerId,
      final List<Integer> initialPartitions,
      final int initialEpoch,
      final EventBridgeClient client) {
    this.groupId = groupId;
    instanceId = consumerId;
    memberEpoch = initialEpoch;
    this.client = client;
    executor = client.getExecutor();
    offsetResetPolicy = client.getOffsetResetPolicy();
    applyOwnedPartitions(initialPartitions);
  }

  public CompletableFuture<Void> joinGroup() {
    final var future = new CompletableFuture<Void>();

    final String requestBody;
    try {
      final var hbBody = new LinkedHashMap<String, Object>();
      hbBody.put("instanceId", instanceId);
      requestBody = client.getObjectMapper().writeValueAsString(hbBody);
    } catch (final JsonProcessingException e) {
      throw new EventBridgeException("Failed to serialize heartbeat request", e);
    }

    final var httpRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    client.getGatewayUrl()
                        + "/v1/groups/"
                        + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                        + "/join"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    client
        .getHttpClient()
        .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Join group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }

              Map<String, Object> body;
              try {
                //noinspection unchecked
                body = client.getObjectMapper().readValue(response.body(), Map.class);
              } catch (final IOException e) {
                LOG.warn("Failed to parse join response body; skipping state update", e);
                body = Map.of();
              }

              if (response.statusCode() == 200) {
                final var errorCode = (String) body.get("errorCode");
                memberId = (String) body.get("memberId");
                final var epochVal = body.get("memberEpoch");
                memberEpoch = epochVal instanceof final Number n ? n.longValue() : 0L;

                LOG.info(
                    "[JoinGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d; start send heartbeat"
                        .formatted(instanceId, groupId, errorCode, memberId, memberEpoch));
                scheduleSendHeartbeat();
                return future.complete(null);
              }

              return future.completeExceptionally(new RuntimeException("Failed to Join Group"));
            },
            executor);

    return future;
  }

  public CompletableFuture<Void> leaveGroup() {
    final var future = new CompletableFuture<Void>();

    final String requestBody;
    try {
      final var hbBody = new LinkedHashMap<String, Object>();
      hbBody.put("memberId", memberId);
      hbBody.put("memberEpoch", memberEpoch);
      requestBody = client.getObjectMapper().writeValueAsString(hbBody);
    } catch (final JsonProcessingException e) {
      throw new EventBridgeException("Failed to serialize heartbeat request", e);
    }

    final var httpRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    client.getGatewayUrl()
                        + "/v1/groups/"
                        + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                        + "/consumers/"
                        + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
                        + "/leave"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    client
        .getHttpClient()
        .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Leave group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }

              Map<String, Object> body;
              try {
                //noinspection unchecked
                body = client.getObjectMapper().readValue(response.body(), Map.class);
              } catch (final IOException e) {
                LOG.warn("Failed to parse leave response body; skipping state update", e);
                body = Map.of();
              }

              if (response.statusCode() == 200) {
                final var errorCode = (String) body.get("errorCode");

                LOG.info(
                    "[LeaveGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d"
                        .formatted(instanceId, groupId, errorCode, memberId, memberEpoch));

                memberId = null;
                memberEpoch = -1;
                ownedPartitions = Collections.emptyList();

                final var hb = scheduledHeartbeat;
                if (hb != null) {
                  hb.cancel(false);
                }

                return future.complete(null);
              }

              return future.completeExceptionally(new RuntimeException("Failed to Leave Group"));
            },
            executor);

    return future;
  }

  private void scheduleSendHeartbeat() {
    if (closed.get()) {
      return;
    }
    // Reschedule regardless of success: a transient heartbeat failure must not silently stop the
    // heartbeat loop and let the consumer expire from the group.
    scheduledHeartbeat =
        executor.schedule(
            () ->
                sendHeartbeat()
                    .whenComplete(
                        (ignored, error) -> {
                          if (error != null) {
                            LOG.warn("Heartbeat failed; will retry: {}", error.getMessage());
                            scheduleSendHeartbeat();
                          }
                        }),
            3,
            TimeUnit.SECONDS);
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
  /**
   * Re-registers this consumer with the coordinator after it has been fenced or the coordinator
   * lost its membership (e.g. a coordinator failover wiped the in-memory registry). Synchronous:
   * callers are already on an executor thread (the heartbeat loop or a commit retry).
   *
   * <p>Acquires a fresh {@code memberId}/{@code memberEpoch} and drops owned partitions; the
   * coordinator reassigns them on the next heartbeat and re-seeds committed offsets. {@link
   * #nextPositions} for retained partitions is preserved (and only ever advanced via {@code max}),
   * so resumption is at-least-once.
   */
  private void rejoinSync() {
    final String requestBody;
    try {
      final var body = new LinkedHashMap<String, Object>();
      body.put("instanceId", instanceId);
      requestBody = client.getObjectMapper().writeValueAsString(body);
    } catch (final JsonProcessingException e) {
      throw new EventBridgeException("Failed to serialize rejoin request", e);
    }

    final var httpRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    client.getGatewayUrl()
                        + "/v1/groups/"
                        + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                        + "/join"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    final HttpResponse<String> response;
    try {
      response = client.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new CoordinatorUnavailableException("Rejoin request failed: " + e.getMessage());
    }

    if (response.statusCode() != 200) {
      throw new EventBridgeException(
          "Rejoin failed: HTTP " + response.statusCode() + " — " + response.body());
    }

    final Map<String, Object> body;
    try {
      //noinspection unchecked
      body = client.getObjectMapper().readValue(response.body(), Map.class);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse rejoin response", e);
    }

    memberId = (String) body.get("memberId");
    final var epochVal = body.get("memberEpoch");
    memberEpoch = epochVal instanceof final Number n ? n.longValue() : 0L;
    applyOwnedPartitions(List.of());

    LOG.info(
        "[Rejoin][Consumer={}] group {} rejoined as member {} (epoch {})",
        instanceId,
        groupId,
        memberId,
        memberEpoch);
  }

  public CompletableFuture<Void> sendHeartbeat() {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();

          final long snapshotEpoch = memberEpoch;
          final List<Integer> snapshotOwned = ownedPartitions;

          final String requestBody;
          try {
            final var hbBody = new LinkedHashMap<String, Object>();
            hbBody.put("epoch", memberEpoch);
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
                              + "/v1/groups/"
                              + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                                  .replace("+", "%20")
                              + "/consumers/"
                              + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
                                  .replace("+", "%20")
                              + "/heartbeat"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                  .build();

          final HttpResponse<String> httpResponse;
          try {
            httpResponse =
                client.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
          } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            throw new CoordinatorUnavailableException(
                "Heartbeat HTTP request failed: " + e.getMessage());
          }

          if (httpResponse.statusCode() == 503) {
            throw new CoordinatorUnavailableException(
                "Heartbeat rejected — coordinator unavailable");
          }
          if (httpResponse.statusCode() == 409) {
            // Fenced or unknown member (stale epoch, or the coordinator failed over and lost
            // in-memory membership). Re-register instead of heartbeating forever as a ghost.
            LOG.warn(
                "[Heartbeat][Consumer={}] membership fenced/unknown (409); rejoining group {}",
                instanceId,
                groupId);
            rejoinSync();
            scheduleSendHeartbeat();
            return;
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

          final long serverEpoch =
              ((Number) body.getOrDefault("memberEpoch", snapshotEpoch)).longValue();

          if (serverEpoch < snapshotEpoch) {
            LOG.debug(
                "Ignoring stale heartbeat response (server epoch {} < client epoch {})",
                serverEpoch,
                snapshotEpoch);
            return;
          }

          // Note: no explicit ACK is sent for either path. The coordinator confirms a revocation
          // from the ownedPartitions this consumer reports in its next heartbeat (see
          // ConsumerGroup.reconcileAssignment); applying the change here and reporting it next beat
          // is the acknowledgement.
          if (serverEpoch > snapshotEpoch) {
            // Full reconciliation: replace owned partitions wholesale from fullAssignment.
            final List<Integer> fullAssignment = extractList(body, "assignment");
            applyOwnedPartitions(fullAssignment);
            memberEpoch = serverEpoch;
          } else {
            // Delta path (serverEpoch == snapshotEpoch).
            final List<Integer> revoke = extractList(body, "revoke");
            final List<Integer> assign = extractList(body, "assign");

            if (!revoke.isEmpty() || !assign.isEmpty()) {
              final var newOwned = new ArrayList<>(snapshotOwned);
              newOwned.removeAll(revoke);
              newOwned.addAll(assign);
              applyOwnedPartitions(newOwned);
            }
          }

          seedCommittedOffsets(body);

          final var state = (String) body.get("errorCode");

          LOG.info(
              "[Heartbeat][Consumer=%s] Consumer Group %s (state %s): memberId %s, memberEpoch: %d, ownedPartitions: %s"
                  .formatted(instanceId, groupId, state, memberId, memberEpoch, ownedPartitions));

          scheduleSendHeartbeat();
        },
        executor);
  }

  /**
   * Pulls the next batch of events from all currently owned partitions, in ascending partition ID
   * order, via the gateway poll endpoint (routed to each partition's leader).
   *
   * <p>POC note: this uses the immediate (non-long-poll) path, so {@code timeout} is not used to
   * wait server-side. Callers should pace their poll loop (e.g. a short sleep when no events are
   * returned).
   *
   * @param maxRecords maximum number of records to fetch per partition
   * @param timeout currently unused (reserved for long-poll support)
   * @return list of events fetched (may be empty if no new records available)
   * @throws ConsumerClosedException if {@link #close()} has been called
   */
  public List<Event> poll(final int maxRecords, final Duration timeout) {
    checkNotClosed();
    final List<Event> allEvents = new ArrayList<>();

    final List<Integer> partitions = new ArrayList<>(ownedPartitions);
    Collections.sort(partitions);

    // Collect position updates locally; apply them only after a full successful sweep so
    // that a mid-sweep signal does not advance positions for already-polled partitions.
    final Map<Integer, Long> pendingPositions = new LinkedHashMap<>();

    for (final int partitionId : partitions) {
      checkNotClosed();
      long fromPosition = nextPositions.getOrDefault(partitionId, -1L);
      if (fromPosition == UNSET_POSITION) {
        // No committed offset for this newly assigned partition — apply the reset policy.
        fromPosition = resolveStartPosition(partitionId);
        nextPositions.put(partitionId, fromPosition);
      }

      final Map<String, Object> response = doPoll(partitionId, fromPosition, maxRecords);
      if (!"OK".equals(response.get("status"))) {
        // Partition not currently pollable here (e.g. leadership moved); skip this sweep.
        continue;
      }

      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> events =
          (List<Map<String, Object>>) response.getOrDefault("events", List.of());
      for (final Map<String, Object> evt : events) {
        final long position = ((Number) evt.get("position")).longValue();
        final byte[] payload = Base64.getDecoder().decode((String) evt.get("payload"));
        allEvents.add(new Event(position, partitionId, payload));
      }

      final Object nextPos = response.get("nextPosition");
      if (nextPos != null) {
        pendingPositions.put(partitionId, ((Number) nextPos).longValue());
      }
    }

    nextPositions.putAll(pendingPositions);
    return allEvents;
  }

  private Map<String, Object> doPoll(
      final int partitionId, final long fromPosition, final int maxRecords) {
    // Reads are not group-aware (see the poll-fencing decision): a poll is just a partition +
    // offset
    // + limit. Membership/epoch is only enforced at commit time on the coordinator.
    final String query = "fromPosition=" + fromPosition + "&maxRecords=" + maxRecords;

    final var uri =
        URI.create(client.getGatewayUrl() + "/v1/events/" + partitionId + "/poll?" + query);
    final var request = HttpRequest.newBuilder().uri(uri).GET().build();

    final HttpResponse<String> response;
    try {
      response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
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
                "Consumer not registered on commitOffset; rejoining and retrying once: {}",
                e.getMessage());
            rejoinSync();
            // Retry exactly once with the fresh membership; any exception (including a repeated
            // ConsumerNotRegisteredException, e.g. the partition is no longer owned) propagates.
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
    if (scheduledHeartbeat != null) {
      scheduledHeartbeat.cancel(false);
    }
  }

  public String getGroupId() {
    return groupId;
  }

  public String getMemberId() {
    return memberId;
  }

  /**
   * Returns the epoch from the last successful heartbeat response ({@code 0} if never received).
   */
  public long getMemberEpoch() {
    return memberEpoch;
  }

  /** Returns an unmodifiable snapshot of the partitions currently owned by this consumer. */
  public List<Integer> getOwnedPartitions() {
    return Collections.unmodifiableList(ownedPartitions);
  }

  // -------------------------------------------------------------------------
  // Internal helpers

  private void doCommitOffset(final int partitionId, final long position) {
    // Commit goes to the coordinator (owns membership + epoch), which fences stale commits.
    final String url =
        client.getGatewayUrl()
            + "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
            + "/commit";
    final String jsonBody;
    try {
      final var bodyMap = new LinkedHashMap<String, Object>();
      bodyMap.put("partitionId", partitionId);
      bodyMap.put("position", position);
      bodyMap.put("memberEpoch", memberEpoch);
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
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EventBridgeException("commitOffset HTTP request failed", e);
    }

    if (response.statusCode() == 404 || response.statusCode() == 409) {
      // 409: fenced/unknown member — the coordinator rejected the commit (no longer a silent 200).
      throw new ConsumerNotRegisteredException(groupId, memberId);
    }
    if (response.statusCode() == 400) {
      final String body = response.body();
      if (body != null && body.contains("\"CONSUMER_NOT_REGISTERED\"")) {
        throw new ConsumerNotRegisteredException(groupId, memberId);
      }
      throw new EventBridgeException("commitOffset failed: HTTP 400 — " + body);
    }
    if (response.statusCode() != 204 && response.statusCode() != 200) {
      throw new EventBridgeException(
          "commitOffset failed: HTTP " + response.statusCode() + " — " + response.body());
    }
  }

  /**
   * Resolves the start position for a newly assigned partition that has no committed offset, per
   * the configured {@link OffsetResetPolicy}: {@code EARLIEST} → oldest retained ({@code -1});
   * {@code LATEST} → just past the current end of the log (skip existing records). Falls back to
   * earliest on error.
   */
  private long resolveStartPosition(final int partitionId) {
    if (offsetResetPolicy == OffsetResetPolicy.LATEST) {
      try {
        final long highWatermark = client.fetch(partitionId, 0, 4096).join().highWatermark();
        return highWatermark < 0 ? 0L : highWatermark + 1;
      } catch (final RuntimeException e) {
        LOG.warn(
            "Failed to resolve LATEST start for partition {}; falling back to earliest",
            partitionId,
            e);
        return -1L;
      }
    }
    return -1L; // EARLIEST
  }

  /**
   * Seeds {@link #nextPositions} from the coordinator's committed offsets carried in the heartbeat
   * response, so a (re)assigned consumer resumes from the committed position. Uses {@code max} so
   * an in-flight local position is never rewound to an older committed one.
   */
  private void seedCommittedOffsets(final Map<String, Object> body) {
    if (!(body.get("committedOffsets") instanceof final Map<?, ?> committed)) {
      return;
    }
    for (final var entry : committed.entrySet()) {
      final int partition = Integer.parseInt(String.valueOf(entry.getKey()));
      if (!ownedPartitions.contains(partition) || !(entry.getValue() instanceof Number offset)) {
        continue;
      }
      nextPositions.merge(partition, offset.longValue(), Math::max);
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
      // Newly assigned: start unresolved. A committed offset (seedCommittedOffsets) or the reset
      // policy (resolved on first poll) determines where this partition actually starts.
      nextPositions.putIfAbsent(p, UNSET_POSITION);
    }
    ownedPartitions = Collections.unmodifiableList(sorted);
  }

  private static List<Integer> extractList(final Map<String, Object> body, final String key) {
    final Object val = body.get(key);
    if (!(val instanceof List<?>)) {
      return List.of();
    }
    final List<?> raw = (List<?>) val;
    final List<Integer> result = new ArrayList<>(raw.size());
    for (final Object element : raw) {
      if (!(element instanceof Number)) {
        throw new EventBridgeException(
            "Invalid payload: expected integer elements in '" + key + "', got " + element);
      }
      result.add(((Number) element).intValue());
    }
    return Collections.unmodifiableList(result);
  }

  private void checkNotClosed() {
    if (closed.get()) {
      throw new ConsumerClosedException(
          "Consumer " + memberId + " in group " + groupId + " is closed");
    }
  }
}
