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
import io.camunda.eventbridge.broker.coordinator.CoordinationManager;
import io.camunda.eventbridge.broker.logstreams.EventBridgeEventStream;
import io.camunda.eventbridge.broker.transport.RequestHandlerRegistry;
import io.camunda.eventbridge.broker.transport.coordinator.CoordinationRequestHandler;
import io.camunda.eventbridge.broker.transport.publish.PublishRequestCorrelator;
import io.camunda.eventbridge.broker.transport.publish.PublishRequestHandler;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.time.InstantSource;
import org.agrona.concurrent.IdGenerator;

public final class PartitionContext {

  private final int partitionId;
  private final int partitionCount;
  private final RaftPartition raftPartition;
  private final ActorSchedulingService actorScheduler;
  private final MessagingService messagingService;
  private final InstantSource clock;
  private final IdGenerator idGenerator;

  private LogStorage logStorage;
  private PublishRequestCorrelator correlator;
  private EventBridgeEventStream eventStream;
  private CoordinationManager coordinationManager;
  private CoordinationRequestHandler coordinationRequestHandler;
  private PublishRequestHandler publishRequestHandler;
  private RequestHandlerRegistry requestHandlerRegistry;

  public PartitionContext(
      final int partitionId,
      final int partitionCount,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final IdGenerator idGenerator) {
    this.partitionId = partitionId;
    this.partitionCount = partitionCount;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.messagingService = messagingService;
    this.clock = clock;
    this.idGenerator = idGenerator;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getPartitionCount() {
    return partitionCount;
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

  public void setLogStorage(final LogStorage v) {
    logStorage = v;
  }

  public PublishRequestCorrelator getCorrelator() {
    return correlator;
  }

  public void setCorrelator(final PublishRequestCorrelator v) {
    correlator = v;
  }

  public EventBridgeEventStream getEventStream() {
    return eventStream;
  }

  public void setEventStream(final EventBridgeEventStream v) {
    eventStream = v;
  }

  public CoordinationManager getCoordinationManager() {
    return coordinationManager;
  }

  public void setCoordinationManager(final CoordinationManager v) {
    coordinationManager = v;
  }

  public CoordinationRequestHandler getCoordinationRequestHandler() {
    return coordinationRequestHandler;
  }

  public void setCoordinationRequestHandler(final CoordinationRequestHandler v) {
    coordinationRequestHandler = v;
  }

  public PublishRequestHandler getPublishRequestHandler() {
    return publishRequestHandler;
  }

  public void setPublishRequestHandler(final PublishRequestHandler v) {
    publishRequestHandler = v;
  }

  public RequestHandlerRegistry getRequestHandlerRegistry() {
    return requestHandlerRegistry;
  }

  public void setRequestHandlerRegistry(final RequestHandlerRegistry v) {
    requestHandlerRegistry = v;
  }
}
