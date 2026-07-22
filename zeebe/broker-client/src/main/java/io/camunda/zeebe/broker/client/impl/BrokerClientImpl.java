/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.Subscription;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerClientImpl implements BrokerClient {
  public static final Logger LOG = LoggerFactory.getLogger(BrokerClientImpl.class);

  private final BrokerTopologyManager topologyManager;
  private final BrokerRequestManager requestManager;

  private boolean isClosed;
  // Keyed by topic so a repeated subscribe for the same topic is a no-op: ActivateJobsHandler#
  // activateJobs can be called concurrently and lazily subscribes per physical tenant on every
  // call, so the dedup here must be atomic (computeIfAbsent) rather than left to the caller.
  private final Map<String, Subscription> jobAvailableSubscriptions = new ConcurrentHashMap<>();
  private final ClusterEventService eventService;
  private final ActorSchedulingService schedulingService;
  private final AtomixClientTransportAdapter atomixTransportAdapter;

  public BrokerClientImpl(
      final Duration requestTimeout,
      final MessagingService messagingService,
      final ClusterEventService eventService,
      final ActorSchedulingService schedulingService,
      final BrokerTopologyManager topologyManager,
      final BrokerClientRequestMetrics metrics) {
    this.eventService = eventService;
    this.schedulingService = schedulingService;

    this.topologyManager = topologyManager;
    atomixTransportAdapter = new AtomixClientTransportAdapter(messagingService);
    requestManager =
        new BrokerRequestManager(
            atomixTransportAdapter,
            topologyManager,
            new RoundRobinDispatchStrategy(),
            requestTimeout,
            metrics);
  }

  @Override
  public Collection<ActorFuture<Void>> start() {
    final var transportStarted = schedulingService.submitActor(atomixTransportAdapter);
    final var requestManagerStarted = schedulingService.submitActor(requestManager);
    return List.of(transportStarted, requestManagerStarted);
  }

  @Override
  public void close() {
    if (isClosed) {
      return;
    }

    isClosed = true;
    LOG.debug("Closing gateway broker client ...");

    doAndLogException(requestManager::close);
    LOG.debug("request manager closed");

    doAndLogException(atomixTransportAdapter::close);
    LOG.debug("transport client closed");

    jobAvailableSubscriptions.values().forEach(Subscription::close);

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
              if (error != null) {
                throwableConsumer.accept(error);
              } else if (response.isResponse()) {
                responseConsumer.accept(response.getKey(), response.getResponse());
              } else {
                throwableConsumer.accept(response.toException());
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
    jobAvailableSubscriptions.computeIfAbsent(
        topic,
        t ->
            eventService
                .subscribe(
                    t,
                    msg -> {
                      handler.accept((String) msg);
                      return CompletableFuture.completedFuture(null);
                    })
                .join());
  }

  private void doAndLogException(final Runnable r) {
    try {
      r.run();
    } catch (final Exception e) {
      LOG.error("Exception when closing client. Ignoring", e);
    }
  }
}
