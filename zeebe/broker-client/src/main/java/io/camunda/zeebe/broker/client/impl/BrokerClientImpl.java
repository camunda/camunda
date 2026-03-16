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
import io.camunda.zeebe.transport.impl.AtomixServerTransport.TopicSupplier;
import io.camunda.zeebe.transport.stream.impl.messages.JobAvailableNotificationDecoder;
import io.camunda.zeebe.transport.stream.impl.messages.MessageHeaderDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrokerClientImpl implements BrokerClient {
  public static final Logger LOG = LoggerFactory.getLogger(BrokerClientImpl.class);
  static final String JOBS_AVAILABLE_BY_PARTITION_TOPIC = "jobsAvailableByPartition";

  private final BrokerTopologyManager topologyManager;
  private final BrokerRequestManager requestManager;

  private boolean isClosed;
  private final List<Subscription> jobAvailableSubscriptions = new ArrayList<>();
  private final ClusterEventService eventService;
  private final ActorSchedulingService schedulingService;
  private final AtomixClientTransportAdapter atomixTransportAdapter;

  public BrokerClientImpl(
      final Duration requestTimeout,
      final MessagingService messagingService,
      final ClusterEventService eventService,
      final ActorSchedulingService schedulingService,
      final BrokerTopologyManager topologyManager,
      final BrokerClientRequestMetrics metrics,
      final TopicSupplier sendingTopicSupplier) {
    this.eventService = eventService;
    this.schedulingService = schedulingService;

    this.topologyManager = topologyManager;
    atomixTransportAdapter =
        new AtomixClientTransportAdapter(messagingService, sendingTopicSupplier);
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

    if (!jobAvailableSubscriptions.isEmpty()) {
      jobAvailableSubscriptions.forEach(Subscription::close);
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
    final var subscription =
        eventService
            .subscribe(
                topic,
                msg -> {
                  handler.accept((String) msg);
                  return CompletableFuture.completedFuture(null);
                })
            .join();
    jobAvailableSubscriptions.add(subscription);
  }

  @Override
  public void subscribeJobAvailableByPartitionNotification(
      final BiConsumer<String, Integer> handler) {
    final var subscription =
        eventService
            .subscribe(
                JOBS_AVAILABLE_BY_PARTITION_TOPIC,
                Function.identity(),
                (final byte[] bytes) -> {
                  final var headerDecoder = new MessageHeaderDecoder();
                  final var notificationDecoder = new JobAvailableNotificationDecoder();
                  final var buffer = new UnsafeBuffer(bytes);
                  notificationDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
                  final int partitionId = notificationDecoder.partitionId();
                  final String jobType = notificationDecoder.jobType();
                  handler.accept(jobType, partitionId);
                  return CompletableFuture.completedFuture(null);
                },
                ignored -> new byte[0])
            .join();
    jobAvailableSubscriptions.add(subscription);
  }

  private void doAndLogException(final Runnable r) {
    try {
      r.run();
    } catch (final Exception e) {
      LOG.error("Exception when closing client. Ignoring", e);
    }
  }
}
