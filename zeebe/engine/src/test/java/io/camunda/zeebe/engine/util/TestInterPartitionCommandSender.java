/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class TestInterPartitionCommandSender implements InterPartitionCommandSender {

  private final Map<Integer, CompletableFuture<LogStreamWriter>> writers =
      new ConcurrentHashMap<>();
  private final Function<Integer, LogStreamWriter> writerFactory;
  private CommandInterceptor interceptor = CommandInterceptor.SEND_ALL;

  public TestInterPartitionCommandSender(final Function<Integer, LogStreamWriter> writerFactory) {
    this.writerFactory = writerFactory;
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command) {
    sendCommand(receiverPartitionId, valueType, intent, null, command);
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command) {
    if (!interceptor.shouldSend(receiverPartitionId, valueType, intent, recordKey, command)) {
      return;
    }
    final var metadata =
        new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);
    final var writer = writers.computeIfAbsent(receiverPartitionId, i -> new CompletableFuture<>());
    final LogAppendEntry entry;
    if (recordKey != null) {
      entry = LogAppendEntry.of(recordKey, metadata, command);
    } else {
      entry = LogAppendEntry.of(metadata, command);
    }

    writer.thenAccept(w -> w.tryWrite(WriteContext.interPartition(), entry));
  }

  // Pre-initialize dedicated writers.
  // We must build new writers because reusing the writers from the environmentRule is unsafe.
  // We can't build them on-demand during `sendCommand` because that might run within an actor
  // context where we can't build new `SyncLogStream`s.
  public void initializeWriters(final int partitionCount) {
    for (int i = Protocol.DEPLOYMENT_PARTITION;
        i < Protocol.DEPLOYMENT_PARTITION + partitionCount;
        i++) {
      writers.computeIfAbsent(i, k -> new CompletableFuture<>()).complete(writerFactory.apply(i));
    }
  }

  public void intercept(final CommandInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @FunctionalInterface
  public interface CommandInterceptor {
    CommandInterceptor SEND_ALL =
        (receiverPartitionId, valueType, intent, recordKey, command) -> true;

    CommandInterceptor DROP_ALL =
        (receiverPartitionId, valueType, intent, recordKey, command) -> false;

    /**
     * @return true if the command should be sent, false if not.
     */
    boolean shouldSend(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue command);
  }
}
