/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.transport.ServerOutput;
import java.util.function.Consumer;

public class CommandApiService implements PartitionListener {

  private final CommandApiMessageHandler service;
  private final PartitionAwareRequestLimiter limiter;
  private final ServerOutput serverOutput;

  public CommandApiService(
      ServerOutput serverOutput,
      CommandApiMessageHandler commandApiMessageHandler,
      PartitionAwareRequestLimiter limiter) {
    this.serverOutput = serverOutput;
    this.limiter = limiter;
    this.service = commandApiMessageHandler;
  }

  @Override
  public void onBecomingFollower(int partitionId, LogStream logStream) {
    limiter.removePartition(partitionId);
    service.removePartition(logStream);
  }

  @Override
  public void onBecomingLeader(int partitionId, LogStream logStream) {
    limiter.addPartition(partitionId);
    service.addPartition(logStream, limiter.getLimiter(partitionId));
  }

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverOutput);
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
