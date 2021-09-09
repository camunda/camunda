/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.externalapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;

public final class ExternalApiServiceImpl extends Actor
    implements PartitionListener, DiskSpaceUsageListener, ExternalApiService {

  private final PartitionAwareRequestLimiter limiter;
  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler commandHandler;
  private final QueryApiRequestHandler queryHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final String actorName;

  public ExternalApiServiceImpl(
      final ServerTransport serverTransport,
      final BrokerInfo localBroker,
      final PartitionAwareRequestLimiter limiter) {
    this.serverTransport = serverTransport;
    this.limiter = limiter;
    commandHandler = new CommandApiRequestHandler();
    queryHandler = new QueryApiRequestHandler();
    actorName = buildActorName(localBroker.getNodeId(), "ExternalApiService");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorClosing() {
    for (final Integer leadPartition : leadPartitions) {
      removeForPartitionId(leadPartition);
    }
    leadPartitions.clear();
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
          limiter.addPartition(partitionId);

          logStream
              .newLogStreamRecordWriter()
              .onComplete(
                  (recordWriter, error) -> {
                    if (error == null) {

                      final var requestLimiter = limiter.getLimiter(partitionId);
                      commandHandler.addPartition(partitionId, recordWriter, requestLimiter);
                      serverTransport.subscribe(partitionId, RequestType.COMMAND, commandHandler);
                      queryHandler.addPartition(partitionId, queryService);
                      serverTransport.subscribe(partitionId, RequestType.QUERY, queryHandler);
                      future.complete(null);
                    } else {
                      Loggers.SYSTEM_LOGGER.error(
                          "Error on retrieving write buffer from log stream {}",
                          partitionId,
                          error);
                      future.completeExceptionally(error);
                    }
                  });
        });
    return future;
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return removeLeaderHandlersAsync(partitionId);
  }

  private ActorFuture<Void> removeLeaderHandlersAsync(final int partitionId) {
    return actor.call(
        () -> {
          commandHandler.removePartition(partitionId);
          queryHandler.removePartition(partitionId);
          cleanLeadingPartition(partitionId);
        });
  }

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    limiter.removePartition(partitionId);
    serverTransport.unsubscribe(partitionId);
  }

  @Override
  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport);
  }

  @Override
  public Consumer<TypedRecord<?>> getOnProcessedListener(final int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
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
