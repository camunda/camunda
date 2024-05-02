/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import org.junit.Test;
import org.mockito.Mockito;

public final class MessageBatchExpireProcessorTest {

  private final StateWriter stateWriter = Mockito.mock(StateWriter.class);
  final MessageBatchExpireProcessor messageBatchExpireProcessor =
      new MessageBatchExpireProcessor(stateWriter);

  @Test
  public void shouldStopProcessingWhenExceedingBatchLimit() {

    // given
    final var messageBatchRecord =
        new MessageBatchRecord()
            .addMessageKey(1)
            .addMessageKey(2)
            .addMessageKey(3)
            .addMessageKey(4);

    doNothing().when(stateWriter).appendFollowUpEvent(eq(1L), any(), any());
    doNothing().when(stateWriter).appendFollowUpEvent(eq(2L), any(), any());

    final var exceededBatchRecordSizeException =
        new ExceededBatchRecordSizeException(mock(RecordBatchEntry.class), 10, 1, 1);
    doThrow(exceededBatchRecordSizeException)
        .when(stateWriter)
        .appendFollowUpEvent(eq(3L), any(), any());

    // when
    messageBatchExpireProcessor.processRecord(
        new UnwrittenRecord(-1, 1, messageBatchRecord, new RecordMetadata()));

    // then
    verify(stateWriter, times(3)).appendFollowUpEvent(anyLong(), any(), any());
  }
}
