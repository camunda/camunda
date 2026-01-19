/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.queryapi.QueryApiRequestHandler;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import java.util.HashSet;

public final class CommandApiServiceImpl extends Actor
    implements DiskSpaceUsageListener, CommandApiService {

  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler commandHandler;
  private final QueryApiRequestHandler queryHandler;
  private final HashSet<PartitionId> leadPartitions = new HashSet<>();
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
    for (final var leadPartition : leadPartitions) {
      unregisterHandlersActorless(leadPartition);
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
  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport);
  }

  @Override
  public void onRecovered(final PartitionId partitionId) {
    commandHandler.onRecovered(partitionId);
  }

  @Override
  public void onPaused(final PartitionId partitionId) {
    commandHandler.onPaused(partitionId);
  }

  @Override
  public void onResumed(final PartitionId partitionId) {
    commandHandler.onResumed(partitionId);
  }

  @Override
  public ActorFuture<Void> registerHandlers(
      final PartitionId partitionId, final LogStream logStream, final QueryService queryService) {
    return actor.call(
        () -> {
          // create the writer immediately so if the logStream is closed, this will throw an
          // exception immediately
          final var logStreamWriter = logStream.newLogStreamWriter();
          leadPartitions.add(partitionId);
          queryHandler.addPartition(partitionId.id(), queryService);
          serverTransport.subscribe(partitionId, RequestType.QUERY, queryHandler);
          commandHandler.addPartition(partitionId, logStreamWriter);
          serverTransport.subscribe(partitionId, RequestType.COMMAND, commandHandler);
        });
  }

  @Override
  public ActorFuture<Void> unregisterHandlers(final PartitionId partitionId) {
    return actor.call(() -> unregisterHandlersActorless(partitionId));
  }

  private void unregisterHandlersActorless(final PartitionId partitionId) {
    commandHandler.removePartition(partitionId);
    queryHandler.removePartition(partitionId.id());
    leadPartitions.remove(partitionId);
    serverTransport.unsubscribe(partitionId, RequestType.COMMAND);
    serverTransport.unsubscribe(partitionId, RequestType.QUERY);
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
