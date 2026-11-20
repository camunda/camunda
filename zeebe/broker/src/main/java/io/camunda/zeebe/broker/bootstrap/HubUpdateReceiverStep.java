/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.hub.BrokerClientPolicyInPort;
import io.camunda.zeebe.broker.hub.HubClient;
import io.camunda.zeebe.broker.hub.HubUpdateReceiver;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;

/**
 * Starts one {@link HubUpdateReceiver} for every physical tenant that gets a {@link HubClient} from
 * the factory registered with the Spring bridge (ADR 0015, D1). It runs before the partition
 * managers are created, which add the receiver of their tenant to their partition listeners.
 */
public final class HubUpdateReceiverStep extends AbstractBrokerStartupStep {

  static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var factory = brokerStartupContext.getSpringBrokerBridge().getHubClientFactory();
    if (factory.isEmpty()) {
      startupFuture.complete(brokerStartupContext);
      return;
    }

    final var futures =
        brokerStartupContext.getPhysicalTenantIds().known().stream()
            .flatMap(
                tenantId ->
                    factory.get().create(tenantId).stream()
                        .map(client -> startReceiver(brokerStartupContext, tenantId, client)))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (ok, error) -> {
          if (error != null) {
            // stop the receivers that started before completing exceptionally
            concurrencyControl.runOnCompletion(
                shutdownReceivers(brokerStartupContext, concurrencyControl),
                (ignored, shutdownError) -> startupFuture.completeExceptionally(error));
          } else {
            startupFuture.complete(brokerStartupContext);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    concurrencyControl.runOnCompletion(
        shutdownReceivers(brokerShutdownContext, concurrencyControl),
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  @Override
  public String getName() {
    return "Hub Update Receiver";
  }

  private ActorFuture<Void> startReceiver(
      final BrokerStartupContext context, final String physicalTenantId, final HubClient client) {
    final var receiver =
        new HubUpdateReceiver(
            physicalTenantId,
            client,
            new BrokerClientPolicyInPort(physicalTenantId, context.getBrokerClient()),
            context.getClusterServices().getMembershipService(),
            RETRY_INTERVAL);
    final var started = context.getActorSchedulingService().submitActor(receiver);
    // stored at once, so a failed start is closed by the clean-up as well
    context.addHubUpdateReceiver(physicalTenantId, receiver);
    return started;
  }

  private ActorFuture<List<Void>> shutdownReceivers(
      final BrokerStartupContext context, final ConcurrencyControl concurrencyControl) {
    return context.getPhysicalTenantIds().known().stream()
        .map(tenantId -> shutdownReceiver(context, tenantId))
        .collect(new ActorFutureCollector<>(concurrencyControl));
  }

  private ActorFuture<Void> shutdownReceiver(
      final BrokerStartupContext context, final String physicalTenantId) {
    final var receiver = context.getHubUpdateReceiver(physicalTenantId);
    if (receiver == null) {
      return CompletableActorFuture.completed(null);
    }
    context.removeHubUpdateReceiver(physicalTenantId);
    return receiver.closeAsync();
  }
}
