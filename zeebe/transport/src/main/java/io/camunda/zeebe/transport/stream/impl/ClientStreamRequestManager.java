/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.cluster.messaging.MessagingException.ProtocolException;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.stream.impl.ClientStreamRegistration.State;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamResponse;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamResponse;
import io.camunda.zeebe.transport.stream.impl.messages.StreamResponseDecoder;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates registering/de-registering {@link AggregatedClientStream} with arbitrary set of
 * servers.
 *
 * <p>Each stream carries its own {@code physicalTenantId}; this manager uses that value to form the
 * correct physicalTenantId-scoped topic for every ADD/REMOVE/REMOVE_ALL message, so that streams
 * are only registered with brokers that serve their physical tenant.
 *
 * <p>NOTE: all operations are potentially asynchronous as network I/O is involved. To avoid race
 * conditions w.r.t to the stream lifecycle, the manager keeps track of the registration state per
 * server via a {@link ClientStreamRegistration}. A registration follows a strict lifecycle via a
 * state machine: INITIAL -> ADDING -> ADDED -> REMOVING -> REMOVED -> CLOSED. As such,
 * registrations cannot be reused, and are recreated per stream/host pair.
 *
 * <p>This class is NOT thread-safe, and must be executed within the same thread context as the
 * {@link ClientStreamManager}.
 */
final class ClientStreamRequestManager<M extends BufferWriter> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientStreamRequestManager.class);
  private static final byte[] REMOVE_ALL_REQUEST = new byte[0];
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  // maps the registration state,  for each known host, of each stream
  private final Map<MemberId, Map<UUID, ClientStreamRegistration<M>>> registrations =
      new HashMap<>();
  private final StreamResponseDecoder responseDecoder = new StreamResponseDecoder();

  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;

  ClientStreamRequestManager(
      final ClusterCommunicationService communicationService, final ConcurrencyControl executor) {
    this.communicationService = communicationService;
    this.executor = executor;
  }

  /**
   * Registers the given stream to all the given servers. See {@link #add(AggregatedClientStream,
   * MemberId)} for the individual operation.
   *
   * @param stream the stream to register
   * @param serverIds the list of servers on which to register the stream
   * @throws IllegalStateException if the stream was already removed, is currently being removed, or
   *     has been closed, on any of the servers
   */
  void add(final AggregatedClientStream<M> stream, final Collection<MemberId> serverIds) {
    for (final var serverId : serverIds) {
      add(stream, serverId);
    }
  }

  /**
   * Registers the given stream to all the given servers. When a stream is finally registered
   * remotely to a specific server, its {@link AggregatedClientStream#isConnected(MemberId)} will
   * return true.
   *
   * <p>This operation is idempotent: if the stream is already registered on a server, it will not
   * be registered again.
   *
   * <p>NOTE: if the stream is currently being removed, this will throw an exception. The idea is
   * that if the stream is being removed, then it doesn't exist in the top level registry anymore,
   * and shouldn't be reused.
   *
   * @param stream the stream to register
   * @param serverId the server on which to register the stream
   * @throws IllegalStateException if the stream was already removed, is currently being removed, or
   *     has been closed
   */
  void add(final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var registration = registrationFor(stream, serverId);
    add(registration);
  }

  /**
   * De-registers a given stream from all given servers. See {@link #remove(AggregatedClientStream,
   * MemberId)} for more.
   *
   * @param stream the stream to remove
   * @param serverIds the list of servers to remove it from
   */
  void remove(final AggregatedClientStream<M> stream, final Collection<MemberId> serverIds) {
    for (final var serverId : serverIds) {
      remove(stream, serverId);
    }
  }

  /**
   * De-registers the stream from the given server. Does nothing if the stream was already closed or
   * is already removed, or in the process of being removed, from the server. This operation is
   * fully idempotent.
   *
   * @param stream the stream to remove
   * @param serverId the server on which it should be removed
   */
  void remove(final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var streamsPerHost = registrations.get(serverId);
    if (streamsPerHost == null) {
      return;
    }

    final var registration = streamsPerHost.get(stream.streamId());
    if (registration != null) {
      remove(registration);
    }
  }

  /**
   * Sends REMOVE_ALL to each broker on its physicalTenantId-scoped topic, and closes all pending
   * registrations.
   *
   * @param serversByPhysicalTenantId physicalTenantId → set of broker member IDs
   */
  void removeAll(final Map<String, Set<MemberId>> serversByPhysicalTenantId) {
    registrations.values().stream()
        .flatMap(m -> m.values().stream())
        .forEach(ClientStreamRegistration::transitionToClosed);
    registrations.clear();

    serversByPhysicalTenantId.forEach(
        (physicalTenantId, servers) ->
            servers.forEach(brokerId -> doRemoveAll(brokerId, physicalTenantId)));
  }

  /**
   * Send remove stream request to servers without waiting for ack and without retry. As this is
   * used to send even when no stream was originally registered, we ignore any existing
   * registrations. Iterates all known physical tenants since the stream's tenant is unknown at this
   * point.
   */
  void removeUnreliable(
      final UUID streamId, final Map<String, Set<MemberId>> serversByPhysicalTenantId) {
    final var request = new RemoveStreamRequest().streamId(streamId);
    final var payload = BufferUtil.bufferAsArray(request);

    serversByPhysicalTenantId.forEach(
        (physicalTenantId, servers) ->
            servers.forEach(
                serverId -> {
                  communicationService.unicast(
                      StreamTopics.REMOVE.topic(physicalTenantId),
                      payload,
                      Function.identity(),
                      serverId,
                      true);
                  legacyUnicastIfDefaultTenant(
                      StreamTopics.REMOVE, physicalTenantId, payload, serverId);
                  purgeRegistration(streamId, serverId);
                }));
  }

  private void add(final ClientStreamRegistration<M> registration) {
    if (registration.state() == State.ADDING || !registration.transitionToAdding()) {
      return;
    }

    final var request =
        new AddStreamRequest()
            .streamId(registration.streamId())
            .streamType(registration.logicalId().streamType())
            .metadata(registration.logicalId().metadata());

    final var pendingRequest = registration.pendingRequest();
    if (pendingRequest != null) {
      // error - should not have a pending request if we're registering!
      throw new IllegalStateException(
          "Failed to add remote client stream %s to %s; there is an incomplete pending request"
              .formatted(registration.streamId(), registration.serverId()));
    }

    final var payload = BufferUtil.bufferAsArray(request);
    final var added = new CompletableFuture<Void>();
    final var legacyAdded = new CompletableFuture<Void>();

    sendAddRequest(registration, payload, added);
    sendLegacyAddRequestIfDefaultTenant(registration, payload, legacyAdded);

    CompletableFuture.allOf(added, legacyAdded)
        .whenCompleteAsync((ok, error) -> registration.transitionToAdded(), executor::run);
  }

  private void remove(final ClientStreamRegistration<M> registration) {
    if (registration.state() == State.INITIAL) {
      // bail early, as we never added this stream
      registration.transitionToRemoved();
      return;
    }

    if (registration.state() == State.REMOVING || !registration.transitionToRemoving()) {
      return;
    }

    final var request = new RemoveStreamRequest().streamId(registration.streamId());
    final var payload = BufferUtil.bufferAsArray(request);

    final var previousRequests =
        Stream.of(registration.pendingRequest(), registration.legacyPendingRequest())
            .filter(Objects::nonNull)
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new);
    if (previousRequests.length == 0) {
      sendRemoveRequestAndLegacy(registration, payload);
      return;
    }

    // to minimize the likelihood of out-of-order requests on the server side, wait until any
    // current request (primary or legacy) is finished before sending the next one, regardless of
    // the result
    CompletableFuture.allOf(previousRequests)
        .whenCompleteAsync(
            (ok, error) -> sendRemoveRequestAndLegacy(registration, payload), executor::run);
  }

  private void sendRemoveRequestAndLegacy(
      final ClientStreamRegistration<M> registration, final byte[] payload) {
    final var removed = new CompletableFuture<Void>();
    final var legacyRemoved = new CompletableFuture<Void>();

    sendRemoveRequest(registration, payload, removed);
    sendLegacyRemoveRequestIfDefaultTenant(registration, payload, legacyRemoved);

    CompletableFuture.allOf(removed, legacyRemoved)
        .whenCompleteAsync(
            (ok, error) -> {
              registration.transitionToRemoved();
              purgeRegistration(registration.streamId(), registration.serverId());
            },
            executor::run);
  }

  /**
   * Closes and removes the pending registrations for this server that belong to {@code
   * physicalTenantId}, leaving any registrations for other physical tenants the server may still
   * serve untouched.
   */
  void onServerRemoved(final MemberId serverId, final String physicalTenantId) {
    final var perHost = registrations.get(serverId);
    if (perHost == null) {
      return;
    }

    final var toClose =
        perHost.values().stream()
            .filter(registration -> physicalTenantId.equals(registration.physicalTenantId()))
            .toList();
    if (toClose.isEmpty()) {
      return;
    }

    LOGGER.trace(
        "Closing registrations for server {} and physical tenant {}", serverId, physicalTenantId);
    toClose.forEach(
        registration -> {
          registration.transitionToClosed();
          perHost.remove(registration.streamId());
        });

    if (perHost.isEmpty()) {
      registrations.remove(serverId);
    }
  }

  @VisibleForTesting("Allows easier test set up and validation")
  ClientStreamRegistration<M> registrationFor(
      final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var streamsPerHost = registrations.computeIfAbsent(serverId, ignored -> new HashMap<>());
    return streamsPerHost.computeIfAbsent(
        stream.streamId(), streamId -> new ClientStreamRegistration<>(stream, serverId));
  }

  private void sendAddRequest(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> added) {
    if (registration.state() != State.ADDING) {
      added.complete(null);
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.ADD.topic(registration.physicalTenantId()),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) ->
            handleAddResponse(
                registration,
                response,
                error,
                () -> sendAddRequest(registration, request, added),
                added),
        executor::run);
  }

  /** Rolling-upgrade compat; remove alongside the legacy topic in 8.11. */
  private void sendLegacyAddRequestIfDefaultTenant(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> legacyAdded) {
    if (!DEFAULT_PHYSICAL_TENANT_ID.equals(registration.physicalTenantId())) {
      legacyAdded.complete(null);
      return;
    }

    sendLegacyAddRequest(registration, request, legacyAdded);
  }

  private void sendLegacyAddRequest(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> legacyAdded) {
    if (registration.state() != State.ADDING) {
      legacyAdded.complete(null);
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.ADD.legacyTopic(),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setLegacyPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) ->
            handleAddResponse(
                registration,
                response,
                error,
                () -> sendLegacyAddRequest(registration, request, legacyAdded),
                legacyAdded),
        executor::run);
  }

  private void handleAddResponse(
      final ClientStreamRegistration<M> registration,
      final byte[] responseBuffer,
      final @Nullable Throwable error,
      final Runnable retry,
      final CompletableFuture<Void> added) {
    final var state = registration.state();
    if (state != State.ADDING) {
      LOGGER.trace("Skip handling ADD response since the state is {}", state, error);
      added.complete(null);
      return;
    }

    final Throwable failure;
    final Either<ErrorResponse, AddStreamResponse> response;
    if (error == null) {
      response = responseDecoder.decode(responseBuffer, new AddStreamResponse());
      if (response.isRight()) {
        added.complete(null);
        return;
      }

      failure = response.getLeft().asException();
    } else {
      failure = error;
    }

    // For now, always retry; in some cases, the request will eventually go through. However, in
    // some cases it never will. This should be covered in a follow-up issue.
    // Unrecoverable cases:
    //   - RemoteHandlerError
    //   - ErrorResponse.code in [ MALFORMED, INVALID ]
    LOGGER.warn(
        "Failed to add stream {} on {}; will retry in {}",
        registration.streamId(),
        registration.serverId(),
        RETRY_DELAY,
        failure);
    executor.schedule(RETRY_DELAY, retry);
  }

  private void sendRemoveRequest(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> removed) {
    if (registration.state() != State.REMOVING) {
      removed.complete(null);
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.REMOVE.topic(registration.physicalTenantId()),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) ->
            handleRemoveResponse(
                registration,
                response,
                error,
                () -> sendRemoveRequest(registration, request, removed),
                removed),
        executor::run);
  }

  /** Rolling-upgrade compat; remove alongside the legacy topic in 8.11. */
  private void sendLegacyRemoveRequestIfDefaultTenant(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> legacyRemoved) {
    if (!DEFAULT_PHYSICAL_TENANT_ID.equals(registration.physicalTenantId())) {
      legacyRemoved.complete(null);
      return;
    }

    sendLegacyRemoveRequest(registration, request, legacyRemoved);
  }

  private void sendLegacyRemoveRequest(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final CompletableFuture<Void> legacyRemoved) {
    if (registration.state() != State.REMOVING) {
      legacyRemoved.complete(null);
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.REMOVE.legacyTopic(),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setLegacyPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) ->
            handleRemoveResponse(
                registration,
                response,
                error,
                () -> sendLegacyRemoveRequest(registration, request, legacyRemoved),
                legacyRemoved),
        executor::run);
  }

  private void handleRemoveResponse(
      final ClientStreamRegistration<M> registration,
      final byte[] responseBuffer,
      final @Nullable Throwable error,
      final Runnable retry,
      final CompletableFuture<Void> removed) {
    final var state = registration.state();
    if (state != State.REMOVING) {
      LOGGER.trace("Skip handling REMOVE response since the state is {}", state, error);
      removed.complete(null);
      return;
    }

    final Throwable failure;
    final Either<ErrorResponse, RemoveStreamResponse> response;
    if (error == null) {
      response = responseDecoder.decode(responseBuffer, new RemoveStreamResponse());
      if (response.isRight()) {
        removed.complete(null);
        return;
      }

      failure = response.getLeft().asException();
    } else {
      failure = error;
    }

    // For now, always retry; in some cases, the request will eventually go through. However, in
    // some cases it never will. This should be covered in a follow-up issue.
    // Unrecoverable cases:
    //   - RemoteHandlerError
    //   - ErrorResponse.code in [ MALFORMED, INVALID ]
    switch (failure) {
      // all returned errors are currently unnecessary to retry
      case final UnrecoverableException e ->
          handleUnrecoverableExceptionOnRemove(registration, e, removed);
      // no point retrying if the remote handler failed to handle our request
      case final RemoteHandlerFailure e ->
          handleUnrecoverableExceptionOnRemove(registration, e, removed);
      // should not happen, since it means the member was removed from the topology, but keep as a
      // failsafe
      case final NoSuchMemberException e ->
          handleUnrecoverableExceptionOnRemove(registration, e, removed);
      // essentially happens due to malformed request, no point retrying
      case final ProtocolException e ->
          handleUnrecoverableExceptionOnRemove(registration, e, removed);
      default -> {
        LOGGER.debug(
            "Failed to remove remote stream {} on {}, will retry in {}",
            registration.streamId(),
            registration.serverId(),
            RETRY_DELAY,
            failure);
        executor.schedule(RETRY_DELAY, retry);
      }
    }
  }

  private void handleUnrecoverableExceptionOnRemove(
      final ClientStreamRegistration<M> registration,
      final Throwable e,
      final CompletableFuture<Void> removed) {
    LOGGER.debug(
        """
        Failed to remove stream '{}' for member '{}'; unrecoverable error occurred on recipient
        side, will not retry.""",
        registration.streamId(),
        registration.serverId(),
        e);
    removed.complete(null);
  }

  private void purgeRegistration(final UUID streamId, final MemberId serverId) {
    final var perHost = registrations.get(serverId);
    if (perHost != null) {
      final var registration = perHost.remove(streamId);

      if (registration != null) {
        registration.transitionToClosed();
      }

      if (perHost.isEmpty()) {
        registrations.remove(serverId);
      }
    }
  }

  private void doRemoveAll(final MemberId brokerId, final String physicalTenantId) {
    communicationService.unicast(
        StreamTopics.REMOVE_ALL.topic(physicalTenantId),
        REMOVE_ALL_REQUEST,
        Function.identity(),
        brokerId,
        true);
    legacyUnicastIfDefaultTenant(
        StreamTopics.REMOVE_ALL, physicalTenantId, REMOVE_ALL_REQUEST, brokerId);
  }

  /** Rolling-upgrade compat; remove alongside the legacy topic in 8.11. */
  private void legacyUnicastIfDefaultTenant(
      final StreamTopics topic,
      final String physicalTenantId,
      final byte[] payload,
      final MemberId serverId) {
    if (!DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      return;
    }
    communicationService.unicast(topic.legacyTopic(), payload, Function.identity(), serverId, true);
  }
}
