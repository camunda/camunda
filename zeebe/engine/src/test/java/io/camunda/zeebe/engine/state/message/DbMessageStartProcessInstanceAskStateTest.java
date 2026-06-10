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
  public void shouldDefaultBackoffMagnitudeToZeroForFreshAsk() {
    // given a fresh ask sourced from a request record (no back-off ever applied)
    final var ask = new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p"));

    // then the P_K-local retry bookkeeping defaults to zero, keeping the ask eligible for
    // base-interval re-send and ensuring values persisted before this field existed decode
    // unchanged
    assertThat(ask.getRetryBackoffMillis()).isZero();
  }

  @Test
  public void shouldPersistBackoffMagnitude() {
    // given
    final var state = stateRule.getProcessingState().getMessageStartProcessInstanceAskState();
    final var ask =
        new MessageStartProcessInstanceAsk()
            .wrap(createRecord(123L, 456L, "b", "p"))
            .setRetryBackoffMillis(4000L);

    // when
    state.put(ask);

    // then the back-off magnitude survives the RocksDB round-trip
    final var retrieved = state.get(123L, 456L);
    assertThat(retrieved.getRetryBackoffMillis()).isEqualTo(4000L);
  }

  @Test
  public void shouldPreserveBackoffMagnitudeOnCopy() {
    // given
    final var ask =
        new MessageStartProcessInstanceAsk()
            .wrap(createRecord(1L, 2L, "b", "p"))
            .setRetryBackoffMillis(8000L);

    // when
    final var copy = ask.copy();

    // then
    assertThat(copy.getRetryBackoffMillis()).isEqualTo(8000L);
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
