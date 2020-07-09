/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;

public final class CommandApiService extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private final PartitionAwareRequestLimiter limiter;
  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler requestHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final String actorName;

  public CommandApiService(
      final ServerTransport serverTransport,
      final BrokerInfo localBroker,
      final PartitionAwareRequestLimiter limiter) {
    this.serverTransport = serverTransport;
    this.limiter = limiter;
    requestHandler = new CommandApiRequestHandler();
    this.actorName = buildActorName(localBroker.getNodeId(), "CommandApiService");
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
    return actor.call(
        () -> {
          requestHandler.removePartition(partitionId);
          cleanLeadingPartition(partitionId);
        });
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId, final long term, final LogStream logStream) {
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

                      final var requestLimiter = this.limiter.getLimiter(partitionId);
                      requestHandler.addPartition(partitionId, recordWriter, requestLimiter);
                      serverTransport.subscribe(partitionId, requestHandler);
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

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    limiter.removePartition(partitionId);
    serverTransport.unsubscribe(partitionId);
  }

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport);
  }

  public Consumer<TypedRecord> getOnProcessedListener(final int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(requestHandler::onDiskSpaceNotAvailable);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(requestHandler::onDiskSpaceAvailable);
  }
}
