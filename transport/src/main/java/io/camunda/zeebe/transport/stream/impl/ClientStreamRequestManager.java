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
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles sending add/remove stream request to the servers. */
final class ClientStreamRequestManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientStreamRequestManager.class);
  private static final byte[] REMOVE_ALL_REQUEST = new byte[0];
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;

  // flag used to prevent ongoing asynchronous from executing if the manager was closed externally
  private boolean isClosed;

  ClientStreamRequestManager(
      final ClusterCommunicationService communicationService, final ConcurrencyControl executor) {
    this.communicationService = communicationService;
    this.executor = executor;
  }

  void add(final ClientStreamRegistration registration) {
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

  void remove(final ClientStreamRegistration registration) {
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
    servers.forEach(this::doRemoveAll);
  }

  /** Send remove stream request to servers without waiting for ack and without retry * */
  void removeUnreliable(final UUID streamId, final Collection<MemberId> servers) {
    final var request = new RemoveStreamRequest().streamId(streamId);
    final var payload = BufferUtil.bufferAsArray(request);

    servers.forEach(
        serverId ->
            communicationService.unicast(
                StreamTopics.REMOVE.topic(), payload, Function.identity(), serverId, true));
  }

  /**
   * Closing the request manager ensures no asynchronous actions are taken, which is useful to
   * prevent retries or dangling operations from occurring after close
   */
  void close() {
    isClosed = true;
  }

  private void sendAddRequest(final ClientStreamRegistration registration, final byte[] request) {
    if (isClosed) {
      return;
    }

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
      final ClientStreamRegistration registration, final byte[] request, final Throwable error) {
    if (isClosed) {
      return;
    }

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
      final ClientStreamRegistration registration, final byte[] request) {
    if (isClosed) {
      return;
    }

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
      final ClientStreamRegistration registration, final byte[] request, final Throwable error) {
    if (isClosed) {
      return;
    }

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
