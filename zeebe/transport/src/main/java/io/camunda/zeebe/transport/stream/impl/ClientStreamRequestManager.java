/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

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
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates registering/de-registering {@link AggregatedClientStream} with arbitrary set of
 * servers.
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
   * Sends a single remove all request to each given server, and closes all pending registrations.
   *
   * @param servers the list of servers to notify
   */
  void removeAll(final Collection<MemberId> servers) {
    registrations.values().stream()
        .flatMap(m -> m.values().stream())
        .forEach(ClientStreamRegistration::transitionToClosed);
    registrations.clear();

    servers.forEach(this::doRemoveAll);
  }

  /**
   * Send remove stream request to servers without waiting for ack and without retry. As this is
   * used to send even when no stream was originally registered, we ignore any existing
   * registrations.
   */
  void removeUnreliable(final UUID streamId, final Collection<MemberId> servers) {
    final var request = new RemoveStreamRequest().streamId(streamId);
    final var payload = BufferUtil.bufferAsArray(request);

    servers.forEach(
        serverId -> {
          communicationService.unicast(
              StreamTopics.REMOVE.topic(), payload, Function.identity(), serverId, true);
          purgeRegistration(streamId, serverId);
        });
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
    sendAddRequest(registration, payload);
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

    final var pendingRequest = registration.pendingRequest();
    if (pendingRequest == null) {
      sendRemoveRequest(registration, payload);
      return;
    }

    // to minimize the likelihood of out-of-order requests on the server side, wait until the
    // current request is finished before sending the next request, regardless of the result
    pendingRequest.whenCompleteAsync(
        (ok, error) -> sendRemoveRequest(registration, payload), executor::run);
  }

  /**
   * Closes all pending registrations for this server, and removes them from the in-memory cache.
   */
  void onServerRemoved(final MemberId serverId) {
    final var perHost = registrations.remove(serverId);
    if (perHost == null) {
      return;
    }

    LOGGER.trace("Closing all registrations for server {}", serverId);
    perHost.values().forEach(ClientStreamRegistration::transitionToClosed);
  }

  @VisibleForTesting("Allows easier test set up and validation")
  ClientStreamRegistration<M> registrationFor(
      final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var streamsPerHost = registrations.computeIfAbsent(serverId, ignored -> new HashMap<>());
    return streamsPerHost.computeIfAbsent(
        stream.streamId(), streamId -> new ClientStreamRegistration<>(stream, serverId));
  }

  private void sendAddRequest(
      final ClientStreamRegistration<M> registration, final byte[] request) {
    if (registration.state() != State.ADDING) {
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.ADD.topic(),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) -> handleAddResponse(registration, request, response, error),
        executor::run);
  }

  private void handleAddResponse(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final byte[] responseBuffer,
      final Throwable error) {
    final var state = registration.state();
    if (state != State.ADDING) {
      LOGGER.trace("Skip handling ADD response since the state is {}", state, error);
      return;
    }

    final Throwable failure;
    final Either<ErrorResponse, AddStreamResponse> response;
    if (error == null) {
      response = responseDecoder.decode(responseBuffer, new AddStreamResponse());
      if (response.isRight()) {
        registration.transitionToAdded();
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
    executor.schedule(RETRY_DELAY, () -> sendAddRequest(registration, request));
  }

  private void sendRemoveRequest(
      final ClientStreamRegistration<M> registration, final byte[] request) {
    if (registration.state() != State.REMOVING) {
      return;
    }

    final var pendingRequest =
        communicationService.send(
            StreamTopics.REMOVE.topic(),
            request,
            Function.identity(),
            Function.identity(),
            registration.serverId(),
            REQUEST_TIMEOUT);
    registration.setPendingRequest(pendingRequest);
    pendingRequest.whenCompleteAsync(
        (response, error) -> handleRemoveResponse(registration, request, response, error),
        executor::run);
  }

  private void handleRemoveResponse(
      final ClientStreamRegistration<M> registration,
      final byte[] request,
      final byte[] responseBuffer,
      final Throwable error) {
    final var state = registration.state();
    if (state != State.REMOVING) {
      LOGGER.trace("Skip handling REMOVE response since the state is {}", state, error);
      return;
    }

    final Throwable failure;
    final Either<ErrorResponse, RemoveStreamResponse> response;
    if (error == null) {
      response = responseDecoder.decode(responseBuffer, new RemoveStreamResponse());
      if (response.isRight()) {
        registration.transitionToRemoved();
        purgeRegistration(registration.streamId(), registration.serverId());
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
      case final UnrecoverableException e -> handleUnrecoverableExceptionOnRemove(registration, e);
      // no point retrying if the remote handler failed to handle our request
      case final RemoteHandlerFailure e -> handleUnrecoverableExceptionOnRemove(registration, e);
      // should not happen, since it means the member was removed from the topology, but keep as a
      // failsafe
      case final NoSuchMemberException e -> handleUnrecoverableExceptionOnRemove(registration, e);
      // essentially happens due to malformed request, no point retrying
      case final ProtocolException e -> handleUnrecoverableExceptionOnRemove(registration, e);
      default -> {
        LOGGER.debug(
            "Failed to remove remote stream {} on {}, will retry in {}",
            registration.streamId(),
            registration.serverId(),
            RETRY_DELAY,
            failure);
        executor.schedule(RETRY_DELAY, () -> sendRemoveRequest(registration, request));
      }
    }
  }

  private void handleUnrecoverableExceptionOnRemove(
      final ClientStreamRegistration<M> registration, final Throwable e) {
    LOGGER.debug(
        """
        Failed to remove stream '{}' for member '{}'; unrecoverable error occurred on recipient
        side, will not retry.""",
        registration.streamId(),
        registration.serverId(),
        e);
    registration.transitionToRemoved();
    purgeRegistration(registration.streamId(), registration.serverId());
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

  private void doRemoveAll(final MemberId brokerId) {
    // Do not wait for response, as this is expected to be called while shutting down.
    communicationService.unicast(
        StreamTopics.REMOVE_ALL.topic(), REMOVE_ALL_REQUEST, Function.identity(), brokerId, true);
  }
}
