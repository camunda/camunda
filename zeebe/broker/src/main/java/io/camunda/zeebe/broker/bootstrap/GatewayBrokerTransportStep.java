/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.transport.commandapi.ResponseForwarder;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;

/** Starts the server transport which can receive commands from the gateway * */
final class GatewayBrokerTransportStep extends AbstractBrokerStartupStep {

  private ResponseForwarder responseForwarder;

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
    final var forwarder =
        new ResponseForwarder(
            brokerStartupContext.getClusterServices().getCommunicationService(),
            brokerStartupContext.getBrokerInfo().getNodeId());
    final var atomixServerTransport =
        new AtomixServerTransport(
            messagingService, requestIdGenerator, config.isReceiveOnLegacySubject(), forwarder);

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(atomixServerTransport),
        proceed(
            () -> {
              forwarder.startReceiving(atomixServerTransport);
              responseForwarder = forwarder;
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

    if (responseForwarder != null) {
      responseForwarder.stopReceiving();
      responseForwarder = null;
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
