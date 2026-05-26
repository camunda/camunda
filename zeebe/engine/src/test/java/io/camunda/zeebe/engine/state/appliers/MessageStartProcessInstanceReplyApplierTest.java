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
 * Unit tests for the appliers that handle cross-partition message-start events on P_K. Each applier
 * should remove the pending-ask entry to stop the retry scheduler.
 */
public final class MessageStartProcessInstanceReplyApplierTest {

  private static final long MESSAGE_KEY = 1L;
  private static final long PROCESS_DEFINITION_KEY = 100L;
  private static final long PROCESS_INSTANCE_KEY = 1000L;

  private MutableMessageStartProcessInstanceDedupState mockDedupState;
  private MutableMessageStartProcessInstanceAskState mockAskState;

  @BeforeEach
  void setUp() {
    mockDedupState = mock(MutableMessageStartProcessInstanceDedupState.class);
    mockAskState = mock(MutableMessageStartProcessInstanceAskState.class);
  }

  @Nested
  class StartedApplier {

    @Test
    void shouldRemovePendingAskOnStarted() {
      // given
      final var applier =
          new MessageStartProcessInstanceStartedV1Applier(mockDedupState, mockAskState);
      final var record = createRecord();

      // when
      applier.applyState(1L, record);

      // then
      verify(mockAskState).remove(MESSAGE_KEY, PROCESS_DEFINITION_KEY);
    }

    @Test
    void shouldWriteDedupEntryOnStarted() {
      // given
      final var applier =
          new MessageStartProcessInstanceStartedV1Applier(mockDedupState, mockAskState);
      final var record = createRecord();

      // when
      applier.applyState(1L, record);

      // then
      verify(mockDedupState).putActive(PROCESS_DEFINITION_KEY, MESSAGE_KEY, PROCESS_INSTANCE_KEY);
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

  private MessageStartProcessInstanceRequestRecord createRecord() {
    final var record = new MessageStartProcessInstanceRequestRecord();
    record.setMessageKey(MESSAGE_KEY);
    record.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    record.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    return record;
  }
}
