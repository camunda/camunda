/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.broker.topology.TopologyService;
import io.camunda.eventbridge.core.EventDataBatch;
import io.camunda.eventbridge.core.protocol.ErrorCode;
import io.camunda.eventbridge.core.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.core.transport.MessageTypes;
import io.camunda.eventbridge.gateway.transport.SbeCodec.AckResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.CommitResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.FetchAssignmentResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.HeartbeatResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.LatestPositionResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.PollParams;
import io.camunda.eventbridge.gateway.transport.SbeCodec.PollResult;
import io.camunda.eventbridge.gateway.transport.SbeCodec.PublishBatchResult;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Routes gateway requests to the appropriate broker via {@link MessagingService} (Netty transport),
 * with configurable retry on transient failures.
 *
 * <h2>Retry policy</h2>
 *
 * <p>Each operation makes <strong>up to {@value #MAX_ATTEMPTS} total attempts</strong> (1 initial +
 * {@value #RETRIES} retries). An attempt is retried when:
 *
 * <ol>
 *   <li>A transport-level failure occurs ({@link ConnectException}, {@link TimeoutException}, or a
 *       {@link io.atomix.cluster.messaging.MessagingException.ConnectionClosed}), which may
 *       indicate a stale address.
 *   <li>The broker responds with {@link ErrorCode#LEADER_UNAVAILABLE} for partition-targeted
 *       operations (publish, poll, latest-position), which means the routing table is stale.
 * </ol>
 *
 * <p>If all attempts are exhausted, the returned future completes exceptionally with a {@link
 * BrokerException} carrying the relevant error code.
 *
 * <h2>Coordinator routing</h2>
 *
 * <p>Coordinator operations (heartbeat, commit-offset, fetch-assignment) are routed to the
 * coordinator address resolved from {@link TopologyService}. If the coordinator address is unknown,
 * the operation fails immediately with {@link ErrorCode#COORDINATOR_UNAVAILABLE}.
 *
 * <h2>Empty-address retries</h2>
 *
 * <p>When {@link TopologyService} returns an empty address (leader or coordinator unknown), the
 * attempt is counted against the {@value #MAX_ATTEMPTS} budget and the loop recurses immediately
 * without making a network call. This means that if the routing table has never been populated
 * (e.g. SWIM gossip has not yet converged), all {@value #MAX_ATTEMPTS} address-lookup checks may
 * complete without any I/O. Each empty-address check is logged at {@code DEBUG} level with the
 * label <em>"no I/O"</em> to make this visible in logs.
 *
 * <h2>Retry delay</h2>
 *
 * <p>A fixed delay of {@value #RETRY_DELAY_MS} ms is inserted before each retry (whether caused by
 * a transport failure, a retryable response, or an unknown address). This gives SWIM gossip time to
 * converge and prevents tight retry loops from saturating the thread pool when the leader is
 * temporarily unavailable.
 *
 * <p>This component requires an {@link AtomixCluster} bean in the Spring context (the bean is
 * optional; without it the router is inoperative and all methods return failed futures). The {@link
 * TopologyService} must also be available.
 */
@Component
public class BrokerRequestRouter {

  /** Maximum total attempts per operation (1 initial + 3 retries). */
  public static final int MAX_ATTEMPTS = 4;

  static final int RETRIES = MAX_ATTEMPTS - 1;

  /** Fixed timeout slack added to the poll server-wait time. */
  static final int POLL_TIMEOUT_SLACK_MS = 5_000;

  /** Default network round-trip timeout for non-poll operations. */
  static final int DEFAULT_TIMEOUT_MS = 5_000;

  /**
   * Fixed delay in milliseconds inserted before every retry (transport failure, retryable response,
   * or unknown address). Gives SWIM gossip time to converge between attempts.
   */
  static final int RETRY_DELAY_MS = 50;

  private static final Logger LOG = LoggerFactory.getLogger(BrokerRequestRouter.class);

  private final MessagingService messagingService;
  private final TopologyService topologyService;

  public BrokerRequestRouter(
      @Autowired(required = false) final AtomixCluster cluster,
      final TopologyService topologyService) {
    this.messagingService = cluster != null ? cluster.getMessagingService() : null;
    this.topologyService = topologyService;
  }

  // ---------------------------------------------------------------------------
  // Partition leader operations

  /**
   * Publishes an event batch to the given partition.
   *
   * @param partitionId target partition
   * @param batch the event batch to publish
   * @return future that resolves to the list of log positions assigned to each event in order;
   *     completes exceptionally with {@link BrokerException} on error
   */
  public CompletableFuture<List<Long>> publishBatch(
      final int partitionId, final EventDataBatch batch) {
    if (messagingService == null) {
      return unavailable("MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodePublishBatch(partitionId, batch);
    return sendToLeader(partitionId, MessageTypes.PRODUCE_REQUEST, request, DEFAULT_TIMEOUT_MS)
        .thenApply(
            bytes -> {
              final PublishBatchResult result = SbeCodec.decodePublishBatch(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return result.positions();
            });
  }

  /**
   * Pulls a batch of events from the given partition (long-poll supported).
   *
   * @param params all poll parameters; {@code serverWaitMs} must already be clamped to the
   *     broker-side ceiling before calling this method
   * @return future that resolves to the decoded poll result; completes exceptionally with {@link
   *     BrokerException} on hard errors (e.g. partition not found, invalid parameter)
   */
  public CompletableFuture<PollResult> poll(final PollParams params) {
    if (messagingService == null) {
      return unavailable("MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodePollRequest(params);
    final int timeoutMs = params.serverWaitMs() + POLL_TIMEOUT_SLACK_MS;
    return sendToLeader(params.partitionId(), MessageTypes.FETCH_REQUEST, request, timeoutMs)
        .thenApply(
            bytes -> {
              final PollResult result = SbeCodec.decodePollResponse(bytes);
              // REBALANCE_IN_PROGRESS is a valid non-error status; surface it to the caller.
              if (result.errorCode() != ErrorCode.NONE
                  && result.errorCode() != ErrorCode.REBALANCE_IN_PROGRESS) {
                requireSuccess(result.errorCode(), result.errorMessage());
              }
              return result;
            });
  }

  /**
   * Queries the highest committed log position on the given partition.
   *
   * @param partitionId target partition
   * @return future that resolves to the latest position (0 when the log is empty); completes
   *     exceptionally with {@link BrokerException} on error
   */
  public CompletableFuture<Long> getLatestPosition(final int partitionId) {
    if (messagingService == null) {
      return unavailable("MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodeLatestPositionRequest(partitionId);
    // LatestPositionResponse has 'position' (int64, 8 bytes) before 'errorCode' in the body.
    return sendToLeader(
            partitionId,
            MessageTypes.LATEST_POSITION_REQUEST,
            request,
            DEFAULT_TIMEOUT_MS,
            /* errorCodeBodyOffset= */ 8)
        .thenApply(
            bytes -> {
              final LatestPositionResult result = SbeCodec.decodeLatestPosition(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return result.position();
            });
  }

  // ---------------------------------------------------------------------------
  // Coordinator operations

  /**
   * Sends a consumer liveness heartbeat to the coordinator, carrying the consumer's current epoch
   * and owned partitions.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @param clientEpoch the epoch last observed by the consumer (0 on first heartbeat)
   * @param ownedPartitions partition IDs the consumer currently holds
   * @return future that resolves to the full heartbeat result (epoch, revoke, assign,
   *     fullAssignment); completes exceptionally with {@link BrokerException} on error
   */
  public CompletableFuture<HeartbeatResult> heartbeat(
      final String groupId,
      final String consumerId,
      final long clientEpoch,
      final List<Integer> ownedPartitions) {
    if (messagingService == null) {
      return unavailable(
          ErrorCode.COORDINATOR_UNAVAILABLE, "MessagingService not available (no AtomixCluster)");
    }
    final byte[] request =
        SbeCodec.encodeHeartbeat(groupId, consumerId, clientEpoch, ownedPartitions);
    return sendToCoordinator(MessageTypes.HEARTBEAT_REQUEST, request)
        .thenApply(
            bytes -> {
              final HeartbeatResult result = SbeCodec.decodeHeartbeat(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return result;
            });
  }

  /**
   * Acknowledges revoked and assigned partitions with the coordinator.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @param epoch the coordinator epoch at the time the assignment was issued
   * @param revoked partition IDs the consumer has stopped processing
   * @param assigned partition IDs the consumer has started processing
   * @return future that resolves to the ack result; completes exceptionally with {@link
   *     BrokerException} on error
   */
  public CompletableFuture<AckResult> ack(
      final String groupId,
      final String consumerId,
      final long epoch,
      final List<Integer> revoked,
      final List<Integer> assigned) {
    if (messagingService == null) {
      return unavailable(
          ErrorCode.COORDINATOR_UNAVAILABLE, "MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodeAck(groupId, consumerId, epoch, revoked, assigned);
    return sendToCoordinator(MessageTypes.ACK_REQUEST, request)
        .thenApply(
            bytes -> {
              final AckResult result = SbeCodec.decodeAckResponse(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return result;
            });
  }

  /**
   * Commits the consumed offset for a partition.
   *
   * @param partitionId target partition
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @param position offset to commit
   * @return future that completes when the commit is acknowledged; completes exceptionally with
   *     {@link BrokerException} on error
   */
  public CompletableFuture<Void> commitOffset(
      final int partitionId, final String groupId, final String consumerId, final long position) {
    if (messagingService == null) {
      return unavailable(
          ErrorCode.COORDINATOR_UNAVAILABLE, "MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodeCommitOffset(partitionId, groupId, consumerId, position);
    return sendToCoordinator(MessageTypes.COMMIT_OFFSET_REQUEST, request)
        .thenApply(
            bytes -> {
              final CommitResult result = SbeCodec.decodeCommitOffset(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return (Void) null;
            });
  }

  /**
   * Fetches the current partition assignment for a consumer without triggering a rebalance.
   *
   * @param groupId consumer group ID
   * @param consumerId consumer ID
   * @return future that resolves to the fetch assignment result; completes exceptionally with
   *     {@link BrokerException} on error
   */
  public CompletableFuture<FetchAssignmentResult> fetchAssignment(
      final String groupId, final String consumerId) {
    if (messagingService == null) {
      return unavailable(
          ErrorCode.COORDINATOR_UNAVAILABLE, "MessagingService not available (no AtomixCluster)");
    }
    final byte[] request = SbeCodec.encodeFetchAssignment(groupId, consumerId);
    return sendToCoordinator(MessageTypes.FETCH_ASSIGNMENT_REQUEST, request)
        .thenApply(
            bytes -> {
              final FetchAssignmentResult result = SbeCodec.decodeFetchAssignment(bytes);
              requireSuccess(result.errorCode(), result.errorMessage());
              return result;
            });
  }

  // ---------------------------------------------------------------------------
  // Core routing helpers

  /**
   * Sends a request to the current partition leader, retrying on transport failures and on {@link
   * ErrorCode#LEADER_UNAVAILABLE} responses.
   *
   * @param errorCodeBodyOffset byte offset within the message body where {@code errorCode} is
   *     encoded; 0 for most response types, 8 for {@code LatestPositionResponse} (which prefixes
   *     the {@code position} int64 field before {@code errorCode})
   */
  private CompletableFuture<byte[]> sendToLeader(
      final int partitionId,
      final String messageType,
      final byte[] payload,
      final int timeoutMs,
      final int errorCodeBodyOffset) {
    return sendWithRetry(
        () -> topologyService.getLeaderAddress(partitionId),
        messageType,
        payload,
        Duration.ofMillis(timeoutMs),
        responseBytes -> isLeaderUnavailableAt(responseBytes, errorCodeBodyOffset),
        MAX_ATTEMPTS,
        ErrorCode.LEADER_UNAVAILABLE);
  }

  /** Convenience overload for responses where {@code errorCode} is the first body field. */
  private CompletableFuture<byte[]> sendToLeader(
      final int partitionId, final String messageType, final byte[] payload, final int timeoutMs) {
    return sendToLeader(partitionId, messageType, payload, timeoutMs, 0);
  }

  /** Sends a request to the coordinator broker, retrying only on transport failures. */
  private CompletableFuture<byte[]> sendToCoordinator(
      final String messageType, final byte[] payload) {
    return sendWithRetry(
        topologyService::getCoordinatorAddress,
        messageType,
        payload,
        Duration.ofMillis(DEFAULT_TIMEOUT_MS),
        /* isRetryableResponse= */ responseBytes -> false,
        MAX_ATTEMPTS,
        ErrorCode.COORDINATOR_UNAVAILABLE);
  }

  /**
   * Core retry loop: sends {@code payload} to the address returned by {@code addressSupplier},
   * retrying up to {@code maxAttempts} times on transport errors or when {@code
   * isRetryableResponse} returns {@code true} for a received response.
   *
   * <p>All {@link CompletableFuture} callbacks execute on the thread-pool provided by {@link
   * MessagingService}; no actor or HTTP thread is blocked.
   *
   * @param addressSupplier resolves the target broker address for each attempt
   * @param messageType Netty message-type identifier (see {@link MessageTypes})
   * @param payload pre-encoded SBE request bytes (reused across retries)
   * @param timeout per-attempt network timeout
   * @param isRetryableResponse predicate on the raw response bytes; returns {@code true} when the
   *     application-level error in the response warrants a retry
   * @param attemptsLeft remaining attempts (decrements each recursive call)
   * @param exhaustedErrorCode the {@link ErrorCode} to use when all attempts fail
   * @return future that resolves to the raw response bytes, or completes exceptionally
   */
  private CompletableFuture<byte[]> sendWithRetry(
      final Supplier<Optional<Address>> addressSupplier,
      final String messageType,
      final byte[] payload,
      final Duration timeout,
      final Predicate<byte[]> isRetryableResponse,
      final int attemptsLeft,
      final ErrorCode exhaustedErrorCode) {

    final Optional<Address> addressOpt = addressSupplier.get();
    if (addressOpt.isEmpty()) {
      if (attemptsLeft > 1) {
        LOG.debug(
            "No known address for message type '{}' (no I/O attempted); {} attempt(s) remaining",
            messageType,
            attemptsLeft - 1);
        return delayedRetry(
            addressSupplier,
            messageType,
            payload,
            timeout,
            isRetryableResponse,
            attemptsLeft - 1,
            exhaustedErrorCode);
      }
      return CompletableFuture.failedFuture(
          new BrokerException(
              exhaustedErrorCode,
              "No known broker address for message type '" + messageType + "' after all attempts"));
    }

    final Address address = addressOpt.get();
    return messagingService
        .sendAndReceive(address, messageType, payload, timeout)
        .thenCompose(
            responseBytes ->
                handleResponse(
                    responseBytes,
                    addressSupplier,
                    messageType,
                    payload,
                    timeout,
                    isRetryableResponse,
                    attemptsLeft,
                    exhaustedErrorCode))
        .exceptionallyCompose(
            ex ->
                handleTransportError(
                    ex,
                    addressSupplier,
                    messageType,
                    payload,
                    timeout,
                    isRetryableResponse,
                    attemptsLeft,
                    exhaustedErrorCode));
  }

  private CompletableFuture<byte[]> handleResponse(
      final byte[] responseBytes,
      final Supplier<Optional<Address>> addressSupplier,
      final String messageType,
      final byte[] payload,
      final Duration timeout,
      final Predicate<byte[]> isRetryableResponse,
      final int attemptsLeft,
      final ErrorCode exhaustedErrorCode) {

    if (isRetryableResponse.test(responseBytes) && attemptsLeft > 1) {
      LOG.debug(
          "Retryable application error in response for '{}'; {} attempt(s) remaining",
          messageType,
          attemptsLeft - 1);
      return delayedRetry(
          addressSupplier,
          messageType,
          payload,
          timeout,
          isRetryableResponse,
          attemptsLeft - 1,
          exhaustedErrorCode);
    }
    return CompletableFuture.completedFuture(responseBytes);
  }

  private CompletableFuture<byte[]> handleTransportError(
      final Throwable ex,
      final Supplier<Optional<Address>> addressSupplier,
      final String messageType,
      final byte[] payload,
      final Duration timeout,
      final Predicate<byte[]> isRetryableResponse,
      final int attemptsLeft,
      final ErrorCode exhaustedErrorCode) {

    final Throwable cause = unwrapCompletionException(ex);
    if (isTransientTransportError(cause) && attemptsLeft > 1) {
      LOG.debug(
          "Transient transport error for '{}' ({}); {} attempt(s) remaining",
          messageType,
          cause.getClass().getSimpleName(),
          attemptsLeft - 1);
      return delayedRetry(
          addressSupplier,
          messageType,
          payload,
          timeout,
          isRetryableResponse,
          attemptsLeft - 1,
          exhaustedErrorCode);
    }
    LOG.warn("Routing to broker failed for message type '{}': {}", messageType, cause.getMessage());
    if (cause instanceof BrokerException be) {
      return CompletableFuture.failedFuture(be);
    }
    return CompletableFuture.failedFuture(
        new BrokerException(exhaustedErrorCode, cause.getMessage(), cause));
  }

  // ---------------------------------------------------------------------------
  // Predicates and helpers

  /**
   * Returns {@code true} when the SBE response indicates the partition leader is unavailable.
   *
   * <p>{@code bodyOffset} is the byte offset of the {@code errorCode} field within the message body
   * (i.e. after the 8-byte SBE header). For most response types this is 0; for {@code
   * LatestPositionResponse} it is 8 (the {@code position} int64 occupies bytes 0–7).
   */
  private static boolean isLeaderUnavailableAt(final byte[] responseBytes, final int bodyOffset) {
    final int absOffset = MessageHeaderDecoder.ENCODED_LENGTH + bodyOffset;
    if (responseBytes == null || responseBytes.length <= absOffset) {
      return false;
    }
    final short raw = (short) (responseBytes[absOffset] & 0xFF);
    return ErrorCode.get(raw) == ErrorCode.LEADER_UNAVAILABLE;
  }

  /** Returns {@code true} when the exception represents a transient transport failure. */
  static boolean isTransientTransportError(final Throwable cause) {
    return cause instanceof ConnectException
        || cause instanceof TimeoutException
        || cause instanceof io.atomix.cluster.messaging.MessagingException.ConnectionClosed
        || cause instanceof io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
  }

  /** Unwraps {@link java.util.concurrent.CompletionException} one level to get the actual cause. */
  private static Throwable unwrapCompletionException(final Throwable ex) {
    if (ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null) {
      return ex.getCause();
    }
    return ex;
  }

  /** Throws {@link BrokerException} if {@code errorCode} is not {@link ErrorCode#NONE}. */
  private static void requireSuccess(final ErrorCode errorCode, final String errorMessage) {
    if (errorCode != ErrorCode.NONE) {
      throw new BrokerException(errorCode, errorMessage);
    }
  }

  /**
   * Returns a {@link CompletableFuture} that has already failed with {@link
   * ErrorCode#LEADER_UNAVAILABLE} and the given message.
   */
  private static <T> CompletableFuture<T> unavailable(final String message) {
    return unavailable(ErrorCode.LEADER_UNAVAILABLE, message);
  }

  /**
   * Returns a {@link CompletableFuture} that has already failed with the given {@link ErrorCode}
   * and message.
   */
  private static <T> CompletableFuture<T> unavailable(
      final ErrorCode errorCode, final String message) {
    return CompletableFuture.failedFuture(new BrokerException(errorCode, message));
  }

  /**
   * Returns a future that, after {@link #RETRY_DELAY_MS} ms, calls {@code sendWithRetry} with the
   * given decremented attempt count. The delay runs on the common fork-join pool via {@link
   * CompletableFuture#delayedExecutor} so no actor or HTTP thread is parked.
   */
  private CompletableFuture<byte[]> delayedRetry(
      final Supplier<Optional<Address>> addressSupplier,
      final String messageType,
      final byte[] payload,
      final Duration timeout,
      final Predicate<byte[]> isRetryableResponse,
      final int attemptsLeft,
      final ErrorCode exhaustedErrorCode) {
    final Executor delayed =
        CompletableFuture.delayedExecutor(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    return CompletableFuture.supplyAsync(() -> null, delayed)
        .thenCompose(
            ignored ->
                sendWithRetry(
                    addressSupplier,
                    messageType,
                    payload,
                    timeout,
                    isRetryableResponse,
                    attemptsLeft,
                    exhaustedErrorCode));
  }
}
