/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import org.agrona.collections.IntHashSet;

public final class GetApiServiceImpl extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private final ServerTransport serverTransport;
  private final GetApiRequestHandler getHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final ActorSchedulingService scheduler;

  public GetApiServiceImpl(
      final ServerTransport serverTransport, final ActorSchedulingService scheduler) {
    this.serverTransport = serverTransport;
    this.scheduler = scheduler;
    getHandler = new GetApiRequestHandler();
  }

  @Override
  public String getName() {
    return "GetApiService";
  }

  @Override
  protected void onActorStarting() {
    scheduler.submitActor(getHandler);
  }

  @Override
  protected void onActorClosing() {
    for (final Integer leadPartition : leadPartitions) {
      removeLeaderHandlers(leadPartition);
    }
    leadPartitions.clear();
    actor.runOnCompletion(
        getHandler.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            Loggers.TRANSPORT_LOGGER.error("Error closing command api request handler", error);
          }
        });
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return removeLeaderHandlersAsync(partitionId);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          leadPartitions.add(partitionId);
          serverTransport.subscribe(partitionId, RequestType.GET, getHandler);
        });
    return future;
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return removeLeaderHandlersAsync(partitionId);
  }

  private ActorFuture<Void> removeLeaderHandlersAsync(final int partitionId) {
    return actor.call(() -> removeLeaderHandlers(partitionId));
  }

  private void removeLeaderHandlers(final int partitionId) {
    cleanLeadingPartition(partitionId);
  }

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    serverTransport.unsubscribe(partitionId, RequestType.GET);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(getHandler::onDiskSpaceNotAvailable);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(getHandler::onDiskSpaceAvailable);
  }
}
