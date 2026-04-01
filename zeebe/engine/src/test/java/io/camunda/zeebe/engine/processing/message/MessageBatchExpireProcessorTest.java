/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageState.ExpiredMessageVisitor;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public final class MessageBatchExpireProcessorTest {

  private static final long NOW = 1000L;

  private final StateWriter stateWriter = Mockito.mock(StateWriter.class);
  private final TypedCommandWriter commandWriter = Mockito.mock(TypedCommandWriter.class);
  private final MessageState messageState = Mockito.mock(MessageState.class);
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.systemDefault());

  @Test
  public void shouldExpireMessagesFromState() {
    // given
    stubVisitWithKeys(1L, 2L);
    doNothing().when(stateWriter).appendFollowUpEvent(anyLong(), any(), any());

    // when
    createProcessor(100).processRecord(emptyBatchRecord());

    // then
    verify(stateWriter).appendFollowUpEvent(eq(1L), eq(MessageIntent.EXPIRED), any());
    verify(stateWriter).appendFollowUpEvent(eq(2L), eq(MessageIntent.EXPIRED), any());
  }

  @Test
  public void shouldWriteFollowUpWhenBatchLimitReached() {
    // given — visitor returns true (hasMore) because batch limit stops it
    stubVisitReturning(true, 1L, 2L);
    doNothing().when(stateWriter).appendFollowUpEvent(anyLong(), any(), any());

    // when
    createProcessor(2).processRecord(emptyBatchRecord());

    // then
    verify(stateWriter, times(2)).appendFollowUpEvent(anyLong(), eq(MessageIntent.EXPIRED), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(-1L), eq(MessageBatchIntent.EXPIRE), any(MessageBatchRecord.class));
  }

  @Test
  public void shouldNotWriteFollowUpWhenAllMessagesProcessed() {
    // given — visitor returns false (no more)
    stubVisitWithKeys(1L);
    doNothing().when(stateWriter).appendFollowUpEvent(anyLong(), any(), any());

    // when
    createProcessor(100).processRecord(emptyBatchRecord());

    // then
    verify(stateWriter).appendFollowUpEvent(eq(1L), eq(MessageIntent.EXPIRED), any());
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), any(), any(MessageBatchRecord.class));
  }

  @Test
  public void shouldDoNothingWhenNoExpiredMessages() {
    // given
    when(messageState.visitMessagesWithDeadlineBeforeTimestamp(eq(NOW), any(), any()))
        .thenReturn(false);

    // when
    createProcessor(100).processRecord(emptyBatchRecord());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), any(), any(MessageBatchRecord.class));
  }

  @Test
  public void shouldWriteFollowUpWhenBatchIsFull() {
    // given — 3 keys but batch runs out of space after the first event
    stubVisitWithKeys(1L, 2L, 3L);
    doNothing().when(stateWriter).appendFollowUpEvent(anyLong(), any(), any());
    final var processor = createProcessor(100);
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true, false);

    // when
    processor.processRecord(emptyBatchRecord());

    // then — writes only 1 event and a follow-up to continue later
    verify(stateWriter, times(1)).appendFollowUpEvent(anyLong(), eq(MessageIntent.EXPIRED), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(-1L), eq(MessageBatchIntent.EXPIRE), any(MessageBatchRecord.class));
  }

  @Test
  public void shouldWriteFollowUpWhenBatchIsFullWithMoreRemaining() {
    // given — batch limit is 2, visitor indicates more remain, and batch fills after first event
    stubVisitReturning(true, 1L, 2L);
    doNothing().when(stateWriter).appendFollowUpEvent(anyLong(), any(), any());
    final var processor = createProcessor(2);
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true, false);

    // when
    processor.processRecord(emptyBatchRecord());

    // then — writes follow-up to continue with remaining messages
    verify(stateWriter, times(1)).appendFollowUpEvent(anyLong(), eq(MessageIntent.EXPIRED), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(-1L), eq(MessageBatchIntent.EXPIRE), any(MessageBatchRecord.class));
  }

  private MessageBatchExpireProcessor createProcessor(final int batchLimit) {
    // default: always have space unless explicitly stubbed otherwise per test
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);
    return new MessageBatchExpireProcessor(
        stateWriter, commandWriter, messageState, batchLimit, clock);
  }

  private UnwrittenRecord emptyBatchRecord() {
    return new UnwrittenRecord(-1, 1, new MessageBatchRecord(), new RecordMetadata());
  }

  /** Stubs the visitor to return the given keys, with hasMore = false. */
  private void stubVisitWithKeys(final long... keys) {
    stubVisitReturning(false, keys);
  }

  /**
   * Stubs the visitor to invoke the visitor with the given keys and return the specified result.
   */
  private void stubVisitReturning(final boolean hasMore, final long... keys) {
    when(messageState.visitMessagesWithDeadlineBeforeTimestamp(eq(NOW), any(), any()))
        .thenAnswer(
            (Answer<Boolean>)
                invocation -> {
                  final ExpiredMessageVisitor visitor = invocation.getArgument(2);
                  for (final long key : keys) {
                    if (!visitor.visit(NOW - 1, key)) {
                      return true;
                    }
                  }
                  return hasMore;
                });
  }
}
