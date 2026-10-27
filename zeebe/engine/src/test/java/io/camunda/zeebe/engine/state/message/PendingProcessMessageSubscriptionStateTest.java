/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class PendingProcessMessageSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessMessageSubscriptionState persistentState;
  private PendingProcessMessageSubscriptionState pendingSubscriptionState;
  private TransientPendingSubscriptionState transientSubscriptionState;

  @Before
  public void setUp() {
    persistentState = stateRule.getProcessingState().getProcessMessageSubscriptionState();
    pendingSubscriptionState =
        stateRule.getProcessingState().getPendingProcessMessageSubscriptionState();
    transientSubscriptionState =
        stateRule.getProcessingState().getTransientPendingSubscriptionState();
  }

  @Test
  public void shouldNoVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record1);
    pendingSubscriptionState.onSent(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    pendingSubscriptionState.onSent(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        1_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitSubscriptionBeforeTime() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record1);
    pendingSubscriptionState.onSent(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    updateSubscriptionRecord(2L, record2);
    pendingSubscriptionState.onSent(record2, 3_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindSubscriptionBeforeTimeInOrder() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record1);
    pendingSubscriptionState.onSent(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    updateSubscriptionRecord(2L, record2);
    pendingSubscriptionState.onSent(record2, 2_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        3_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitSubscriptionIfOpened() {
    // given
    final ProcessMessageSubscriptionRecord record1 = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record1);
    pendingSubscriptionState.onSent(record1, 1_000L);

    final ProcessMessageSubscriptionRecord record2 = subscriptionRecordWithElementInstanceKey(2L);
    updateSubscriptionRecord(2L, record2);
    pendingSubscriptionState.onSent(record2, 2_000L);

    persistentState.updateToOpenedState(record2.setSubscriptionPartitionId(3));
    removeFromTransientSubscriptionState(record2);

    // then
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateSubscriptionSentTime() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);

    // when
    updateSubscriptionRecord(1L, record);
    pendingSubscriptionState.onSent(record, 1_000L);

    // then
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    final ProcessMessageSubscription existingSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    pendingSubscriptionState.onSent(existingSubscription.getRecord(), 1_500);

    keys.clear();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateOpenState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record);
    pendingSubscriptionState.onSent(record, 1_000L);

    final ProcessMessageSubscription subscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    Assertions.assertThat(subscription.isOpening()).isTrue();

    // when
    persistentState.updateToOpenedState(record.setSubscriptionPartitionId(3));
    removeFromTransientSubscriptionState(record);

    // then
    final ProcessMessageSubscription updatedSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    Assertions.assertThat(updatedSubscription.isOpening()).isFalse();

    // and
    assertThat(updatedSubscription.getRecord().getSubscriptionPartitionId()).isEqualTo(3);

    // and
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUpdateCloseState() {
    // given
    final ProcessMessageSubscriptionRecord record = subscriptionRecordWithElementInstanceKey(1L);
    updateSubscriptionRecord(1L, record);
    pendingSubscriptionState.onSent(record, 1_000L);

    persistentState.updateToOpenedState(record.setSubscriptionPartitionId(3));
    final ProcessMessageSubscription subscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    Assertions.assertThat(subscription.isClosing()).isFalse();

    // when
    persistentState.updateToClosingState(record);
    transientSubscriptionState.update(
        new PendingSubscription(
            record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()),
        InstantSource.system().millis());
    pendingSubscriptionState.onSent(record, 1_000L);

    // then
    final ProcessMessageSubscription updatedSubscription =
        persistentState.getSubscription(
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(updatedSubscription.isClosing()).isTrue();

    // and
    final List<Long> keys = new ArrayList<>();
    pendingSubscriptionState.visitPending(
        2_000, s -> keys.add(s.getRecord().getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  private void updateSubscriptionRecord(
      final long key, final ProcessMessageSubscriptionRecord record1) {
    persistentState.put(key, record1);
    addToTransientSubscriptionState(record1);
  }

  private void addToTransientSubscriptionState(final ProcessMessageSubscriptionRecord record1) {
    transientSubscriptionState.add(
        new PendingSubscription(
            record1.getElementInstanceKey(), record1.getMessageName(), record1.getTenantId()),
        InstantSource.system().millis());
  }

  private void removeFromTransientSubscriptionState(final ProcessMessageSubscriptionRecord record) {
    transientSubscriptionState.remove(
        new PendingSubscription(
            record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()));
  }

  private ProcessMessageSubscriptionRecord subscriptionRecordWithElementInstanceKey(
      final long elementInstanceKey) {
    return subscriptionRecord("handler", "messageName", "correlationKey", elementInstanceKey);
  }

  private ProcessMessageSubscriptionRecord subscriptionRecord(
      final String name, final String correlationKey, final long elementInstanceKey) {
    return subscriptionRecord("handler", name, correlationKey, elementInstanceKey);
  }

  private ProcessMessageSubscriptionRecord subscriptionRecord(
      final String handlerId,
      final String name,
      final String correlationKey,
      final long elementInstanceKey) {
    return new ProcessMessageSubscriptionRecord()
        .setProcessInstanceKey(1L)
        .setElementInstanceKey(elementInstanceKey)
        .setProcessDefinitionKey(2L)
        .setBpmnProcessId(wrapString("process"))
        .setElementId(wrapString(handlerId))
        .setMessageName(wrapString(name))
        .setCorrelationKey(wrapString(correlationKey))
        .setInterrupting(true)
        .setSubscriptionPartitionId(1);
  }
}
