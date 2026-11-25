/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.client.FanoutClient;
import io.camunda.fanout.client.port.ClientState;
import io.camunda.fanout.client.port.EventHandler;
import io.camunda.fanout.protocol.FanoutProtocol;
import io.camunda.fanout.protocol.model.ApplyReportV1;
import io.camunda.fanout.protocol.model.CloudEvent;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * A fan-out client without Hub: the test gives it the events that Hub would send. Like the real
 * client, it gives them to the handler of the host one at a time.
 */
final class StubFanoutClient implements FanoutClient {

  private final EventHandler handler;
  private volatile @Nullable ClientState startState;
  private volatile boolean closed;

  StubFanoutClient(final EventHandler handler) {
    this.handler = handler;
  }

  @Override
  public void start(final ClientState initial) {
    if (startState != null || closed) {
      throw new IllegalStateException("The client was started or closed before");
    }
    startState = initial;
  }

  @Override
  public void close() {
    closed = true;
  }

  /** Gives the change set to the handler, as the event with the given sequence. */
  CompletableFuture<Optional<ApplyReportV1>> send(
      final PolicyChangeSetV1 changeSet, final long sequence) {
    final var event =
        new CloudEvent(
            FanoutProtocol.SPEC_VERSION,
            UUID.randomUUID().toString(),
            "hub",
            FanoutProtocol.POLICY_CHANGE_SET_V1,
            Long.toString(sequence),
            null,
            "application/json",
            changeSet);
    return handler.handle(event).toCompletableFuture();
  }

  @Nullable ClientState startState() {
    return startState;
  }

  boolean isClosed() {
    return closed;
  }

  /** Creates stub clients and keeps every one, in order. */
  static final class Factory implements FanoutClientFactory {

    final List<StubFanoutClient> clients = new CopyOnWriteArrayList<>();

    @Override
    public FanoutClient create(final EventHandler policyChangeSetHandler) {
      final var client = new StubFanoutClient(policyChangeSetHandler);
      clients.add(client);
      return client;
    }
  }
}
