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
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.queryapi.QueryApiRequestHandler;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import org.agrona.collections.IntHashSet;

public final class CommandApiServiceImpl extends Actor
    implements PartitionListener, DiskSpaceUsageListener, CommandApiService {

  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler commandHandler;
  private final QueryApiRequestHandler queryHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final ActorSchedulingService scheduler;

  public CommandApiServiceImpl(
      final ServerTransport serverTransport,
      final ActorSchedulingService scheduler,
      final QueryApiCfg queryApiCfg) {
    this.serverTransport = serverTransport;
    this.scheduler = scheduler;
    commandHandler = new CommandApiRequestHandler();
    queryHandler = new QueryApiRequestHandler(queryApiCfg);
  }

  @Override
  public String getName() {
    return "CommandApiService";
  }

  @Override
  protected void onActorStarting() {
    scheduler.submitActor(queryHandler);
    scheduler.submitActor(commandHandler);
  }

  @Override
  protected void onActorClosing() {
    for (final Integer leadPartition : leadPartitions) {
      removeLeaderHandlers(leadPartition);
    }
    leadPartitions.clear();
    actor.runOnCompletion(
        commandHandler.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            Loggers.TRANSPORT_LOGGER.error("Error closing command api request handler", error);
          }
        });
    actor.runOnCompletion(
        queryHandler.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            Loggers.TRANSPORT_LOGGER.warn("Failed to close query API request handler", error);
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
          queryHandler.addPartition(partitionId, queryService);
          serverTransport.subscribe(partitionId, RequestType.QUERY, queryHandler);

          final var logStreamWriter = logStream.newLogStreamWriter();
          commandHandler.addPartition(partitionId, logStreamWriter);
          serverTransport.subscribe(partitionId, RequestType.COMMAND, commandHandler);
          future.complete(null);
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
    commandHandler.removePartition(partitionId);
    queryHandler.removePartition(partitionId);
    cleanLeadingPartition(partitionId);
  }

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    serverTransport.unsubscribe(partitionId, RequestType.COMMAND);
    serverTransport.unsubscribe(partitionId, RequestType.QUERY);
  }

  @Override
  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport);
  }

  @Override
  public void onRecovered(final int partitionId) {
    commandHandler.onRecovered(partitionId);
  }

  @Override
  public void onPaused(final int partitionId) {
    commandHandler.onPaused(partitionId);
  }

  @Override
  public void onResumed(final int partitionId) {
    commandHandler.onResumed(partitionId);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(commandHandler::onDiskSpaceNotAvailable);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(commandHandler::onDiskSpaceAvailable);
  }
}
