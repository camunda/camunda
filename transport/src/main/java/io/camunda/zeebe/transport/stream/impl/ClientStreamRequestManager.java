/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.stream.impl.ClientStreamRegistration.State;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles sending add/remove stream request to the servers. */
final class ClientStreamRequestManager<M extends BufferWriter> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientStreamRequestManager.class);
  private static final byte[] REMOVE_ALL_REQUEST = new byte[0];
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  // maps the registration state,  for each known host, of each stream
  private final Map<MemberId, Map<UUID, ClientStreamRegistration<M>>> registrations =
      new HashMap<>();

  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;

  ClientStreamRequestManager(
      final ClusterCommunicationService communicationService, final ConcurrencyControl executor) {
    this.communicationService = communicationService;
    this.executor = executor;
  }

  void add(final AggregatedClientStream<M> stream, final Collection<MemberId> serverIds) {
    for (final var serverId : serverIds) {
      add(stream, serverId);
    }
  }

  void add(final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var registration = registrationFor(stream, serverId);
    add(registration);
  }

  void add(final ClientStreamRegistration<M> registration) {
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

  void remove(final AggregatedClientStream<M> stream, final Collection<MemberId> serverIds) {
    for (final var serverId : serverIds) {
      remove(stream, serverId);
    }
  }

  void remove(final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var streamsPerHost = registrations.get(serverId);
    if (streamsPerHost == null) {
      return;
    }

    final var registration = streamsPerHost.get(stream.getStreamId());
    if (registration != null) {
      remove(registration);
    }
  }

  void remove(final ClientStreamRegistration<M> registration) {
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
        serverId ->
            communicationService.unicast(
                StreamTopics.REMOVE.topic(), payload, Function.identity(), serverId, true));
  }

  @VisibleForTesting("Allows easier test set up and validation")
  ClientStreamRegistration<M> registrationFor(
      final AggregatedClientStream<M> stream, final MemberId serverId) {
    final var streamsPerHost = registrations.computeIfAbsent(serverId, ignored -> new HashMap<>());
    return streamsPerHost.computeIfAbsent(
        stream.getStreamId(), streamId -> new ClientStreamRegistration<>(stream, serverId));
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
        (ok, error) -> handleAddResponse(registration, request, error), executor::run);
  }

  private void handleAddResponse(
      final ClientStreamRegistration<M> registration, final byte[] request, final Throwable error) {
    final var state = registration.state();
    if (state != State.ADDING) {
      LOGGER.trace("Skip handling ADD response since the state is {}", state, error);
      return;
    }

    if (error == null) {
      registration.transitionToAdded();
      return;
    }

    // TODO: define some abort conditions. We may not have to retry indefinitely.
    // For now, always retry; eventually the request will succeed, and duplicate add request are
    // fine.
    LOGGER.warn(
        "Failed to add stream {} on {}; will retry in {}",
        registration.streamId(),
        registration.serverId(),
        RETRY_DELAY,
        error);
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
        (ok, error) -> handleRemoveResponse(registration, request, error), executor::run);
  }

  private void handleRemoveResponse(
      final ClientStreamRegistration<M> registration, final byte[] request, final Throwable error) {
    final var state = registration.state();
    if (state != State.REMOVING) {
      LOGGER.trace("Skip handling REMOVE response since the state is {}", state, error);
      return;
    }

    if (error == null) {
      registration.transitionToRemoved();
      return;
    }

    // TODO: use backoff delay
    LOGGER.debug(
        "Failed to remove remote stream {} on {}, will retry in {}",
        registration.streamId(),
        registration.serverId(),
        RETRY_DELAY);
    executor.schedule(RETRY_DELAY, () -> sendRemoveRequest(registration, request));
  }

  private void doRemoveAll(final MemberId brokerId) {
    // Do not wait for response, as this is expected to be called while shutting down.
    communicationService.unicast(
        StreamTopics.REMOVE_ALL.topic(), REMOVE_ALL_REQUEST, Function.identity(), brokerId, true);
  }
}
