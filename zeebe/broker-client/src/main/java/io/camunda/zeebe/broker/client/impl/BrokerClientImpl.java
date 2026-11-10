/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static io.camunda.zeebe.util.Unit.unit;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.Subscription;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailbox;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailboxSetups;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerClientImpl implements BrokerClient {
  public static final Logger LOG = LoggerFactory.getLogger(BrokerClientImpl.class);

  private final BrokerTopologyManager topologyManager;
  private final BrokerRequestManager requestManager;

  private boolean isClosed;
  // Keyed by (topic, subscriber), not topic alone: topic-only would collapse distinct callers
  // (e.g. gRPC and REST gateways) onto one subscription, dropping every subscriber but the first.
  private final Map<TopicSubscriber, Subscription> jobAvailableSubscriptions =
      new ConcurrentHashMap<>();
  private final ClusterEventService eventService;
  private final ActorSchedulingService schedulingService;
  private final AtomixClientTransportAdapter atomixTransportAdapter;
  private final @Nullable GatewayMailbox mailbox;

  public BrokerClientImpl(
      final Duration requestTimeout,
      final MessagingService messagingService,
      final ClusterEventService eventService,
      final ActorSchedulingService schedulingService,
      final BrokerTopologyManager topologyManager,
      final BrokerClientRequestMetrics metrics) {
    this(
        requestTimeout,
        messagingService,
        eventService,
        schedulingService,
        topologyManager,
        metrics,
        GatewayMailboxSetups.load().map(GatewayMailbox::new).orElse(null));
  }

  /**
   * @param mailbox if set, requests for a partition whose leader is not in the topology go through
   *     the mailbox when the store records a leader for the partition. The client starts and closes
   *     it.
   */
  public BrokerClientImpl(
      final Duration requestTimeout,
      final MessagingService messagingService,
      final ClusterEventService eventService,
      final ActorSchedulingService schedulingService,
      final BrokerTopologyManager topologyManager,
      final BrokerClientRequestMetrics metrics,
      final @Nullable GatewayMailbox mailbox) {
    this.mailbox = mailbox;
    this.eventService = eventService;
    this.schedulingService = schedulingService;

    this.topologyManager = topologyManager;
    atomixTransportAdapter = new AtomixClientTransportAdapter(messagingService);
    requestManager =
        new BrokerRequestManager(
            atomixTransportAdapter,
            topologyManager,
            newDispatchStrategy(mailbox),
            requestTimeout,
            metrics,
            mailbox);
  }

  /**
   * A partition whose leader is recorded in the mailbox can take requests, also if the topology
   * does not show its leader. The mailbox is for the default physical tenant only.
   */
  private static RoundRobinDispatchStrategy newDispatchStrategy(
      final @Nullable GatewayMailbox mailbox) {
    if (mailbox == null) {
      return new RoundRobinDispatchStrategy();
    }
    return new RoundRobinDispatchStrategy(
        0,
        (group, partition) ->
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(group)
                && mailbox.recordedLeader(partition).isPresent());
  }

  @Override
  public Collection<ActorFuture<Void>> start() {
    if (mailbox != null) {
      mailbox.start();
    }
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

    if (mailbox != null) {
      doAndLogException(mailbox::close);
    }

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
                responseConsumer.accept(response.getKey(), response.getResponseOrThrow());
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
      final String topic, final Object subscriber, final Consumer<String> handler) {
    jobAvailableSubscriptions.computeIfAbsent(
        new TopicSubscriber(topic, subscriber),
        k ->
            eventService
                .subscribe(
                    topic,
                    msg -> {
                      handler.accept((String) msg);
                      return CompletableFuture.completedFuture(unit());
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

  // Relies on subscriber's default (identity-based) equals/hashCode; a caller-side override to
  // content-based equality would silently collapse distinct subscribers again.
  private record TopicSubscriber(String topic, Object subscriber) {}
}
