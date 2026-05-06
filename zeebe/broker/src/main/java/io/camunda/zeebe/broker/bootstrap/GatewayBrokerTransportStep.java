/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.transport.impl.AtomixServerTransport.TopicSupplier;
import java.util.List;

/** Starts the server transport which can receive commands from the gateway * */
final class GatewayBrokerTransportStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Broker Transport";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(() -> startServerTransport(brokerStartupContext, startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    closeServerTransport(brokerShutdownContext, concurrencyControl, shutdownFuture);
  }

  private void startServerTransport(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
    final var schedulingService = brokerStartupContext.getActorSchedulingService();
    final var messagingService = brokerStartupContext.getApiMessagingService();
    final var requestIdGenerator = brokerStartupContext.getRequestIdGenerator();

    final var config = brokerStartupContext.getBrokerConfiguration().getExperimental();
    final TopicSupplier legacyTopicSupplier = TopicSupplier.withLegacyTopicName();
    final TopicSupplier topicSupplier = TopicSupplier.withPrefix(config.getDefaultEngineName());

    final var receiveOnTopicSuppliers =
        config.isReceiveOnLegacySubject()
            ? List.of(legacyTopicSupplier, topicSupplier)
            : List.of(topicSupplier);

    final var atomixServerTransport =
        new AtomixServerTransport(messagingService, requestIdGenerator, receiveOnTopicSuppliers);

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(atomixServerTransport),
        proceed(
            () -> {
              brokerStartupContext.setGatewayBrokerTransport(atomixServerTransport);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }

  private void closeServerTransport(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var serverTransport = brokerShutdownContext.getGatewayBrokerTransport();

    if (serverTransport == null) {
      return;
    }

    concurrencyControl.runOnCompletion(
        serverTransport.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setGatewayBrokerTransport(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }
}
