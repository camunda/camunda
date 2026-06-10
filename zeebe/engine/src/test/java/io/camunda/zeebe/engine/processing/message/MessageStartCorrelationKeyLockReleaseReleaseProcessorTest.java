/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies the {@code P_K}-side {@code RELEASE} reply handler in isolation: it releases the
 * correlation-key lock and picks up the next buffered message only while the lock is still held by
 * the exact holder the reply names, and ignores stale / redelivered replies (the idempotency guard
 * that protects a successor's lock). The end-to-end multi-partition flow is covered separately.
 */
public final class MessageStartCorrelationKeyLockReleaseReleaseProcessorTest {

  private static final String PROCESS_ID = "wf";
  private static final String CORRELATION_KEY = "ck";
  private static final long HOLDER_KEY = Protocol.encodePartitionId(2, 7);
  private static final long RECORD_KEY = Protocol.encodePartitionId(1, 11);
  private static final long REQUEST_KEY = Protocol.encodePartitionId(1, 42);

  private MessageState messageState;
  private BpmnBufferedMessageStartEventBehavior bufferedBehavior;
  private StateWriter stateWriter;
  private MessageStartCorrelationKeyLockReleaseReleaseProcessor processor;

  @BeforeEach
  void setUp() {
    messageState = mock(MessageState.class);
    bufferedBehavior = mock(BpmnBufferedMessageStartEventBehavior.class);
    stateWriter = mock(StateWriter.class);
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    processor =
        new MessageStartCorrelationKeyLockReleaseReleaseProcessor(
            messageState, bufferedBehavior, writers);
  }

  @Test
  void shouldReleaseLockAndPickUpNextBufferedMessageWhenHolderStillHoldsLock() {
    // given the lock is still held by the named holder
    when(messageState.getCrossPartitionStartLockHolder(any(), any())).thenReturn(HOLDER_KEY);

    // when
    processor.processRecord(releaseRecord(HOLDER_KEY));

    // then a RELEASED event is written carrying the holder ...
    final var captor = ArgumentCaptor.forClass(MessageStartCorrelationKeyLockReleaseRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(RECORD_KEY),
            eq(MessageStartCorrelationKeyLockReleaseIntent.RELEASED),
            captor.capture());
    final var released = captor.getValue();
    assertThat(released.getRequestKey()).isEqualTo(REQUEST_KEY);
    assertThat(released.getHolders()).hasSize(1);
    final var holder = released.getHolders().getFirst();
    assertThat(holder.getProcessInstanceKey()).isEqualTo(HOLDER_KEY);
    assertThat(holder.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(holder.getCorrelationKey()).isEqualTo(CORRELATION_KEY);

    // ... and the next buffered message for that correlation key is picked up
    verify(bufferedBehavior)
        .correlateNextBufferedMessage(
            eq(BufferUtil.wrapString(PROCESS_ID)),
            eq(BufferUtil.wrapString(CORRELATION_KEY)),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  void shouldIgnoreReleaseWhenLockHeldByDifferentHolder() {
    // given the lock has since been re-acquired by a different instance for the same key
    when(messageState.getCrossPartitionStartLockHolder(any(), any())).thenReturn(HOLDER_KEY + 1);

    // when a stale/redelivered RELEASE for the old holder arrives
    processor.processRecord(releaseRecord(HOLDER_KEY));

    // then the successor's lock is left untouched and nothing is picked up
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verifyNoInteractions(bufferedBehavior);
  }

  @Test
  void shouldIgnoreReleaseWhenLockAlreadyGone() {
    // given the lock has already been released (absent entry → -1)
    when(messageState.getCrossPartitionStartLockHolder(any(), any())).thenReturn(-1L);

    // when a redelivered RELEASE arrives
    processor.processRecord(releaseRecord(HOLDER_KEY));

    // then it is a no-op
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verifyNoInteractions(bufferedBehavior);
  }

  @SuppressWarnings("unchecked")
  private TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> releaseRecord(
      final long holderProcessInstanceKey) {
    final var value = new MessageStartCorrelationKeyLockReleaseRecord().setRequestKey(REQUEST_KEY);
    value
        .addHolder()
        .setProcessInstanceKey(holderProcessInstanceKey)
        .setBpmnProcessId(PROCESS_ID)
        .setCorrelationKey(CORRELATION_KEY)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> record = mock(TypedRecord.class);
    when(record.getKey()).thenReturn(RECORD_KEY);
    when(record.getValue()).thenReturn(value);
    return record;
  }
}
