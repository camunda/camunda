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
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;

public final class CommandApiService extends Actor implements PartitionListener {

  private final PartitionAwareRequestLimiter limiter;
  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler requestHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final String actorName;
  private final CommandTracer tracer;

  public CommandApiService(
      final ServerTransport serverTransport,
      final BrokerInfo localBroker,
      final PartitionAwareRequestLimiter limiter,
      final CommandTracer tracer) {
    this.serverTransport = serverTransport;
    this.limiter = limiter;
    requestHandler = new CommandApiRequestHandler();
    this.actorName = buildActorName(localBroker.getNodeId(), "CommandApiService");
    this.tracer = tracer;
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
  public void onBecomingFollower(
      final int partitionId, final long term, final LogStream logStream) {
    actor.call(
        () -> {
          requestHandler.removePartition(logStream);
          cleanLeadingPartition(partitionId);
        });
  }

  @Override
  public void onBecomingLeader(final int partitionId, final long term, final LogStream logStream) {
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

                    } else {
                      // TODO https://github.com/zeebe-io/zeebe/issues/3499
                      // the best would be to return a future onBecomingLeader
                      // when one of these futures failed we need to stop the partition installation
                      // and
                      // step down
                      // because then otherwise we are not correctly installed
                      Loggers.SYSTEM_LOGGER.error(
                          "Error on retrieving write buffer from log stream {}",
                          partitionId,
                          error);
                    }
                  });
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

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport, tracer);
  }

  public Consumer<TypedRecord> getOnProcessedListener(final int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
  }
}
