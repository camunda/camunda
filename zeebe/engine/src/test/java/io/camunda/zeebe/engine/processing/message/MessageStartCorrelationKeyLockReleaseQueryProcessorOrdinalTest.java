/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.CrossPartitionMessageStartHolderOrigin;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue.MessageStartLockReleaseHolderValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies, with mocked state, which source the {@code P_B}-side query processor stamps the holder
 * storage ordinal from: the {@code PUSHED} event for a leftover origin entry takes the ordinal
 * persisted on that entry, while the {@code RELEASE} reply echoes the holder the query carried. The
 * end-to-end flow on a real engine is covered by {@link
 * MessageStartCorrelationKeyLockReleaseQueryProcessorTest}.
 */
final class MessageStartCorrelationKeyLockReleaseQueryProcessorOrdinalTest {

  private static final String PROCESS_ID = "wf";
  private static final String CORRELATION_KEY = "ck";
  private static final long HOLDER_KEY = Protocol.encodePartitionId(1, 4711);
  private static final long RECORD_KEY = Protocol.encodePartitionId(1, 11);
  private static final long REQUEST_KEY = Protocol.encodePartitionId(1, 42);
  private static final int ORIGIN_ORDINAL = 1234;
  // deliberately different from the origin entry's ordinal so the assertions tell the two sources
  // apart
  private static final int QUERY_ORDINAL = 42;

  private MessageState messageState;
  private StateWriter stateWriter;
  private SubscriptionCommandSender commandSender;
  private MessageStartCorrelationKeyLockReleaseQueryProcessor processor;

  @BeforeEach
  void setUp() {
    messageState = mock(MessageState.class);
    stateWriter = mock(StateWriter.class);
    commandSender = mock(SubscriptionCommandSender.class);
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    processor =
        new MessageStartCorrelationKeyLockReleaseQueryProcessor(
            mock(ElementInstanceState.class),
            mock(BannedInstanceState.class),
            messageState,
            commandSender,
            writers,
            new MessageCorrelationMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void shouldCarryOriginStorageOrdinalOnPushedAndReleaseHolders() {
    // given a gone holder (not active, not banned) that left an origin entry with a non-zero
    // storage ordinal behind
    when(messageState.getCrossPartitionStartHolderOrigin(HOLDER_KEY))
        .thenReturn(
            new CrossPartitionMessageStartHolderOrigin()
                .setBpmnProcessId(BufferUtil.wrapString(PROCESS_ID))
                .setCorrelationKey(BufferUtil.wrapString(CORRELATION_KEY))
                .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .setMessageKey(Protocol.encodePartitionId(2, 7))
                .setStorageOrdinal(ORIGIN_ORDINAL));
    // and a query whose holder carries what P_K's lock entry knows
    final var query = new MessageStartCorrelationKeyLockReleaseRecord().setRequestKey(REQUEST_KEY);
    query
        .addHolder()
        .setProcessInstanceKey(HOLDER_KEY)
        .setBpmnProcessId(PROCESS_ID)
        .setCorrelationKey(CORRELATION_KEY)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setStorageOrdinal(QUERY_ORDINAL);
    @SuppressWarnings("unchecked")
    final TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> record = mock(TypedRecord.class);
    when(record.getKey()).thenReturn(RECORD_KEY);
    when(record.getValue()).thenReturn(query);

    // when
    processor.processRecord(record);

    // then the PUSHED holder carries the origin entry's ordinal
    final var pushedCaptor = ArgumentCaptor.forClass(RecordValue.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(HOLDER_KEY),
            eq(MessageStartCorrelationKeyLockReleaseIntent.PUSHED),
            pushedCaptor.capture());
    final var pushed = (MessageStartCorrelationKeyLockReleaseRecord) pushedCaptor.getValue();
    assertThat(pushed.getHolders())
        .singleElement()
        .extracting(MessageStartLockReleaseHolderValue::getStorageOrdinal)
        .isEqualTo(ORIGIN_ORDINAL);

    // and the RELEASE holder handed to the sender echoes the query holder, so P_K gets back the
    // ordinal its own lock entry sent
    final var releaseHolderCaptor =
        ArgumentCaptor.forClass(MessageStartLockReleaseHolderValue.class);
    verify(commandSender)
        .sendCorrelationKeyLockRelease(eq(REQUEST_KEY), releaseHolderCaptor.capture());
    assertThat(releaseHolderCaptor.getValue().getStorageOrdinal()).isEqualTo(QUERY_ORDINAL);
  }
}
