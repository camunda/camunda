/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InMemoryEngine {

  private final Runnable startup;
  private final Runnable shutdown;
  private final LogStreamWriter writer;
  private final RecordStreamView recordStreamView;
  private final InMemoryEngineMonitor engineMonitor;

  public InMemoryEngine(
      final Runnable startup,
      final Runnable shutdown,
      final LogStream logStream,
      final InMemoryEngineMonitor engineMonitor) {
    this.startup = startup;
    this.shutdown = shutdown;

    writer = logStream.newLogStreamWriter();
    recordStreamView =
        new RecordStreamView(logStream.newLogStreamReader(), logStream.getPartitionId());
    this.engineMonitor = engineMonitor;
  }

  public void start() {
    startup.run();
  }

  public void stop() {
    shutdown.run();
  }

  public EventPosition writeCommand(final CommandRecord commandRecord) {
    return writeCommand(commandRecord.valueType(), commandRecord.intent(), commandRecord.command());
  }

  public EventPosition writeCommand(final CommandRecord commandRecord, final long key) {
    return writeCommand(
        commandRecord.valueType(), commandRecord.intent(), commandRecord.command(), key);
  }

  public EventPosition writeCommand(
      final ValueType valueType, final Intent intent, final UnifiedRecordValue command) {
    final RecordMetadata recordMetadata =
        new RecordMetadata().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

    return writer
        .tryWrite(WriteContext.userCommand(intent), LogAppendEntry.of(recordMetadata, command))
        .map(EventPosition::new)
        .getOrElse(new EventPosition(-1));
  }

  public EventPosition writeCommand(
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command,
      final long key) {
    final RecordMetadata recordMetadata =
        new RecordMetadata().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

    return writer
        .tryWrite(WriteContext.userCommand(intent), LogAppendEntry.of(key, recordMetadata, command))
        .map(EventPosition::new)
        .getOrElse(new EventPosition(-1));
  }

  public RecordStreamView getRecordStreamView() {
    return recordStreamView;
  }

  public void waitForIdleState(final Duration timeout) {
    final CompletableFuture<Void> idleState = new CompletableFuture<>();

    engineMonitor.addOnIdleCallback(() -> idleState.complete(null));

    try {
      idleState.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final ExecutionException | InterruptedException | TimeoutException e) {
      // Do nothing. ExecutionExceptions won't appear. The function only completes the future, which
      // in itself does not throw any exceptions.
    }
  }
}
