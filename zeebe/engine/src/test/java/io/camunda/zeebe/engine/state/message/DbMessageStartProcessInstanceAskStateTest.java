/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import org.junit.Rule;
import org.junit.Test;

public class DbMessageStartProcessInstanceAskStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  @Test
  public void shouldPutAndGetPendingAsk() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    final var record = createRecord(123L, 456L, "test-business-id", "test-process");
    final var ask = new MessageStartProcessInstanceAsk().wrap(record);

    // when
    state.put(ask);

    // then
    final var retrieved = state.get(123L, 456L);
    assertThat(retrieved).isNotNull();
    assertThat(retrieved.getMessageKey()).isEqualTo(123L);
    assertThat(retrieved.getProcessDefinitionKey()).isEqualTo(456L);
  }

  @Test
  public void shouldRemovePendingAsk() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    final var record = createRecord(123L, 456L, "test-business-id", "test-process");
    final var ask = new MessageStartProcessInstanceAsk().wrap(record);
    state.put(ask);

    // when
    state.remove(123L, 456L);

    // then
    assertThat(state.get(123L, 456L)).isNull();
  }

  @Test
  public void shouldRemoveAllPendingAsksForGivenMessageKey() {
    // given two pending asks for the same messageKey targeting different process definitions, and
    // one pending ask for a different messageKey that must survive
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(7L, 100L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(7L, 200L, "b2", "p2")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(8L, 300L, "b3", "p3")));

    // when
    state.removeAllByMessageKey(7L);

    // then
    assertThat(state.get(7L, 100L)).isNull();
    assertThat(state.get(7L, 200L)).isNull();
    assertThat(state.get(8L, 300L)).isNotNull();
  }

  @Test
  public void shouldVisitAllPendingAsks() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(3L, 30L, "b3", "p3")));

    // when
    final var visited = new java.util.ArrayList<Long>();
    state.forEach((messageKey, pdKey, ask) -> visited.add(messageKey));

    // then
    assertThat(visited).containsExactlyInAnyOrder(1L, 2L, 3L);
  }

  @Test
  public void shouldGetPendingAsksPastDeadline() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    // All entries are added with timestamp 0, so they are all past any positive deadline
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));

    // when - check with a deadline in the future
    final var pendingAsks = state.getPendingAsksPastDeadline(System.currentTimeMillis());

    // then
    assertThat(pendingAsks).hasSize(2);
  }

  @Test
  public void shouldPopulateRecordFromAsk() {
    // given
    final var originalRecord = createRecord(123L, 456L, "test-business-id", "test-process");
    originalRecord.setMessageName("test-message");
    originalRecord.setCorrelationKey("test-correlation");
    originalRecord.setStartEventId("start-event");
    originalRecord.setMessageStartEventSubscriptionKey(789L);
    originalRecord.setTenantId("test-tenant");
    originalRecord.setMessageDeadline(99999L);

    final var ask = new MessageStartProcessInstanceAsk().wrap(originalRecord);

    // when
    final var populatedRecord = new MessageStartProcessInstanceRequestRecord();
    ask.populateRecord(populatedRecord);

    // then
    assertThat(populatedRecord.getMessageKey()).isEqualTo(123L);
    assertThat(populatedRecord.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(populatedRecord.getBusinessId()).isEqualTo("test-business-id");
    assertThat(populatedRecord.getBpmnProcessId()).isEqualTo("test-process");
    assertThat(populatedRecord.getMessageName()).isEqualTo("test-message");
    assertThat(populatedRecord.getCorrelationKey()).isEqualTo("test-correlation");
    assertThat(populatedRecord.getStartEventId()).isEqualTo("start-event");
    assertThat(populatedRecord.getMessageStartEventSubscriptionKey()).isEqualTo(789L);
    assertThat(populatedRecord.getTenantId()).isEqualTo("test-tenant");
    assertThat(populatedRecord.getMessageDeadline()).isEqualTo(99999L);
  }

  @Test
  public void shouldDefaultRejectionCountToZeroForFreshAsk() {
    // given a fresh ask sourced from a request record (never rejected)
    final var ask = new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p"));

    // then the P_K-local retry bookkeeping defaults to zero, keeping the ask at the base re-send
    // cadence and ensuring values persisted before this field existed decode unchanged
    assertThat(ask.getRejectionCount()).isZero();
  }

  @Test
  public void shouldPersistRejectionCount() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    final var ask =
        new MessageStartProcessInstanceAsk()
            .wrap(createRecord(123L, 456L, "b", "p"))
            .setRejectionCount(3L);

    // when
    state.put(ask);

    // then the rejection count survives the RocksDB round-trip
    final var retrieved = state.get(123L, 456L);
    assertThat(retrieved.getRejectionCount()).isEqualTo(3L);
  }

  @Test
  public void shouldPreserveRejectionCountOnCopy() {
    // given
    final var ask =
        new MessageStartProcessInstanceAsk()
            .wrap(createRecord(1L, 2L, "b", "p"))
            .setRejectionCount(5L);

    // when
    final var copy = ask.copy();

    // then
    assertThat(copy.getRejectionCount()).isEqualTo(5L);
  }

  @Test
  public void shouldIncrementRejectionCountOnBackOff() {
    // given a pending ask with no rejections yet
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));

    // when backed off twice
    state.backOff(1L, 2L);
    state.backOff(1L, 2L);

    // then the persisted rejection count reflects both rejections
    assertThat(state.get(1L, 2L).getRejectionCount()).isEqualTo(2L);
  }

  @Test
  public void shouldNotResetSendEligibilityOnBackOff() {
    // given a pending ask that has already been sent (transient last-sent advanced past any
    // positive deadline)
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));
    state.updateLastSentTime(1L, 2L, 10_000L);

    // when the ask is backed off
    state.backOff(1L, 2L);

    // then back-off does not reset the transient send-tracking: the ask is still considered sent at
    // 10_000 and is not made immediately eligible again (which would defeat the back-off)
    assertThat(state.getPendingAsksPastDeadline(10_000L)).isEmpty();
    assertThat(state.getPendingAsksPastDeadline(10_001L)).hasSize(1);
  }

  @Test
  public void shouldBeNoOpWhenBackingOffMissingAsk() {
    // given no pending ask for the key
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();

    // when / then no exception and nothing is created
    state.backOff(7L, 8L);
    assertThat(state.get(7L, 8L)).isNull();
  }

  @Test
  public void shouldCapRejectionCount() {
    // given a pending ask
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));

    // when backed off far more often than the cap (30)
    for (int i = 0; i < 100; i++) {
      state.backOff(1L, 2L);
    }

    // then the persisted count saturates at the cap, so it never overflows when the scheduler
    // computes 2^rejectionCount
    assertThat(state.get(1L, 2L).getRejectionCount()).isEqualTo(30L);
  }

  private MessageStartProcessInstanceRequestRecord createRecord(
      final long messageKey,
      final long processDefinitionKey,
      final String businessId,
      final String bpmnProcessId) {
    return new MessageStartProcessInstanceRequestRecord()
        .setMessageKey(messageKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setBusinessId(businessId)
        .setBpmnProcessId(bpmnProcessId);
  }
}
