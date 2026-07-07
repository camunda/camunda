/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.api.proto.CommitRequest;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatRequest;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.IntList;
import io.camunda.eventbridge.api.proto.JoinRequest;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.api.proto.OffsetMap;
import io.camunda.eventbridge.client.ConsumerNotRegisteredException;
import io.camunda.eventbridge.client.CoordinatorUnavailableException;
import io.camunda.eventbridge.client.EventBridgeException;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
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
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>All coordination messages travel as binary protobuf through the single {@link HttpTransport}
 * and are non-blocking: join, the heartbeat loop, rejoin, and commit issue async requests and chain
 * their completion. The scheduler is used only for heartbeat cadence and backoff, never to park a
 * thread on a request.
 */
public final class GroupCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(GroupCoordinator.class);

  private final HttpTransport transport;
  private final ScheduledExecutorService executor;
  private static final RebalanceListener NO_OP_REBALANCE_LISTENER = new RebalanceListener() {};

  private volatile RebalanceListener rebalanceListener = NO_OP_REBALANCE_LISTENER;
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

  // Single-flight guard for rejoin(): with a static instanceId the coordinator holds the instance
  // slot for the live incumbent and fences any *concurrent* second join, so overlapping rejoins
  // must be coalesced onto one request (see rejoin()).
  private final AtomicReference<CompletableFuture<Void>> inFlightRejoin = new AtomicReference<>();

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
        .postProtobufRaw(joinPath(), joinRequest(), "join")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Join group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }
              if (response.statusCode() != 201) {
                return future.completeExceptionally(new RuntimeException("Failed to Join Group"));
              }

              final var body = transport.parse(response.body(), JoinResponse.parser(), "join");
              applyJoinResponse(body);
              LOG.info(
                  "[JoinGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d; start send heartbeat"
                      .formatted(instanceId, groupId, body.getErrorCode(), memberId, memberEpoch));
              scheduleSendHeartbeat();
              return future.complete(null);
            },
            executor);
    return future;
  }

  /** Builds the {@code /v1/groups/{group}/members} path (shared by join and rejoin). */
  private String joinPath() {
    return "/v1/groups/" + URLEncoder.encode(groupId, StandardCharsets.UTF_8) + "/members";
  }

  /** Builds the join/rejoin request body. */
  private JoinRequest joinRequest() {
    return JoinRequest.newBuilder().addAllTopics(topics).setInstanceId(instanceId).build();
  }

  /** Adopts the member id/epoch the coordinator assigned in a join/rejoin response. */
  private void applyJoinResponse(final JoinResponse body) {
    memberId = body.getMemberId();
    memberEpoch = body.getMemberEpoch();
  }

  public CompletableFuture<Void> leaveGroup() {
    final var future = new CompletableFuture<Void>();

    // Leaving a member is a DELETE; the epoch travels as a query parameter and a successful
    // removal replies 204 No Content (no body to parse).
    final var path =
        "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/members/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
            + "?epoch="
            + memberEpoch;

    transport
        .deleteRaw(path, "leave")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                return future.completeExceptionally(
                    new CoordinatorUnavailableException(
                        "Leave group request failed: "
                            + (error != null ? error.getMessage() : "no response")));
              }
              if (response.statusCode() != 204) {
                return future.completeExceptionally(new RuntimeException("Failed to Leave Group"));
              }

              LOG.info(
                  "[LeaveGroup][Consumer=%s] Consumer Group %s with Member ID %s and Member Epoch %d"
                      .formatted(instanceId, groupId, memberId, memberEpoch));

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
    // A heartbeat may be scheduled from more than one trigger — the initial join and any manual
    // sendHeartbeat() both land here on success. Cancel the previously scheduled beat before
    // rescheduling; otherwise each trigger starts its own self-rescheduling chain and the consumer
    // heartbeats at a multiple of the configured interval forever.
    final ScheduledFuture<?> previous = scheduledHeartbeat;
    if (previous != null) {
      previous.cancel(false);
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
            + "/members/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8).replace("+", "%20")
            + "/heartbeat";

    final var body =
        ConsumerHeartbeatRequest.newBuilder()
            .setEpoch(memberEpoch)
            .putAllOwnedPartitions(groupByTopic(snapshotOwned))
            .build();

    // Chain the response handling on the executor so state mutations stay on a single-threaded
    // executor as before; the request itself runs on the HTTP client's executor (non-blocking).
    return transport
        .postProtobufRaw(path, body, "heartbeat")
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
      final BinaryResponse httpResponse,
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
      // Rejoin (coalesced), then schedule exactly one next beat. Swallow the rejoin error here — it
      // is already logged, and returning a normally-completed future keeps the outer heartbeat
      // error
      // handler from scheduling a *second* beat, which would double the heartbeat rate every fence.
      return rejoin()
          .handle((ignored, rejoinError) -> (Void) null)
          .whenComplete((ignored, ignoredError) -> scheduleSendHeartbeat());
    }
    if (httpResponse.statusCode() != 200) {
      throw new EventBridgeException("Heartbeat failed: HTTP " + httpResponse.statusCode());
    }

    final var hb =
        transport.parse(httpResponse.body(), ConsumerHeartbeatResponse.parser(), "heartbeat");
    final long serverEpoch = hb.getMemberEpoch();

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
      applyOwnedPartitions(flatten(hb.getAssignmentMap()));
      memberEpoch = serverEpoch;
    } else {
      // Delta path (serverEpoch == snapshotEpoch).
      final var revoke = flatten(hb.getRevokeMap());
      final var assign = flatten(hb.getAssignMap());

      if (!revoke.isEmpty() || !assign.isEmpty()) {
        final var newOwned = new ArrayList<>(snapshotOwned);
        newOwned.removeAll(revoke);
        newOwned.addAll(assign);
        applyOwnedPartitions(newOwned);
      }
    }

    subscription.seedCommittedOffsets(committedOffsets(hb.getCommittedOffsetsMap()));

    // A reassignment or freshly seeded offset may have added fetchable partitions — start
    // prefetching so a poll() currently blocked on the buffer becomes responsive to them.
    prefetcher.kick();

    final var state = hb.getErrorCode();

    LOG.info(
        "[Heartbeat][Consumer=%s] Consumer Group %s (state %s): memberId %s, memberEpoch: %d, ownedPartitions: %s"
            .formatted(
                instanceId, groupId, state, memberId, memberEpoch, subscription.ownedPartitions()));

    scheduleSendHeartbeat();
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Re-registers this consumer with the coordinator after it has been fenced or the coordinator
   * lost its membership (e.g. a coordinator failover wiped the in-memory registry), coalescing
   * concurrent rejoins onto a single in-flight request.
   *
   * <p>A static {@code instanceId} means the coordinator holds the instance slot for the live
   * incumbent and rejects a <em>second</em> concurrent join with {@code UNRELEASED_INSTANCE_ID}
   * (409); since many in-flight operations (every fenced commit, plus the heartbeat) each want to
   * rejoin when a member is lost, letting them each fire a join makes all but one 409 and retry — a
   * self-sustaining storm that exhausts sockets. Single-flight means one join per fence: every
   * caller awaits the same fresh membership.
   */
  public CompletableFuture<Void> rejoin() {
    while (true) {
      final var existing = inFlightRejoin.get();
      if (existing != null) {
        return existing;
      }
      final var promise = new CompletableFuture<Void>();
      if (inFlightRejoin.compareAndSet(null, promise)) {
        doRejoin()
            .whenComplete(
                (value, error) -> {
                  inFlightRejoin.compareAndSet(promise, null);
                  if (error != null) {
                    promise.completeExceptionally(error);
                  } else {
                    promise.complete(value);
                  }
                });
        return promise;
      }
      // Lost the CAS race; loop to return the winner's future.
    }
  }

  /**
   * Issues the actual rejoin request (non-blocking): acquires a fresh {@code memberId}/{@code
   * memberEpoch} and drops owned partitions; the coordinator reassigns them on the next heartbeat
   * and re-seeds committed offsets. Fetch positions for retained partitions are preserved (and only
   * ever advanced via {@code max}), so resumption is at-least-once. Callers go through {@link
   * #rejoin()} so concurrent attempts are coalesced.
   */
  private CompletableFuture<Void> doRejoin() {
    return transport
        .postProtobufRaw(joinPath(), joinRequest(), "rejoin")
        .handleAsync(
            (response, error) -> {
              if (error != null || response == null) {
                throw new CoordinatorUnavailableException(
                    "Rejoin request failed: "
                        + (error != null ? error.getMessage() : "no response"));
              }
              if (response.statusCode() != 201) {
                throw new EventBridgeException("Rejoin failed: HTTP " + response.statusCode());
              }

              applyJoinResponse(transport.parse(response.body(), JoinResponse.parser(), "rejoin"));
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
            + "/members/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
            + "/offsets";

    final var body =
        CommitRequest.newBuilder()
            .setTopic(topic)
            .setPartitionId(partitionId)
            .setPosition(position)
            .setMemberEpoch(memberEpoch)
            .build();

    return transport
        .postProtobufRaw(path, body, "commitOffset")
        .thenApply(
            response -> {
              if (response.statusCode() == 404 || response.statusCode() == 409) {
                // 409: fenced/unknown member — the coordinator rejected the commit.
                throw new ConsumerNotRegisteredException(groupId, memberId);
              }
              if (response.statusCode() == 400) {
                throw new EventBridgeException("commitOffset failed: HTTP 400");
              }
              if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new EventBridgeException(
                    "commitOffset failed: HTTP " + response.statusCode());
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
    final List<TopicPartition> previous = subscription.ownedPartitions();
    buffer.runLocked(
        () -> {
          buffer.bumpGeneration();
          subscription.applyOwnedPartitions(sorted);
          buffer.retain(sorted);
        });
    notifyRebalance(previous, sorted);
  }

  /** Sets the rebalance listener (never null); replaces any previous one. */
  void setRebalanceListener(final RebalanceListener listener) {
    rebalanceListener = listener == null ? NO_OP_REBALANCE_LISTENER : listener;
  }

  /**
   * Notifies the listener of the assignment delta, outside the buffer lock so the listener may call
   * back into the consumer (e.g. {@code seekToBeginning}) without deadlock. Revoked first, then
   * assigned, mirroring the acquire-after-release ordering a caller expects.
   */
  private void notifyRebalance(
      final List<TopicPartition> previous, final List<TopicPartition> current) {
    final List<TopicPartition> revoked = new ArrayList<>(previous);
    revoked.removeAll(current);
    final List<TopicPartition> assigned = new ArrayList<>(current);
    assigned.removeAll(previous);
    if (!revoked.isEmpty()) {
      rebalanceListener.onPartitionsRevoked(revoked);
    }
    if (!assigned.isEmpty()) {
      rebalanceListener.onPartitionsAssigned(assigned);
    }
  }

  /** Groups owned partitions into the {@code topic -> IntList} wire shape. */
  private static Map<String, IntList> groupByTopic(final List<TopicPartition> partitions) {
    final Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
    for (final var tp : partitions) {
      byTopic.computeIfAbsent(tp.topic(), ignored -> new ArrayList<>()).add(tp.partition());
    }
    final Map<String, IntList> result = new LinkedHashMap<>();
    byTopic.forEach(
        (topic, partitionsForTopic) ->
            result.put(topic, IntList.newBuilder().addAllValues(partitionsForTopic).build()));
    return result;
  }

  /** Flattens a {@code topic -> IntList} assignment map into a {@link TopicPartition} list. */
  private static List<TopicPartition> flatten(final Map<String, IntList> byTopic) {
    if (byTopic == null || byTopic.isEmpty()) {
      return List.of();
    }
    final List<TopicPartition> result = new ArrayList<>();
    byTopic.forEach(
        (topic, partitions) ->
            partitions.getValuesList().forEach(p -> result.add(new TopicPartition(topic, p))));
    return Collections.unmodifiableList(result);
  }

  /** Converts the committed-offset wire shape into {@code topic -> (partition -> offset)}. */
  private static Map<String, Map<Integer, Long>> committedOffsets(
      final Map<String, OffsetMap> byTopic) {
    if (byTopic == null || byTopic.isEmpty()) {
      return Map.of();
    }
    final Map<String, Map<Integer, Long>> result = new LinkedHashMap<>();
    byTopic.forEach(
        (topic, offsets) -> result.put(topic, new LinkedHashMap<>(offsets.getOffsetsMap())));
    return result;
  }
}
