/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the appliers that handle cross-partition message-start events. The STARTED applier
 * writes the dedup entry (using the deadline carried on the request record) and removes the
 * pending-ask; the rejection appliers only remove the pending-ask.
 */
public final class MessageStartProcessInstanceReplyApplierTest {

  private static final long MESSAGE_KEY = 1L;
  private static final long PROCESS_DEFINITION_KEY = 100L;
  private static final long PROCESS_INSTANCE_KEY = 1000L;
  private static final long MESSAGE_DEADLINE = 65_000L;
  private static final String TENANT = "<default>";
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "bid-1";

  private MutableMessageStartProcessInstanceDedupState mockDedupState;
  private MutableMessageStartProcessInstanceAskState mockAskState;

  @BeforeEach
  void setUp() {
    mockDedupState = mock(MutableMessageStartProcessInstanceDedupState.class);
    mockAskState = mock(MutableMessageStartProcessInstanceAskState.class);
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
    record.setMessageDeadline(MESSAGE_DEADLINE);
    return record;
  }

  @Nested
  class StartedApplier {

    @Test
    void shouldAlwaysWriteDedupEntryAndClearPendingAsk() {
      // given
      final var applier = newApplier();
      final var record = createRecord();

      // when
      applier.applyState(1L, record);

      // then
      verify(mockDedupState)
          .put(PROCESS_DEFINITION_KEY, MESSAGE_KEY, PROCESS_INSTANCE_KEY, MESSAGE_DEADLINE);
      verify(mockAskState).remove(MESSAGE_KEY, PROCESS_DEFINITION_KEY);
    }

    private MessageStartProcessInstanceStartedV1Applier newApplier() {
      return new MessageStartProcessInstanceStartedV1Applier(mockDedupState, mockAskState);
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
