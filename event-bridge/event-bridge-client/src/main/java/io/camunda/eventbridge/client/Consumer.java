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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer handle returned by {@link EventBridgeClient#subscribe(String, String, List)}.
 *
 * <p>Tracks the consumer's owned {@link TopicPartition}s, epoch, and per-partition {@code
 * nextPosition}. Call {@link #sendHeartbeat()} periodically to maintain group membership and
 * receive partition assignment deltas or full reconciliation signals from the coordinator.
 *
 * <p>All public methods are thread-safe.
 */
public final class Consumer {

  private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

  /** Sentinel for a newly assigned partition whose start offset has not been resolved yet. */
  private static final long UNSET_POSITION = Long.MIN_VALUE;

  /** Max bytes requested per (topic, partition) fetch in a poll sweep. */
  private static final int FETCH_MAX_BYTES = 1 << 20;

  /**
   * How long a background fetch parks on the broker (its {@code maxWaitMs}) before returning empty.
   * An idle partition issues at most one fetch per this interval — no client spin — while the
   * broker wakes it the instant data is committed (low latency when active).
   */
  private static final long LONG_POLL_MS = Long.getLong("eventbridge.consumer.longPollMs", 5_000L);

  /** Backoff before re-fetching a partition whose fetch failed (e.g. a leadership move). */
  private static final long FETCH_ERROR_BACKOFF_MS = 500L;

  private final ScheduledExecutorService executor;
  private final OffsetResetPolicy offsetResetPolicy;
  private final String groupId;
  private final List<String> topics;
  private final String instanceId;
  // Written from executor threads (join/heartbeat/leave), read from caller threads (poll, commit) —
  // must be volatile for visibility, since this class is documented as thread-safe.
  private volatile String memberId;
  private volatile long memberEpoch = 0;
  private final EventBridgeClient client;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * The (topic, partition)s currently owned by this consumer (sorted). Updated on every heartbeat
   * that returns a delta or full-reconciliation signal.
   */
  private volatile List<TopicPartition> ownedPartitions = List.of();

  /**
   * Per-partition fetch position. Initialized to {@code -1} (oldest retained) for newly assigned
   * partitions; updated after each successful {@link #poll(int, Duration)}.
   */
  private final ConcurrentHashMap<TopicPartition, Long> nextPositions = new ConcurrentHashMap<>();

  /**
   * Caller-requested start positions (see {@link #seek}). Consulted when a partition is assigned,
   * so the consumer resumes from a position the caller has durably checkpointed rather than from
   * the coordinator's committed offset or the reset policy.
   */
  private final ConcurrentHashMap<TopicPartition, Long> startPositions = new ConcurrentHashMap<>();

  // --- Background prefetch (Kafka ConsumerNetworkThread + FetchBuffer style) --------------------
  // A background driver keeps at most one long-poll fetch in flight per owned partition; results
  // land in a per-partition buffer. poll() only drains the buffers, blocking on a condition up to
  // its timeout. So an idle poll parks on the broker (no spin), and fetch N+1 overlaps the caller's
  // processing of batch N (pipelining). Like Kafka's FetchBuffer, buffer access is guarded by a
  // lock and the wait/signal by its condition; a partition is only re-fetched once its buffer
  // drains
  // (natural back-pressure).
  private final ReentrantLock bufferLock = new ReentrantLock();
  private final Condition dataAvailable = bufferLock.newCondition();
  private final Map<TopicPartition, ArrayDeque<Event>> buffers = new LinkedHashMap<>(); // guarded
  private final Set<TopicPartition> inFlight = new HashSet<>(); // guarded by bufferLock
  private int fetchGeneration; // bumped on seek/reassignment to discard stale in-flight fetches

  private volatile ScheduledFuture<?> scheduledHeartbeat;

  /** Primary constructor. Consumer starts with no owned partitions and {@code currentEpoch = 0}. */
  Consumer(
      final String groupId,
      final List<String> topics,
      final String instanceId,
      final EventBridgeClient client) {
    this.groupId = groupId;
    this.topics = topics == null ? List.of() : List.copyOf(topics);
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
      final List<TopicPartition> initialPartitions,
      final int initialEpoch,
      final EventBridgeClient client) {
    this.groupId = groupId;
    topics = List.of();
    instanceId = consumerId;
    memberEpoch = initialEpoch;
    this.client = client;
    executor = client.getExecutor();
    offsetResetPolicy = client.getOffsetResetPolicy();
    applyOwnedPartitions(initialPartitions);
  }

  public CompletableFuture<Void> joinGroup() {
    final var future = new CompletableFuture<Void>();
    client
        .getHttpClient()
        .sendAsync(joinRequest(), HttpResponse.BodyHandlers.ofString())
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

              final var body = client.readBody(response.body(), JoinResponse.class, "join");
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

  /** Builds the {@code POST /v1/groups/{group}/join} request (shared by join and rejoin). */
  private HttpRequest joinRequest() {
    return HttpRequest.newBuilder()
        .uri(
            URI.create(
                client.getGatewayUrl()
                    + "/v1/groups/"
                    + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                    + "/join"))
        .header("Content-Type", "application/json")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                client.writeBody(new JoinRequest(topics, instanceId), "join")))
        .build();
  }

  /** Adopts the member id/epoch the coordinator assigned in a join/rejoin response. */
  private void applyJoinResponse(final JoinResponse body) {
    memberId = body.memberId();
    memberEpoch = body.memberEpoch() != null ? body.memberEpoch() : 0L;
  }

  public CompletableFuture<Void> leaveGroup() {
    final var future = new CompletableFuture<Void>();

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
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    client.writeBody(new LeaveRequest(memberId, memberEpoch), "leave")))
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
              if (response.statusCode() != 200) {
                return future.completeExceptionally(new RuntimeException("Failed to Leave Group"));
              }

              final var body = client.readBody(response.body(), LeaveResponse.class, "leave");
              LOG.info(
                  "[LeaveGroup][Consumer=%s] Consumer Group %s (state %s) with Member ID %s and Member Epoch %d"
                      .formatted(instanceId, groupId, body.errorCode(), memberId, memberEpoch));

              memberId = null;
              memberEpoch = -1;
              ownedPartitions = Collections.emptyList();

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
   * Requests that the next fetch for each given (topic, partition) start at the supplied position,
   * resuming from a caller-checkpointed offset rather than the coordinator's committed offset or
   * the reset policy. Pass the position <em>after</em> the last record the caller durably processed
   * (i.e. {@code lastProcessed + 1}).
   *
   * <p>Safe to call before or after the partition is assigned: it takes effect immediately for
   * already-owned partitions and is remembered for partitions assigned later. A later
   * coordinator-committed offset never rewinds it (positions only advance via {@code max}), so a
   * caller whose durable checkpoint is ahead of the committed offset resumes from the checkpoint.
   */
  public void seek(final Map<TopicPartition, Long> positions) {
    bufferLock.lock();
    try {
      // Invalidate any in-flight fetch started from the old cursor and drop stale buffered events,
      // so the next fetch resumes from the seeked position.
      fetchGeneration++;
      positions.forEach(
          (tp, position) -> {
            startPositions.put(tp, position);
            nextPositions.merge(tp, position, Math::max);
            final ArrayDeque<Event> buf = buffers.get(tp);
            if (buf != null) {
              buf.clear();
            }
          });
    } finally {
      bufferLock.unlock();
    }
  }

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
    final HttpResponse<String> response;
    try {
      response = client.getHttpClient().send(joinRequest(), HttpResponse.BodyHandlers.ofString());
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

    applyJoinResponse(client.readBody(response.body(), JoinResponse.class, "rejoin"));
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
          final List<TopicPartition> snapshotOwned = ownedPartitions;

          final var requestBody =
              client.writeBody(
                  new HeartbeatRequest(memberEpoch, groupByTopic(snapshotOwned)), "heartbeat");

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

          final var hb = client.readBody(httpResponse.body(), HeartbeatResponse.class, "heartbeat");
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

          seedCommittedOffsets(hb.committedOffsets());

          // A reassignment or freshly seeded offset may have added fetchable partitions — start
          // prefetching so a poll() currently blocked on the buffer becomes responsive to them.
          kick();

          final var state = hb.errorCode();

          LOG.info(
              "[Heartbeat][Consumer=%s] Consumer Group %s (state %s): memberId %s, memberEpoch: %d, ownedPartitions: %s"
                  .formatted(instanceId, groupId, state, memberId, memberEpoch, ownedPartitions));

          scheduleSendHeartbeat();
        },
        executor);
  }

  /**
   * Returns the next batch of prefetched events across all owned (topic, partition)s, in sorted
   * partition order, blocking up to {@code timeout} for the background prefetcher to deliver at
   * least one record.
   *
   * <p>This does not itself hit the network: a background driver keeps a long-poll fetch in flight
   * per owned partition (see {@link #kick()}) and fills a per-partition buffer, so an idle poll
   * parks on the broker (no spin), a multi-partition consumer returns as soon as <em>any</em>
   * partition delivers, and the next fetch overlaps the caller's processing of this batch
   * (pipelining).
   *
   * @param maxRecords maximum total number of records to return across all partitions
   * @param timeout maximum time to wait for records to become available
   * @return list of events fetched (may be empty if none arrived within {@code timeout})
   * @throws ConsumerClosedException if {@link #close()} has been called
   */
  public List<Event> poll(final int maxRecords, final Duration timeout) {
    checkNotClosed();
    kick(); // ensure a fetch is in flight for every empty, owned partition

    final List<Event> out = new ArrayList<>();
    final long deadlineNanos = System.nanoTime() + Math.max(0L, timeout.toNanos());
    bufferLock.lock();
    try {
      while (!closed.get() && isBufferEmpty()) {
        final long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
          return out; // nothing arrived within the timeout
        }
        try {
          dataAvailable.awaitNanos(remaining);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return out;
        }
      }
      drainInto(out, maxRecords);
    } finally {
      bufferLock.unlock();
    }
    kick(); // pipeline: refill the partitions we just drained while the caller processes this batch
    return out;
  }

  /**
   * Drains up to {@code maxRecords} buffered events in sorted partition order. Caller holds lock.
   */
  private void drainInto(final List<Event> out, final int maxRecords) {
    final var partitions = new ArrayList<>(buffers.keySet());
    Collections.sort(partitions);
    for (final TopicPartition tp : partitions) {
      final ArrayDeque<Event> buf = buffers.get(tp);
      while (buf != null && !buf.isEmpty() && out.size() < maxRecords) {
        out.add(buf.poll());
      }
      if (out.size() >= maxRecords) {
        break;
      }
    }
  }

  /** True if no owned partition has buffered events. Caller holds {@link #bufferLock}. */
  private boolean isBufferEmpty() {
    for (final ArrayDeque<Event> buf : buffers.values()) {
      if (!buf.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Ensures every owned partition with an empty buffer and no in-flight fetch has a long-poll fetch
   * issued. Idempotent and cheap to call often (on poll, after each fetch completes): the in-flight
   * set prevents duplicate fetches, and a partition is only re-fetched once its buffer drains.
   */
  private void kick() {
    if (closed.get()) {
      return;
    }
    final List<TopicPartition> toFetch = new ArrayList<>();
    final int gen;
    bufferLock.lock();
    try {
      gen = fetchGeneration;
      for (final TopicPartition tp : ownedPartitions) {
        final ArrayDeque<Event> buf = buffers.get(tp);
        if (!inFlight.contains(tp) && (buf == null || buf.isEmpty())) {
          inFlight.add(tp);
          toFetch.add(tp);
        }
      }
    } finally {
      bufferLock.unlock();
    }
    for (final TopicPartition tp : toFetch) {
      issueFetch(tp, gen);
    }
  }

  /** Issues a single long-poll fetch for {@code tp} from its current fetch cursor. */
  private void issueFetch(final TopicPartition tp, final int gen) {
    long from = nextPositions.getOrDefault(tp, -1L);
    if (from == UNSET_POSITION) {
      from = resolveStartPosition(tp);
      nextPositions.put(tp, from);
    }
    final long fromPosition = from;
    client
        .fetchFromTopic(tp.topic(), tp.partition(), fromPosition, FETCH_MAX_BYTES, 0, LONG_POLL_MS)
        .whenComplete((result, error) -> onFetchComplete(tp, gen, fromPosition, result, error));
  }

  /** Handles a completed background fetch: buffer new events, then re-arm. */
  private void onFetchComplete(
      final TopicPartition tp,
      final int gen,
      final long fromPosition,
      final FetchResult result,
      final Throwable error) {
    boolean retryNow = false;
    boolean retryDelayed = false;
    bufferLock.lock();
    try {
      inFlight.remove(tp);
      if (gen != fetchGeneration || !ownedPartitions.contains(tp)) {
        return; // a seek/reassignment happened while this fetch was in flight — discard its result
      }
      if (error != null) {
        LOG.debug("Fetch failed for {}; will retry", tp, error);
        retryDelayed = true;
      } else if (result != null && result.isSuccess()) {
        final ArrayDeque<Event> buf = buffers.computeIfAbsent(tp, ignored -> new ArrayDeque<>());
        long next = fromPosition;
        for (final var entry : result.entries(fromPosition)) {
          buf.add(new Event(entry.getPosition(), tp.topic(), tp.partition(), entry.getValueCopy()));
          next = entry.getPosition() + 1;
        }
        if (buf.isEmpty()) {
          // Parked long-poll returned empty (still at the tip) — re-arm immediately; the emptiness
          // already cost LONG_POLL_MS of server-side waiting, so this is not a spin.
          retryNow = true;
        } else {
          nextPositions.put(tp, next);
          dataAvailable.signalAll();
        }
      } else if (result != null && result.statusCode() == 416) {
        // OFFSET_OUT_OF_RANGE — cursor below the earliest retained record. Mark unresolved so the
        // next fetch re-applies the reset policy (resolved off-lock in issueFetch).
        LOG.warn("Fetch out of range for {} at {}; resetting", tp, fromPosition);
        nextPositions.put(tp, UNSET_POSITION);
        retryNow = true;
      } else {
        retryDelayed = true;
      }
    } finally {
      bufferLock.unlock();
    }
    if (retryNow) {
      kick();
    } else if (retryDelayed && !closed.get()) {
      executor.schedule(this::kick, FETCH_ERROR_BACKOFF_MS, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Commits the consumed offset for the given (topic, partition).
   *
   * <p>If the coordinator reports that this consumer is no longer registered (HTTP 404), the client
   * automatically rejoins and retries the commit exactly once. If the single retry also fails, the
   * exception is propagated to the caller.
   *
   * @param topic topic the partition belongs to
   * @param partitionId partition to commit
   * @param position the log position that has been fully processed
   * @return a future that completes when the commit is acknowledged
   */
  public CompletableFuture<Void> commitOffset(
      final String topic, final int partitionId, final long position) {
    checkNotClosed();
    return CompletableFuture.runAsync(
        () -> {
          checkNotClosed();
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

  /**
   * Closes this consumer handle. Idempotent. After close, all method calls throw {@link
   * ConsumerClosedException}.
   */
  public void close() {
    closed.set(true);
    if (scheduledHeartbeat != null) {
      scheduledHeartbeat.cancel(false);
    }
    // Wake any poll() blocked on the buffer so it observes the closed flag and returns.
    bufferLock.lock();
    try {
      dataAvailable.signalAll();
    } finally {
      bufferLock.unlock();
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

  /** Returns an unmodifiable snapshot of the (topic, partition)s currently owned. */
  public List<TopicPartition> getOwnedPartitions() {
    return Collections.unmodifiableList(ownedPartitions);
  }

  // -------------------------------------------------------------------------
  // Internal helpers

  private void doCommitOffset(final String topic, final int partitionId, final long position) {
    // Commit goes to the coordinator (owns membership + epoch), which fences stale commits.
    final String url =
        client.getGatewayUrl()
            + "/v1/groups/"
            + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
            + "/consumers/"
            + URLEncoder.encode(memberId, StandardCharsets.UTF_8)
            + "/commit";
    final var jsonBody =
        client.writeBody(
            new CommitRequest(topic, partitionId, position, memberEpoch), "commitOffset");
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
  private long resolveStartPosition(final TopicPartition tp) {
    if (offsetResetPolicy == OffsetResetPolicy.LATEST) {
      try {
        final long highWatermark =
            client.fetchFromTopic(tp.topic(), tp.partition(), 0, 4096).join().highWatermark();
        return highWatermark < 0 ? 0L : highWatermark + 1;
      } catch (final RuntimeException e) {
        LOG.warn("Failed to resolve LATEST start for {}; falling back to earliest", tp, e);
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
  private void seedCommittedOffsets(final Map<String, Map<Integer, Long>> committed) {
    // committedOffsets is grouped {topic -> {partition -> offset}}.
    if (committed == null) {
      return;
    }
    committed.forEach(
        (topic, offsets) -> {
          if (offsets == null) {
            return;
          }
          offsets.forEach(
              (partition, position) -> {
                if (position == null) {
                  return;
                }
                final var tp = new TopicPartition(topic, partition);
                if (ownedPartitions.contains(tp)) {
                  nextPositions.merge(tp, position, Math::max);
                }
              });
        });
  }

  /**
   * Atomically replaces {@code ownedPartitions} with a sorted copy of {@code partitions}, updating
   * {@code nextPositions} to drop revoked partitions and initialise newly assigned ones.
   */
  private void applyOwnedPartitions(final List<TopicPartition> partitions) {
    final var sorted = new ArrayList<>(partitions);
    Collections.sort(sorted);
    bufferLock.lock();
    try {
      // Invalidate in-flight fetches (a partition may have moved leaders) and drop buffers for
      // revoked partitions; retained partitions re-fetch from their preserved cursor on next kick.
      fetchGeneration++;
      nextPositions.keySet().retainAll(sorted);
      buffers.keySet().retainAll(sorted);
      inFlight.retainAll(sorted);
      for (final var tp : sorted) {
        // Newly assigned: prefer a caller-requested start (seek), else start unresolved. A
        // committed
        // offset (seedCommittedOffsets) or the reset policy (resolved on first fetch) then
        // determines where an unresolved partition actually starts.
        nextPositions.putIfAbsent(tp, startPositions.getOrDefault(tp, UNSET_POSITION));
      }
      ownedPartitions = Collections.unmodifiableList(sorted);
    } finally {
      bufferLock.unlock();
    }
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

  private void checkNotClosed() {
    if (closed.get()) {
      throw new ConsumerClosedException(
          "Consumer " + memberId + " in group " + groupId + " is closed");
    }
  }

  // ---------------------------------------------------------------------------
  // Coordination wire shapes (gateway JSON). Field names must match the gateway DTOs.

  private record JoinRequest(List<String> topics, String instanceId) {}

  private record JoinResponse(String errorCode, String memberId, Long memberEpoch) {}

  private record LeaveRequest(String memberId, long memberEpoch) {}

  private record LeaveResponse(String errorCode) {}

  private record CommitRequest(String topic, int partitionId, long position, long memberEpoch) {}

  private record HeartbeatRequest(long epoch, Map<String, List<Integer>> ownedPartitions) {}

  private record HeartbeatResponse(
      String errorCode,
      String memberId,
      long memberEpoch,
      Map<String, List<Integer>> revoke,
      Map<String, List<Integer>> assign,
      long assignmentEpoch,
      Map<String, List<Integer>> assignment,
      Map<String, Map<Integer, Long>> committedOffsets) {}
}
