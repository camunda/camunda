/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the appliers that handle cross-partition message-start events. The STARTED applier
 * writes the dedup entry, removes the pending-ask, and conditionally marks the lock entry as
 * cross-partition; the rejection appliers only remove the pending-ask.
 */
public final class MessageStartProcessInstanceReplyApplierTest {

  private static final long MESSAGE_KEY = 1L;
  private static final long PROCESS_DEFINITION_KEY = 100L;
  private static final long PROCESS_INSTANCE_KEY = 1000L;
  private static final String TENANT = "<default>";
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "bid-1";

  private MutableMessageStartProcessInstanceDedupState mockDedupState;
  private MutableMessageStartProcessInstanceAskState mockAskState;
  private MutableMessageState mockMessageState;

  @BeforeEach
  void setUp() {
    mockDedupState = mock(MutableMessageStartProcessInstanceDedupState.class);
    mockAskState = mock(MutableMessageStartProcessInstanceAskState.class);
    mockMessageState = mock(MutableMessageState.class);
  }

  private MessageStartProcessInstanceRequestRecord createRecord() {
    final var record = new MessageStartProcessInstanceRequestRecord();
    record.setMessageKey(MESSAGE_KEY);
    record.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    record.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    record.setBpmnProcessId(BPMN_PROCESS_ID);
    record.setCorrelationKey(CORRELATION_KEY);
    record.setBusinessId(BUSINESS_ID);
    record.setTenantId(TENANT);
    return record;
  }

  @Nested
  class StartedApplier {

    @Test
    void shouldAlwaysWriteDedupEntryAndClearPendingAsk() {
      // given
      final var applier = newApplier();
      final var record = createRecord();
      when(mockMessageState.existActiveProcessInstance(any(), any(), any())).thenReturn(false);

      // when
      applier.applyState(1L, record);

      // then
      verify(mockDedupState).putActive(PROCESS_DEFINITION_KEY, MESSAGE_KEY, PROCESS_INSTANCE_KEY);
      verify(mockAskState).remove(MESSAGE_KEY, PROCESS_DEFINITION_KEY);
    }

    @Test
    void shouldRecordCrossPartitionBusinessIdWhenLockEntryExists() {
      // given the CORRELATED applier (run before this one) has written the lock entry
      final var applier = newApplier();
      final var record = createRecord();
      when(mockMessageState.existActiveProcessInstance(
              eq(TENANT),
              eq(BufferUtil.wrapString(BPMN_PROCESS_ID)),
              eq(BufferUtil.wrapString(CORRELATION_KEY))))
          .thenReturn(true);

      // when
      applier.applyState(1L, record);

      // then
      verify(mockMessageState)
          .putCrossPartitionStartLockBusinessId(
              eq(BufferUtil.wrapString(BPMN_PROCESS_ID)),
              eq(BufferUtil.wrapString(CORRELATION_KEY)),
              eq(BufferUtil.wrapString(BUSINESS_ID)));
    }

    @Test
    void shouldNotRecordCrossPartitionBusinessIdWhenLockEntryAbsent() {
      // given no local lock entry — e.g., this applier is firing on P_B where the holder PI is
      // local but no CORRELATED applier wrote a lock entry, or on the first STARTED firing in
      // single-partition mode before CORRELATED has run.
      final var applier = newApplier();
      final var record = createRecord();
      when(mockMessageState.existActiveProcessInstance(any(), any(), any())).thenReturn(false);

      // when
      applier.applyState(1L, record);

      // then
      verify(mockMessageState, never()).putCrossPartitionStartLockBusinessId(any(), any(), any());
    }

    @Test
    void shouldNotRecordCrossPartitionBusinessIdWhenCorrelationKeyEmpty() {
      // given a record without a correlation key (start event without a correlation key) — no
      // lock entry can exist for this case
      final var applier = newApplier();
      final var record = createRecord();
      record.setCorrelationKey("");

      // when
      applier.applyState(1L, record);

      // then
      verify(mockMessageState, never()).putCrossPartitionStartLockBusinessId(any(), any(), any());
    }

    private MessageStartProcessInstanceStartedV1Applier newApplier() {
      return new MessageStartProcessInstanceStartedV1Applier(
          mockDedupState, mockAskState, mockMessageState);
    }
  }

  @Nested
  class UniquenessRejectedApplier {

    @Test
    void shouldRemovePendingAskOnUniquenessRejected() {
      // given
      final var applier = new MessageStartProcessInstanceUniquenessRejectedV1Applier(mockAskState);
      final var record = createRecord();

      // when
      applier.applyState(1L, record);

      // then
      verify(mockAskState).remove(MESSAGE_KEY, PROCESS_DEFINITION_KEY);
    }
  }

  @Nested
  class NoSubscriptionRejectedApplier {

    @Test
    void shouldRemovePendingAskOnNoSubscriptionRejected() {
      // given
      final var applier =
          new MessageStartProcessInstanceNoSubscriptionRejectedV1Applier(mockAskState);
      final var record = createRecord();

      // when
      applier.applyState(1L, record);

      // then
      verify(mockAskState).remove(MESSAGE_KEY, PROCESS_DEFINITION_KEY);
    }
  }
}
