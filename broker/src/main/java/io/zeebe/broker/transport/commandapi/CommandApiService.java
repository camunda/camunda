/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;
import java.util.function.Consumer;

public class CommandApiService implements PartitionListener {

  private final CommandApiMessageHandler service;
  private final PartitionAwareRequestLimiter limiter;
  private ServerOutput serverOutput;


  public CommandApiService(ServerOutput serverOutput,
      CommandApiMessageHandler commandApiMessageHandler, PartitionAwareRequestLimiter limiter) {
    this.serverOutput = serverOutput;
    this.limiter = limiter;
    this.service = commandApiMessageHandler;
  }

  @Override
  public void onBecomingFollower(Partition partition) {
    removePartition(partition);
  }

  @Override
  public void onBecomingLeader(Partition partition) {
    addPartition(partition);
  }

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverOutput);
  }

  public CommandApiMessageHandler getCommandApiMessageHandler() {
    return service;
  }

  private void removePartition(Partition partition) {
    limiter.removePartition(partition.getPartitionId());
    service.removePartition(partition.getLogStream());
  }

  private void addPartition(Partition partition) {
    limiter.addPartition(partition.getPartitionId());
    service.addPartition(partition.getLogStream(), limiter.getLimiter(partition.getPartitionId()));
  }

  public Consumer<TypedRecord> getOnProcessedListener(int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
  }
}
