/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the registration state of remote stream from the client's point of view. Used
 * specifically to serialize the lifecycle of a stream and avoid concurrent, out of order,
 * registration and removal requests.
 *
 * <p>This class is not thread safe, and expects to be always called from the same synchronization
 * context.
 */
final class ClientStreamRegistration<M extends BufferWriter> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientStreamRegistration.class);

  private final AggregatedClientStream<M> stream;
  private final MemberId serverId;

  private State state = State.INITIAL;
  private CompletionStage<byte[]> pendingRequest;

  ClientStreamRegistration(final AggregatedClientStream<M> stream, final MemberId serverId) {
    this.stream = stream;
    this.serverId = serverId;
  }

  UUID streamId() {
    return stream.streamId();
  }

  LogicalId<? extends BufferWriter> logicalId() {
    return stream.logicalId();
  }

  MemberId serverId() {
    return serverId;
  }

  State state() {
    return state;
  }

  CompletionStage<byte[]> pendingRequest() {
    return pendingRequest;
  }

  void setPendingRequest(final CompletionStage<byte[]> pendingRequest) {
    this.pendingRequest = pendingRequest;
  }

  boolean transitionToAdding() {
    return transition(State.ADDING, EnumSet.of(State.INITIAL));
  }

  void transitionToAdded() {
    if (transition(State.ADDED, EnumSet.of(State.ADDING))) {
      stream.add(serverId);
    }
  }

  boolean transitionToRemoving() {
    return transition(State.REMOVING, EnumSet.of(State.INITIAL, State.ADDING, State.ADDED));
  }

  void transitionToRemoved() {
    if (transition(State.REMOVED, EnumSet.of(State.INITIAL, State.REMOVING))) {
      stream.remove(serverId);
    }
  }

  void transitionToClosed() {
    transition(State.CLOSED, EnumSet.allOf(State.class));
    stream.remove(serverId);
  }

  private boolean transition(final State target, final Set<State> allowed) {
    if (!allowed.contains(state)) {
      logSkippedTransition(target);
      return false;
    }

    state = target;
    LOGGER.trace("{} remote client stream {} on server {}", target, stream.streamId(), serverId);
    return true;
  }

  private void logSkippedTransition(final State target) {
    LOGGER.trace(
        "Skip {} transition of stream {} on {} as its state is {}",
        target,
        stream.streamId(),
        serverId,
        state);
  }

  /**
   * State transitions:
   *
   * <ul>
   *   <li>INITIAL -> [ADDING, REMOVED]
   *   <li>ADDING -> [ADDED, REMOVING]
   *   <li>ADDED -> [REMOVING]
   *   <li>REMOVING -> [REMOVED]
   *   <li>REMOVED -> []
   * </ul>
   */
  enum State {
    INITIAL,
    ADDING,
    ADDED,
    REMOVING,
    REMOVED,
    CLOSED
  }
}
