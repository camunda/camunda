/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.ConsumerNotRegisteredException;
import io.camunda.eventbridge.client.CoordinatorUnavailableException;
import io.camunda.eventbridge.client.EventBridgeException;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.CommitRequest;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.HeartbeatRequest;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.HeartbeatResponse;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.JoinRequest;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.JoinResponse;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.LeaveRequest;
import io.camunda.eventbridge.client.internal.consumer.CoordinationMessages.LeaveResponse;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.SyncResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a consumer's group membership with the coordinator: join, leave, the scheduled heartbeat
 * loop, rejoin-on-fence, and offset commit. Owns the {@code memberId}/{@code memberEpoch} and, on
 * each heartbeat, applies assignment deltas or full reconciliations plus seeded committed offsets
 * into the {@link SubscriptionState} (under the {@link PrefetchBuffer} lock) and kicks the {@link
 * Prefetcher}.
 *
 * <p>All HTTP is routed through the single {@link HttpTransport} and is non-blocking: join, the
 * heartbeat loop, rejoin, and commit issue async requests and chain their completion. The scheduler
 * is used only for heartbeat cadence and backoff, never to park a thread on a request.
 */
public final class GroupCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(GroupCoordinator.class);

  private final HttpTransport transport;
  private final ScheduledExecutorService executor;
  private final SubscriptionState subscription;
  private final PrefetchBuffer buffer;
  private final Prefetcher prefetcher;
  private final BooleanSupplier closed;

  private final String groupId;
  private final List<String> topics;
  private final String instanceId;
  private final Runnable checkNotClosed;
  private final long heartbeatIntervalMs;

  // Written from executor threads (join/heartbeat/leave), read from caller threads (commit) — must
  // be volatile for visibility, since the enclosing consumer is documented as thread-safe.
  private volatile String memberId;
  private volatile long memberEpoch = 0;

  private volatile ScheduledFuture<?> scheduledHeartbeat;

  public GroupCoordinator(
      final HttpTransport transport,
      final ScheduledExecutorService executor,
      final SubscriptionState subscription,
      final PrefetchBuffer buffer,
      final Prefetcher prefetcher,
      final BooleanSupplier closed,
      final String groupId,
      final List<String> topics,
      final String instanceId,
      final Runnable checkNotClosed,
      final long heartbeatIntervalMs) {
    this.transport = transport;
    this.executor = executor;
    this.subscription = subscription;
    this.buffer = buffer;
    this.prefetcher = prefetcher;
    this.closed = closed;
    this.groupId = groupId;
    this.topics = topics;
    this.instanceId = instanceId;
    this.checkNotClosed = checkNotClosed;
    this.heartbeatIntervalMs = heartbeatIntervalMs;
  }

  public String memberId() {
    return memberId;
  }

  public long memberEpoch() {
    return memberEpoch;
  }

  /** Presets the epoch for the test-only pre-seeded consumer constructor. */
  public void presetEpoch(final long epoch) {
    memberEpoch = epoch;
  }

  /** Cancels the scheduled heartbeat, if any. Invoked when the consumer is closed. */
  public void cancelHeartbeat() {
    final var hb = scheduledHeartbeat;
    if (hb != null) {
      hb.cancel(false);
    }
  }

  public CompletableFuture<Void> joinGroup() {
    final var future = new CompletableFuture<Void>();
    transport
        .postJsonRaw(joinPath(), new JoinRequest(topics, instanceId), "join")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Join group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }
              if (response.statusCode() != 200) {
                return future.completeExceptionally(new RuntimeException("Failed to Join Group"));
              }

              final var body = transport.readBody(response.body(), JoinResponse.class, "join");
              applyJoinResponse(body);
              LOG.info(
                  "[JoinGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d; start send heartbeat"
                      .formatted(instanceId, groupId, body.errorCode(), memberId, memberEpoch));
              scheduleSendHeartbeat();
              return future.complete(null);
            },
            executor);
    return future;
  }

  /** Builds the {@code /v1/groups/{group}/join} path (shared by join and rejoin). */
  private String joinPath() {
    return "/v1/groups/" + URLEncoder.encode(groupId, StandardCharsets.UTF_8) + "/join";
  }

  /** Adopts the member id/epoch the coordinator assigned in a join/rejoin response. */
  private void applyJoinResponse(final JoinResponse body) {
    memberId = body.memberId();
    memberEpoch = body.memberEpoch() != null ? body.memberEpoch() : 0L;
  }

  public CompletableFuture<Void> leaveGroup() {
    final var future = new CompletableFuture<Void>();

    final var path =
        "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
            + "/leave";

    transport
        .postJsonRaw(path, new LeaveRequest(memberId, memberEpoch), "leave")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Leave group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }
              if (response.statusCode() != 200) {
                return future.completeExceptionally(new RuntimeException("Failed to Leave Group"));
              }

              final var body = transport.readBody(response.body(), LeaveResponse.class, "leave");
              LOG.info(
                  "[LeaveGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d"
                      .formatted(instanceId, groupId, body.errorCode(), memberId, memberEpoch));

              memberId = null;
              memberEpoch = -1;
              subscription.clearOwnedPartitions();

              final var hb = scheduledHeartbeat;
              if (hb != null) {
                hb.cancel(false);
              }
              return future.complete(null);
            },
            executor);

    return future;
  }

  private void scheduleSendHeartbeat() {
    if (closed.getAsBoolean()) {
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
            heartbeatIntervalMs,
            TimeUnit.MILLISECONDS);
  }

  public CompletableFuture<Void> sendHeartbeat() {
    checkNotClosed.run();

    final long snapshotEpoch = memberEpoch;
    final List<TopicPartition> snapshotOwned = subscription.ownedPartitions();

    final var path =
        "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8).replace("+", "%20")
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
            + "/heartbeat";

    // Chain the response handling on the executor so state mutations stay on a single-threaded
    // executor as before; the request itself runs on the HTTP client's executor (non-blocking).
    return transport
        .postJsonRaw(
            path, new HeartbeatRequest(memberEpoch, groupByTopic(snapshotOwned)), "heartbeat")
        .handleAsync(
            (httpResponse, error) ->
                onHeartbeatResponse(snapshotEpoch, snapshotOwned, httpResponse, error),
            executor)
        .thenCompose(future -> future);
  }

  /**
   * Applies a heartbeat response on the executor thread, preserving the original semantics
   * (stale-response discard, delta vs. full reconciliation, offset seeding, reschedule) and turning
   * a 409-fence into an async rejoin-then-reschedule. Returns the future the outer heartbeat future
   * completes from.
   */
  private CompletableFuture<Void> onHeartbeatResponse(
      final long snapshotEpoch,
      final List<TopicPartition> snapshotOwned,
      final SyncResponse httpResponse,
      final Throwable error) {
    if (error != null || httpResponse == null) {
      throw new CoordinatorUnavailableException(
          "Heartbeat HTTP request failed: " + (error != null ? error.getMessage() : "no response"));
    }

    if (httpResponse.statusCode() == 503) {
      throw new CoordinatorUnavailableException("Heartbeat rejected — coordinator unavailable");
    }
    if (httpResponse.statusCode() == 409) {
      // Fenced or unknown member (stale epoch, or the coordinator failed over and lost in-memory
      // membership). Re-register instead of heartbeating forever as a ghost.
      LOG.warn(
          "[Heartbeat][Consumer={}] membership fenced/unknown (409); rejoining group {}",
          instanceId,
          groupId);
      return rejoin().whenComplete((ignored, rejoinError) -> scheduleSendHeartbeat());
    }
    if (httpResponse.statusCode() != 200) {
      throw new EventBridgeException(
          "Heartbeat failed: HTTP " + httpResponse.statusCode() + " — " + httpResponse.body());
    }

    final var hb = transport.readBody(httpResponse.body(), HeartbeatResponse.class, "heartbeat");
    final long serverEpoch = hb.memberEpoch();

    if (serverEpoch < snapshotEpoch) {
      LOG.debug(
          "Ignoring stale heartbeat response (server epoch {} < client epoch {})",
          serverEpoch,
          snapshotEpoch);
      return CompletableFuture.completedFuture(null);
    }

    // Note: no explicit ACK is sent for either path. The coordinator confirms a revocation from the
    // ownedPartitions this consumer reports in its next heartbeat (see GroupReconciliation);
    // applying the change here and reporting it next beat is the acknowledgement.
    if (serverEpoch > snapshotEpoch) {
      // Full reconciliation: replace owned partitions wholesale from the full assignment.
      applyOwnedPartitions(flatten(hb.assignment()));
      memberEpoch = serverEpoch;
    } else {
      // Delta path (serverEpoch == snapshotEpoch).
      final var revoke = flatten(hb.revoke());
      final var assign = flatten(hb.assign());

      if (!revoke.isEmpty() || !assign.isEmpty()) {
        final var newOwned = new ArrayList<>(snapshotOwned);
        newOwned.removeAll(revoke);
        newOwned.addAll(assign);
        applyOwnedPartitions(newOwned);
      }
    }

    subscription.seedCommittedOffsets(hb.committedOffsets());

    // A reassignment or freshly seeded offset may have added fetchable partitions — start
    // prefetching so a poll() currently blocked on the buffer becomes responsive to them.
    prefetcher.kick();

    final var state = hb.errorCode();

    LOG.info(
        "[Heartbeat][Consumer=%s] Consumer Group %s (state %s): memberId %s, memberEpoch: %d, ownedPartitions: %s"
            .formatted(
                instanceId, groupId, state, memberId, memberEpoch, subscription.ownedPartitions()));

    scheduleSendHeartbeat();
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Re-registers this consumer with the coordinator after it has been fenced or the coordinator
   * lost its membership (e.g. a coordinator failover wiped the in-memory registry). Non-blocking:
   * the request is issued asynchronously and its result applied on the executor thread.
   *
   * <p>Acquires a fresh {@code memberId}/{@code memberEpoch} and drops owned partitions; the
   * coordinator reassigns them on the next heartbeat and re-seeds committed offsets. Fetch
   * positions for retained partitions are preserved (and only ever advanced via {@code max}), so
   * resumption is at-least-once.
   */
  public CompletableFuture<Void> rejoin() {
    return transport
        .postJsonRaw(joinPath(), new JoinRequest(topics, instanceId), "rejoin")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                throw new CoordinatorUnavailableException(
                    "Rejoin request failed: "
                        + (error != null ? error.getMessage() : "no response"));
              }
              if (response.statusCode() != 200) {
                throw new EventBridgeException(
                    "Rejoin failed: HTTP " + response.statusCode() + " — " + response.body());
              }

              applyJoinResponse(transport.readBody(response.body(), JoinResponse.class, "rejoin"));
              applyOwnedPartitions(List.of());

              LOG.info(
                  "[Rejoin][Consumer={}] group {} rejoined as member {} (epoch {})",
                  instanceId,
                  groupId,
                  memberId,
                  memberEpoch);
              return null;
            },
            executor);
  }

  public CompletableFuture<Void> commitOffset(
      final String topic, final int partitionId, final long position) {
    checkNotClosed.run();
    return doCommitOffset(topic, partitionId, position)
        .handle(
            (ignored, error) -> {
              final Throwable cause = unwrap(error);
              if (cause instanceof ConsumerNotRegisteredException) {
                LOG.warn(
                    "Consumer not registered on commitOffset; rejoining and retrying once: {}",
                    cause.getMessage());
                // Retry exactly once with fresh membership; any exception on the retry (including a
                // repeated ConsumerNotRegisteredException, e.g. the partition is no longer owned)
                // propagates.
                return rejoin().thenCompose(v -> doCommitOffset(topic, partitionId, position));
              }
              if (error != null) {
                return CompletableFuture.<Void>failedFuture(cause);
              }
              return CompletableFuture.<Void>completedFuture(null);
            })
        .thenCompose(future -> future);
  }

  private CompletableFuture<Void> doCommitOffset(
      final String topic, final int partitionId, final long position) {
    // Commit goes to the coordinator (owns membership + epoch), which fences stale commits.
    final String path =
        "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
            + "/commit";

    return transport
        .postJsonRaw(
            path, new CommitRequest(topic, partitionId, position, memberEpoch), "commitOffset")
        .thenApply(
            response -> {
              if (response.statusCode() == 404 || response.statusCode() == 409) {
                // 409: fenced/unknown member — the coordinator rejected the commit.
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
              return null;
            });
  }

  /** Unwraps a {@link CompletionException} to its underlying cause, if any. */
  private static Throwable unwrap(final Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }

  /**
   * Atomically replaces the owned partitions with a sorted copy of {@code partitions}, bumping the
   * fetch generation (in-flight fetches for moved partitions are discarded) and dropping buffers
   * for revoked partitions; retained partitions re-fetch from their preserved cursor on the next
   * kick.
   */
  public void applyOwnedPartitions(final List<TopicPartition> partitions) {
    final List<TopicPartition> sorted = subscription.sorted(partitions);
    buffer.runLocked(
        () -> {
          buffer.bumpGeneration();
          subscription.applyOwnedPartitions(sorted);
          buffer.retain(sorted);
        });
  }

  /** Groups owned partitions into the {@code topic -> [partition,...]} wire shape. */
  private static Map<String, List<Integer>> groupByTopic(final List<TopicPartition> partitions) {
    final Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
    for (final var tp : partitions) {
      byTopic.computeIfAbsent(tp.topic(), ignored -> new ArrayList<>()).add(tp.partition());
    }
    return byTopic;
  }

  /**
   * Flattens a {@code topic -> [partition,...]} assignment map into a {@link TopicPartition} list.
   */
  private static List<TopicPartition> flatten(final Map<String, List<Integer>> byTopic) {
    if (byTopic == null || byTopic.isEmpty()) {
      return List.of();
    }
    final List<TopicPartition> result = new ArrayList<>();
    byTopic.forEach(
        (topic, partitions) -> {
          if (partitions != null) {
            partitions.forEach(p -> result.add(new TopicPartition(topic, p)));
          }
        });
    return Collections.unmodifiableList(result);
  }
}
