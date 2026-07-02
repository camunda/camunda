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
 * <p>All HTTP is routed through the single {@link HttpTransport}: join uses an async send; the
 * heartbeat loop, rejoin, and commit run on executor threads and use the blocking send.
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
      final Runnable checkNotClosed) {
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
            3,
            TimeUnit.SECONDS);
  }

  public CompletableFuture<Void> sendHeartbeat() {
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed.run();

          final long snapshotEpoch = memberEpoch;
          final List<TopicPartition> snapshotOwned = subscription.ownedPartitions();

          final var path =
              "/v1/groups/"
                  + URLEncoder.encode(groupId, StandardCharsets.UTF_8).replace("+", "%20")
                  + "/consumers/"
                  + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
                  + "/heartbeat";

          final SyncResponse httpResponse;
          try {
            httpResponse =
                transport.sendJsonSync(
                    path,
                    new HeartbeatRequest(memberEpoch, groupByTopic(snapshotOwned)),
                    "heartbeat");
          } catch (final EventBridgeException e) {
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

          final var hb =
              transport.readBody(httpResponse.body(), HeartbeatResponse.class, "heartbeat");
          final long serverEpoch = hb.memberEpoch();

          if (serverEpoch < snapshotEpoch) {
            LOG.debug(
                "Ignoring stale heartbeat response (server epoch {} < client epoch {})",
                serverEpoch,
                snapshotEpoch);
            return;
          }

          // Note: no explicit ACK is sent for either path. The coordinator confirms a revocation
          // from the ownedPartitions this consumer reports in its next heartbeat (see
          // GroupReconciliation); applying the change here and reporting it next beat is the
          // acknowledgement.
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
                      instanceId,
                      groupId,
                      state,
                      memberId,
                      memberEpoch,
                      subscription.ownedPartitions()));

          scheduleSendHeartbeat();
        },
        executor);
  }

  /**
   * Re-registers this consumer with the coordinator after it has been fenced or the coordinator
   * lost its membership (e.g. a coordinator failover wiped the in-memory registry). Synchronous:
   * callers are already on an executor thread (the heartbeat loop or a commit retry).
   *
   * <p>Acquires a fresh {@code memberId}/{@code memberEpoch} and drops owned partitions; the
   * coordinator reassigns them on the next heartbeat and re-seeds committed offsets. Fetch
   * positions for retained partitions are preserved (and only ever advanced via {@code max}), so
   * resumption is at-least-once.
   */
  public void rejoinSync() {
    final SyncResponse response;
    try {
      response = transport.sendJsonSync(joinPath(), new JoinRequest(topics, instanceId), "rejoin");
    } catch (final CoordinatorUnavailableException e) {
      throw e;
    } catch (final EventBridgeException e) {
      throw new CoordinatorUnavailableException("Rejoin request failed: " + e.getMessage());
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
  }

  public CompletableFuture<Void> commitOffset(
      final String topic, final int partitionId, final long position) {
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed.run();
          try {
            doCommitOffset(topic, partitionId, position);
          } catch (final ConsumerNotRegisteredException e) {
            LOG.warn(
                "Consumer not registered on commitOffset; rejoining and retrying once: {}",
                e.getMessage());
            rejoinSync();
            // Retry exactly once with the fresh membership; any exception (including a repeated
            // ConsumerNotRegisteredException, e.g. the partition is no longer owned) propagates.
            doCommitOffset(topic, partitionId, position);
          }
        });
  }

  private void doCommitOffset(final String topic, final int partitionId, final long position) {
    // Commit goes to the coordinator (owns membership + epoch), which fences stale commits.
    final String path =
        "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
            + "/commit";

    final SyncResponse response =
        transport.sendJsonSync(
            path, new CommitRequest(topic, partitionId, position, memberEpoch), "commitOffset");

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
