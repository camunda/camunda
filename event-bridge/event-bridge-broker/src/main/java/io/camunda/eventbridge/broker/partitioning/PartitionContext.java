/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.messaging.fetch.FetchPurgatory;
import io.camunda.eventbridge.messaging.stream.EventBridgeEventStream;
import io.camunda.eventbridge.messaging.transport.fetch.FetchRequestHandler;
import io.camunda.eventbridge.messaging.transport.publish.PublishRequestCorrelator;
import io.camunda.eventbridge.messaging.transport.publish.PublishRequestHandler;
import io.camunda.eventbridge.messaging.watermark.HighWatermark;
import io.camunda.eventbridge.transport.RequestHandlerRegistry;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.time.InstantSource;
import java.util.concurrent.ExecutorService;
import org.agrona.concurrent.IdGenerator;

public final class PartitionContext {

  private final int partitionId;
  private final int partitionCount;
  private final String routingGroup;
  private final RaftPartition raftPartition;
  private final ActorSchedulingService actorScheduler;
  private final MessagingService messagingService;
  private final InstantSource clock;
  private final IdGenerator idGenerator;
  private final ExecutorService executorService;

  private LogStorage logStorage;
  private PublishRequestCorrelator correlator;
  private EventBridgeEventStream eventStream;
  private PublishRequestHandler publishRequestHandler;
  private FetchRequestHandler fetchRequestHandler;
  private RequestHandlerRegistry requestHandlerRegistry;
  private HighWatermark highWatermark;
  private FetchPurgatory fetchPurgatory;

  public PartitionContext(
      final int partitionId,
      final int partitionCount,
      final String routingGroup,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final IdGenerator idGenerator,
      final ExecutorService executorService) {
    this.partitionId = partitionId;
    this.partitionCount = partitionCount;
    this.routingGroup = routingGroup;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.messagingService = messagingService;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.executorService = executorService;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  /** The gateway routing group whose request subjects this partition's handlers register under. */
  public String getRoutingGroup() {
    return routingGroup;
  }

  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  public ActorSchedulingService getActorScheduler() {
    return actorScheduler;
  }

  public MessagingService getMessagingService() {
    return messagingService;
  }

  public InstantSource getClock() {
    return clock;
  }

  public IdGenerator getIdGenerator() {
    return idGenerator;
  }

  public LogStorage getLogStorage() {
    return logStorage;
  }

  public void setLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
  }

  public PublishRequestCorrelator getCorrelator() {
    return correlator;
  }

  public void setCorrelator(final PublishRequestCorrelator requestCorrelator) {
    correlator = requestCorrelator;
  }

  public EventBridgeEventStream getEventStream() {
    return eventStream;
  }

  public void setEventStream(final EventBridgeEventStream eventStream) {
    this.eventStream = eventStream;
  }

  public PublishRequestHandler getPublishRequestHandler() {
    return publishRequestHandler;
  }

  public void setPublishRequestHandler(final PublishRequestHandler publishRequestHandler) {
    this.publishRequestHandler = publishRequestHandler;
  }

  public FetchRequestHandler getFetchRequestHandler() {
    return fetchRequestHandler;
  }

  public void setFetchRequestHandler(final FetchRequestHandler fetchRequestHandler) {
    this.fetchRequestHandler = fetchRequestHandler;
  }

  public RequestHandlerRegistry getRequestHandlerRegistry() {
    return requestHandlerRegistry;
  }

  public void setRequestHandlerRegistry(final RequestHandlerRegistry requestHandlerRegistry) {
    this.requestHandlerRegistry = requestHandlerRegistry;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public HighWatermark getHighWatermark() {
    return highWatermark;
  }

  public void setHighWatermark(final HighWatermark highWatermark) {
    this.highWatermark = highWatermark;
  }

  public FetchPurgatory getFetchPurgatory() {
    return fetchPurgatory;
  }

  public void setFetchPurgatory(final FetchPurgatory fetchPurgatory) {
    this.fetchPurgatory = fetchPurgatory;
  }
}
