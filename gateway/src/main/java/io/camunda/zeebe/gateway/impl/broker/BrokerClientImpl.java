/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.Subscription;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class BrokerClientImpl implements BrokerClient {
  public static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final String ERROR_MSG_STOP_FAILED =
      "Failed to gracefully shutdown gateway broker client";

  private final ActorScheduler actorScheduler;
  private final BrokerTopologyManagerImpl topologyManager;
  private final boolean ownsActorScheduler;
  private final BrokerRequestManager requestManager;
  private boolean isClosed;
  private Subscription jobAvailableSubscription;
  private final ClusterEventService eventService;

  public BrokerClientImpl(
      final GatewayCfg configuration,
      final MessagingService messagingService,
      final ClusterMembershipService membershipService,
      final ClusterEventService eventService) {
    this(configuration, messagingService, membershipService, eventService, null);
  }

  public BrokerClientImpl(
      final GatewayCfg configuration,
      final MessagingService messagingService,
      final ClusterMembershipService membershipService,
      final ClusterEventService eventService,
      final ActorClock actorClock) {
    this(
        configuration,
        messagingService,
        membershipService,
        eventService,
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(configuration.getThreads().getManagementThreads())
            .setIoBoundActorThreadCount(0)
            .setActorClock(actorClock)
            .setSchedulerName("gateway-scheduler")
            .build(),
        true);
  }

  public BrokerClientImpl(
      final GatewayCfg configuration,
      final MessagingService messagingService,
      final ClusterMembershipService membershipService,
      final ClusterEventService eventService,
      final ActorScheduler actorScheduler,
      final boolean ownsActorScheduler) {
    this.eventService = eventService;
    this.actorScheduler = actorScheduler;
    this.ownsActorScheduler = ownsActorScheduler;

    if (ownsActorScheduler) {
      actorScheduler.start();
    }

    final ClusterCfg clusterCfg = configuration.getCluster();
    topologyManager = new BrokerTopologyManagerImpl(membershipService::getMembers);
    actorScheduler.submitActor(topologyManager);
    membershipService.addListener(topologyManager);
    membershipService
        .getMembers()
        .forEach(
            member -> topologyManager.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member)));

    final var atomixTransportAdapter = new AtomixClientTransportAdapter(messagingService);
    actorScheduler.submitActor(atomixTransportAdapter);
    requestManager =
        new BrokerRequestManager(
            atomixTransportAdapter,
            topologyManager,
            new RoundRobinDispatchStrategy(topologyManager),
            clusterCfg.getRequestTimeout());
    actorScheduler.submitActor(requestManager);
  }

  @Override
  public void close() {
    if (isClosed) {
      return;
    }

    isClosed = true;

    LOG.debug("Closing gateway broker client ...");

    doAndLogException(topologyManager::close);
    LOG.debug("topology manager closed");

    if (jobAvailableSubscription != null) {
      jobAvailableSubscription.close();
    }

    if (ownsActorScheduler) {
      try {
        actorScheduler.stop().get(15, TimeUnit.SECONDS);
      } catch (final InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new UncheckedExecutionException(ERROR_MSG_STOP_FAILED, ie);
      } catch (final ExecutionException | TimeoutException e) {
        throw new UncheckedExecutionException(ERROR_MSG_STOP_FAILED, e);
      }
    }

    LOG.debug("Gateway broker client closed.");
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    return requestManager.sendRequest(request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return requestManager.sendRequest(request, requestTimeout);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request) {
    return requestManager.sendRequestWithRetry(request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return requestManager.sendRequestWithRetry(request, requestTimeout);
  }

  @Override
  public <T> void sendRequestWithRetry(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    requestManager
        .sendRequestWithRetry(request)
        .whenComplete(
            (response, error) -> {
              if (error == null) {
                responseConsumer.accept(response.getKey(), response.getResponse());
              } else {
                throwableConsumer.accept(error);
              }
            });
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public void subscribeJobAvailableNotification(
      final String topic, final Consumer<String> handler) {
    jobAvailableSubscription =
        eventService
            .subscribe(
                topic,
                msg -> {
                  handler.accept((String) msg);
                  return CompletableFuture.completedFuture(null);
                })
            .join();
  }

  private void doAndLogException(final Runnable r) {
    try {
      r.run();
    } catch (final Exception e) {
      LOG.error("Exception when closing client. Ignoring", e);
    }
  }
}
