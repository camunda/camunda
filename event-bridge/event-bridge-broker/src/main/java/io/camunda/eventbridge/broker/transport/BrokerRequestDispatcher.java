/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.PollActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.CommitOffsetRequest;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.FetchAssignmentRequest;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.HeartbeatRequest;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.PollRequest;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.PublishBatchRequest;
import io.camunda.eventbridge.broker.transport.BrokerSbeCodec.SubscribeRequest;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.transport.MessageTypes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Netty {@link MessagingService} handlers for all Event Bridge gateway↔broker protocol
 * messages and dispatches each request to the appropriate broker actor.
 *
 * <h2>Message flow</h2>
 *
 * <pre>
 * Gateway (SBE-encoded request via Netty)
 *   → BrokerRequestDispatcher.handleXxx()
 *   → Decode SBE → call actor method (returns ActorFuture)
 *   → ActorFuture.toCompletableFuture() thenApply encode SBE response
 *   → Netty sends response bytes back to gateway
 * </pre>
 *
 * <h2>Partition-targeted vs coordinator-targeted messages</h2>
 *
 * <ul>
 *   <li><strong>Partition-targeted</strong> ({@code PRODUCE_REQUEST}, {@code FETCH_REQUEST}, {@code
 *       LATEST_POSITION_REQUEST}): routed to the {@link PublishActor} or {@link PollActor} for the
 *       requested {@code partitionId}. If the partition ID is unknown (no actor registered),
 *       responds with {@link ErrorCode#PARTITION_NOT_FOUND}.
 *   <li><strong>Coordinator-targeted</strong> ({@code SUBSCRIBE_REQUEST}, {@code
 *       HEARTBEAT_REQUEST}, {@code COMMIT_OFFSET_REQUEST}, {@code FETCH_ASSIGNMENT_REQUEST}):
 *       always routed to the single {@link CoordinatorActor}.
 * </ul>
 *
 * <h2>Error handling</h2>
 *
 * <p>Actor futures that complete exceptionally are mapped to error SBE responses. The mapping is
 * best-effort: a generic {@link ErrorCode#LEADER_UNAVAILABLE} is used for unexpected actor failures
 * on partition operations; {@link ErrorCode#CONSUMER_NOT_REGISTERED} is used for {@link
 * CoordinatorActor.ConsumerNotRegisteredException} on coordinator operations.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Call {@link #start()} to register all handlers and {@link #stop()} to unregister them. Both
 * methods are idempotent when called from the Spring {@link
 * org.springframework.context.SmartLifecycle} callbacks.
 */
public final class BrokerRequestDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerRequestDispatcher.class);

  private final MessagingService messagingService;
  private final Map<Integer, PublishActor> publishActors;
  private final Map<Integer, PollActor> pollActors;
  private final CoordinatorActor coordinatorActor;

  public BrokerRequestDispatcher(
      final MessagingService messagingService,
      final Map<Integer, PublishActor> publishActors,
      final Map<Integer, PollActor> pollActors,
      final CoordinatorActor coordinatorActor) {
    this.messagingService = messagingService;
    this.publishActors = publishActors;
    this.pollActors = pollActors;
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Registers all Netty message handlers. Safe to call multiple times; subsequent calls after the
   * first are no-ops from the perspective of the {@link MessagingService} (handler replace).
   */
  public void start() {
    LOG.info("Registering Event Bridge broker request handlers");
    messagingService.registerHandler(MessageTypes.PRODUCE_REQUEST, this::handlePublish);
    messagingService.registerHandler(MessageTypes.FETCH_REQUEST, this::handlePoll);
    messagingService.registerHandler(
        MessageTypes.LATEST_POSITION_REQUEST, this::handleLatestPosition);
    messagingService.registerHandler(MessageTypes.SUBSCRIBE_REQUEST, this::handleSubscribe);
    messagingService.registerHandler(MessageTypes.HEARTBEAT_REQUEST, this::handleHeartbeat);
    messagingService.registerHandler(MessageTypes.COMMIT_OFFSET_REQUEST, this::handleCommitOffset);
    messagingService.registerHandler(
        MessageTypes.FETCH_ASSIGNMENT_REQUEST, this::handleFetchAssignment);
  }

  /**
   * Unregisters all Netty message handlers. Safe to call when not started; the {@link
   * MessagingService} silently ignores unregister calls for unknown types.
   */
  public void stop() {
    LOG.info("Unregistering Event Bridge broker request handlers");
    messagingService.unregisterHandler(MessageTypes.PRODUCE_REQUEST);
    messagingService.unregisterHandler(MessageTypes.FETCH_REQUEST);
    messagingService.unregisterHandler(MessageTypes.LATEST_POSITION_REQUEST);
    messagingService.unregisterHandler(MessageTypes.SUBSCRIBE_REQUEST);
    messagingService.unregisterHandler(MessageTypes.HEARTBEAT_REQUEST);
    messagingService.unregisterHandler(MessageTypes.COMMIT_OFFSET_REQUEST);
    messagingService.unregisterHandler(MessageTypes.FETCH_ASSIGNMENT_REQUEST);
  }

  // ---------------------------------------------------------------------------
  // Partition-targeted handlers

  private CompletableFuture<byte[]> handlePublish(final Address sender, final byte[] requestBytes) {
    final PublishBatchRequest req;
    try {
      req = BrokerSbeCodec.decodePublishBatch(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode PublishBatchRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodePublishBatchError(
              ErrorCode.INVALID_REQUEST, "Failed to decode request: " + e.getMessage()));
    }

    final PublishActor actor = publishActors.get(req.partitionId());
    if (actor == null) {
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodePublishBatchError(
              ErrorCode.PARTITION_NOT_FOUND,
              "Partition " + req.partitionId() + " does not exist on this broker"));
    }

    return actor
        .publishBatch(req.eventBatch())
        .toCompletableFuture()
        .thenApply(BrokerSbeCodec::encodePublishBatchSuccess)
        .exceptionally(
            t -> {
              final Throwable cause = unwrap(t);
              if (cause instanceof IllegalArgumentException) {
                return BrokerSbeCodec.encodePublishBatchError(
                    ErrorCode.INVALID_REQUEST, rootMessage(t));
              }
              LOG.error("Publish failed on partition {} (sender={})", req.partitionId(), sender, t);
              return BrokerSbeCodec.encodePublishBatchError(
                  ErrorCode.LEADER_UNAVAILABLE, rootMessage(t));
            });
  }

  private CompletableFuture<byte[]> handlePoll(final Address sender, final byte[] requestBytes) {
    final PollRequest req;
    try {
      req = BrokerSbeCodec.decodePoll(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode PollRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodePollError(
              ErrorCode.INVALID_REQUEST, "Failed to decode request: " + e.getMessage()));
    }

    final PollActor actor = pollActors.get(req.partitionId());
    if (actor == null) {
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodePollError(
              ErrorCode.PARTITION_NOT_FOUND,
              "Partition " + req.partitionId() + " does not exist on this broker"));
    }

    return actor
        .poll(req.fromPosition(), req.maxRecords(), req.serverWaitMs())
        .toCompletableFuture()
        .thenApply(
            pollResult -> {
              final List<BrokerSbeCodec.PollResponseEvent> events =
                  pollResult.events().stream()
                      .map(e -> new BrokerSbeCodec.PollResponseEvent(e.position(), e.payload()))
                      .collect(Collectors.toList());
              return BrokerSbeCodec.encodePollSuccess(events, pollResult.nextPosition(), 0L);
            })
        .exceptionally(
            t -> {
              final Throwable cause = unwrap(t);
              if (cause instanceof PollActor.PositionTruncatedException truncated) {
                return BrokerSbeCodec.encodePollError(
                    ErrorCode.POSITION_TRUNCATED,
                    "Position truncated; oldest available: "
                        + truncated.getOldestAvailablePosition());
              }
              if (cause instanceof PollActor.InvalidPositionException) {
                return BrokerSbeCodec.encodePollError(
                    ErrorCode.INVALID_REQUEST, "Invalid fromPosition: " + rootMessage(t));
              }
              LOG.error("Poll failed on partition {} (sender={})", req.partitionId(), sender, t);
              return BrokerSbeCodec.encodePollError(ErrorCode.LEADER_UNAVAILABLE, rootMessage(t));
            });
  }

  private CompletableFuture<byte[]> handleLatestPosition(
      final Address sender, final byte[] requestBytes) {
    final int partitionId;
    try {
      partitionId = BrokerSbeCodec.decodeLatestPositionRequest(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode LatestPositionRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeLatestPosition(
              ErrorCode.INVALID_REQUEST, 0L, "Failed to decode request: " + e.getMessage()));
    }

    final PollActor actor = pollActors.get(partitionId);
    if (actor == null) {
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeLatestPosition(
              ErrorCode.PARTITION_NOT_FOUND,
              0L,
              "Partition " + partitionId + " does not exist on this broker"));
    }

    return actor
        .getLatestPosition()
        .toCompletableFuture()
        .thenApply(pos -> BrokerSbeCodec.encodeLatestPosition(ErrorCode.NONE, pos, ""))
        .exceptionally(
            t -> {
              LOG.error(
                  "LatestPosition failed on partition {} (sender={})", partitionId, sender, t);
              return BrokerSbeCodec.encodeLatestPosition(
                  ErrorCode.LEADER_UNAVAILABLE, 0L, rootMessage(t));
            });
  }

  // ---------------------------------------------------------------------------
  // Coordinator-targeted handlers

  private CompletableFuture<byte[]> handleSubscribe(
      final Address sender, final byte[] requestBytes) {
    final SubscribeRequest req;
    try {
      req = BrokerSbeCodec.decodeSubscribe(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode SubscribeRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeSubscribeError(
              ErrorCode.INVALID_REQUEST, "Failed to decode request: " + e.getMessage()));
    }

    return coordinatorActor
        .subscribe(req.groupId(), req.consumerId())
        .toCompletableFuture()
        .thenApply(
            result ->
                BrokerSbeCodec.encodeSubscribeSuccess(
                    result.generation(), result.assignedPartitions()))
        .exceptionally(
            t -> {
              LOG.error(
                  "Subscribe failed for group={} consumer={} (sender={})",
                  req.groupId(),
                  req.consumerId(),
                  sender,
                  t);
              return BrokerSbeCodec.encodeSubscribeError(
                  ErrorCode.COORDINATOR_UNAVAILABLE, rootMessage(t));
            });
  }

  private CompletableFuture<byte[]> handleHeartbeat(
      final Address sender, final byte[] requestBytes) {
    final HeartbeatRequest req;
    try {
      req = BrokerSbeCodec.decodeHeartbeat(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode HeartbeatRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeHeartbeat(
              ErrorCode.INVALID_REQUEST, 0L, "Failed to decode request: " + e.getMessage()));
    }

    return coordinatorActor
        .heartbeat(req.groupId(), req.consumerId())
        .toCompletableFuture()
        .thenApply(generation -> BrokerSbeCodec.encodeHeartbeat(ErrorCode.NONE, generation, ""))
        .exceptionally(
            t -> {
              final Throwable cause = unwrap(t);
              if (cause instanceof CoordinatorActor.ConsumerNotRegisteredException) {
                return BrokerSbeCodec.encodeHeartbeat(
                    ErrorCode.CONSUMER_NOT_REGISTERED, 0L, rootMessage(t));
              }
              LOG.error(
                  "Heartbeat failed for group={} consumer={} (sender={})",
                  req.groupId(),
                  req.consumerId(),
                  sender,
                  t);
              return BrokerSbeCodec.encodeHeartbeat(
                  ErrorCode.COORDINATOR_UNAVAILABLE, 0L, rootMessage(t));
            });
  }

  private CompletableFuture<byte[]> handleCommitOffset(
      final Address sender, final byte[] requestBytes) {
    final CommitOffsetRequest req;
    try {
      req = BrokerSbeCodec.decodeCommitOffset(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode CommitOffsetRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeCommitOffset(
              ErrorCode.INVALID_REQUEST, "Failed to decode request: " + e.getMessage()));
    }

    return coordinatorActor
        .commitOffset(req.groupId(), req.consumerId(), req.partitionId(), req.position())
        .toCompletableFuture()
        .thenApply(ignored -> BrokerSbeCodec.encodeCommitOffset(ErrorCode.NONE, ""))
        .exceptionally(
            t -> {
              final Throwable cause = unwrap(t);
              if (cause instanceof CoordinatorActor.ConsumerNotRegisteredException) {
                return BrokerSbeCodec.encodeCommitOffset(
                    ErrorCode.CONSUMER_NOT_REGISTERED, rootMessage(t));
              }
              LOG.error(
                  "CommitOffset failed for group={} consumer={} partition={} (sender={})",
                  req.groupId(),
                  req.consumerId(),
                  req.partitionId(),
                  sender,
                  t);
              return BrokerSbeCodec.encodeCommitOffset(
                  ErrorCode.COORDINATOR_UNAVAILABLE, rootMessage(t));
            });
  }

  private CompletableFuture<byte[]> handleFetchAssignment(
      final Address sender, final byte[] requestBytes) {
    final FetchAssignmentRequest req;
    try {
      req = BrokerSbeCodec.decodeFetchAssignment(requestBytes);
    } catch (final Exception e) {
      LOG.warn("Failed to decode FetchAssignmentRequest from {}", sender, e);
      return CompletableFuture.completedFuture(
          BrokerSbeCodec.encodeFetchAssignmentError(
              ErrorCode.INVALID_REQUEST, "Failed to decode request: " + e.getMessage()));
    }

    return coordinatorActor
        .getAssignment(req.groupId(), req.consumerId())
        .toCompletableFuture()
        .thenApply(
            result ->
                BrokerSbeCodec.encodeFetchAssignmentSuccess(
                    result.generation(), result.assignedPartitions()))
        .exceptionally(
            t -> {
              final Throwable cause = unwrap(t);
              if (cause instanceof CoordinatorActor.ConsumerNotRegisteredException) {
                return BrokerSbeCodec.encodeFetchAssignmentError(
                    ErrorCode.CONSUMER_NOT_REGISTERED, rootMessage(t));
              }
              LOG.error(
                  "FetchAssignment failed for group={} consumer={} (sender={})",
                  req.groupId(),
                  req.consumerId(),
                  sender,
                  t);
              return BrokerSbeCodec.encodeFetchAssignmentError(
                  ErrorCode.COORDINATOR_UNAVAILABLE, rootMessage(t));
            });
  }

  // ---------------------------------------------------------------------------
  // Helpers

  /**
   * Unwraps one level of {@link java.util.concurrent.ExecutionException} or {@link
   * java.lang.reflect.UndeclaredThrowableException} so that actor-thrown exceptions are visible to
   * {@code instanceof} checks.
   */
  private static Throwable unwrap(final Throwable t) {
    if ((t instanceof java.util.concurrent.ExecutionException
            || t instanceof java.util.concurrent.CompletionException
            || t instanceof java.lang.reflect.UndeclaredThrowableException)
        && t.getCause() != null) {
      return t.getCause();
    }
    return t;
  }

  /** Returns the message of the root cause (after a single unwrap). */
  private static String rootMessage(final Throwable t) {
    final Throwable cause = unwrap(t);
    final String msg = cause.getMessage();
    return msg != null ? msg : cause.getClass().getSimpleName();
  }
}
