/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedNetworkService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.NetworkService;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ManagedNetworkService} backed by Netty, pairing a TCP {@link NettyMessagingService} with
 * a UDP {@link NettyUnicastService}.
 *
 * <p>Both transports are brought up here so that callers never have to order them: messaging starts
 * first and stops last.
 *
 * <p>Note that we only expose the non-managed interfaces; this ensures the life cycle of the
 * wrapped managed services are never controlled except in this class.
 *
 * <p>Both transitions are chained on a caller-provided {@link Executor}, so that neither transport
 * is driven from a thread belonging to the other: the messaging service completes its start future
 * on a Netty event loop, where binding the unicast transport would do set up work on a thread meant
 * to stay free. The executor is borrowed, not owned, and must stay usable until {@link #stop()}
 * completes.
 */
@NullMarked
public final class NettyNetworkService implements ManagedNetworkService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NettyNetworkService.class);

  private final ManagedMessagingService messagingService;
  private final ManagedUnicastService unicastService;
  private final Executor executor;

  public NettyNetworkService(
      final String clusterId,
      final Address advertisedAddress,
      final MessagingConfig config,
      final String actorSchedulerName,
      final MeterRegistry registry,
      final Executor executor) {
    this(
        new NettyMessagingService(
            clusterId, advertisedAddress, config, actorSchedulerName, registry),
        new NettyUnicastService(clusterId, advertisedAddress, config, actorSchedulerName, registry),
        executor);
  }

  @VisibleForTesting
  NettyNetworkService(
      final ManagedMessagingService messagingService,
      final ManagedUnicastService unicastService,
      final Executor executor) {
    this.messagingService = messagingService;
    this.unicastService = unicastService;
    this.executor = executor;
  }

  @Override
  public MessagingService messagingService() {
    return messagingService;
  }

  @Override
  public UnicastService unicastService() {
    return unicastService;
  }

  @Override
  public CompletableFuture<NetworkService> start() {
    return messagingService
        .start()
        .thenComposeAsync(ignored -> unicastService.start(), executor)
        .thenApply(ignored -> this);
  }

  @Override
  public boolean isRunning() {
    return messagingService.isRunning() && unicastService.isRunning();
  }

  @Override
  public CompletableFuture<Void> stop() {
    // recover from a failed unicast stop so the messaging transport is still torn down; leaking it
    // would keep the node reachable after shutdown
    return unicastService
        .stop()
        .exceptionally(
            error -> {
              LOGGER.error("Failed to stop the unicast service", error);
              return null;
            })
        .thenComposeAsync(ignored -> messagingService.stop(), executor);
  }
}
