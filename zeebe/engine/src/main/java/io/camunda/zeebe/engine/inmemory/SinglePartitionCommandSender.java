/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;

public final class SinglePartitionCommandSender implements InterPartitionCommandSender {

  private final LogStreamWriter writer;

  public SinglePartitionCommandSender(final LogStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command) {

    final RecordMetadata metadata =
        new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);

    writer.tryWrite(WriteContext.interPartition(), LogAppendEntry.of(metadata, command));
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command) {

    final RecordMetadata metadata =
        new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);

    writer.tryWrite(WriteContext.interPartition(), LogAppendEntry.of(recordKey, metadata, command));
  }
}
