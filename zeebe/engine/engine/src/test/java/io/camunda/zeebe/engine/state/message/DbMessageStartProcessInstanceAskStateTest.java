/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbMessageStartProcessInstanceAskStateTest {

  private static final String PENDING_ASKS_GAUGE =
      MessageCorrelationMetricsDoc.CROSS_PARTITION_ASKS_PENDING.getName();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;

  @Test
  void shouldPutAndGetPendingAsk() {
    // given
    final var state = processingState.getMessageStartProcessInstanceAskState();
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
  void shouldRemovePendingAsk() {
    // given
    final var state = processingState.getMessageStartProcessInstanceAskState();
    final var record = createRecord(123L, 456L, "test-business-id", "test-process");
    final var ask = new MessageStartProcessInstanceAsk().wrap(record);
    state.put(ask);

    // when
    state.remove(123L, 456L);

    // then
    assertThat(state.get(123L, 456L)).isNull();
  }

  @Test
  void shouldRemoveAllPendingAsksForGivenMessageKey() {
    // given two pending asks for the same messageKey targeting different process definitions, and
    // one pending ask for a different messageKey that must survive
    final var state = processingState.getMessageStartProcessInstanceAskState();
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
  void shouldVisitAllPendingAsks() {
    // given
    final var state = processingState.getMessageStartProcessInstanceAskState();
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
  void shouldVisitPendingAsksWithLastSentTime() {
    // given two pending asks; one already has a last-sent time recorded
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));
    state.updateLastSentTime(2L, 20L, 5_000L);

    // when
    final var lastSentByMessageKey = new java.util.HashMap<Long, Long>();
    state.forEachPendingAsk(
        (lastSentTime, ask) -> lastSentByMessageKey.put(ask.getMessageKey(), lastSentTime));

    // then both pending asks are visited with their transient last-sent time (0 until first send)
    assertThat(lastSentByMessageKey).containsOnlyKeys(1L, 2L);
    assertThat(lastSentByMessageKey.get(1L)).isZero();
    assertThat(lastSentByMessageKey.get(2L)).isEqualTo(5_000L);
  }

  @Test
  void shouldPopulateRecordFromAsk() {
    // given
    final var originalRecord = createRecord(123L, 456L, "test-business-id", "test-process");
    originalRecord.setMessageName("test-message");
    originalRecord.setCorrelationKey("test-correlation");
    originalRecord.setStartEventId("start-event");
    originalRecord.setMessageStartEventSubscriptionKey(789L);
    originalRecord.setTenantId("test-tenant");
    originalRecord.setMessageDeadline(99999L);
    originalRecord.setMessageTtl(88888L);

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
    assertThat(populatedRecord.getMessageTtl()).isEqualTo(88888L);
  }

  @Test
  void shouldDefaultRejectionCountToZeroForFreshAsk() {
    // given a fresh ask sourced from a request record (never rejected)
    final var ask = new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p"));

    // then the P_K-local retry bookkeeping defaults to zero, keeping the ask at the base re-send
    // cadence and ensuring values persisted before this field existed decode unchanged
    assertThat(ask.getRejectionCount()).isZero();
  }

  @Test
  void shouldPersistRejectionCount() {
    // given
    final var state = processingState.getMessageStartProcessInstanceAskState();
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
  void shouldPreserveRejectionCountOnCopy() {
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
  void shouldIncrementRejectionCountOnBackOff() {
    // given a pending ask with no rejections yet
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));

    // when backed off twice
    state.backOff(1L, 2L);
    state.backOff(1L, 2L);

    // then the persisted rejection count reflects both rejections
    assertThat(state.get(1L, 2L).getRejectionCount()).isEqualTo(2L);
  }

  @Test
  void shouldNotResetSendEligibilityOnBackOff() {
    // given a pending ask that has already been sent (transient last-sent advanced)
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));
    state.updateLastSentTime(1L, 2L, 10_000L);

    // when the ask is backed off
    state.backOff(1L, 2L);

    // then back-off does not reset the transient send-tracking: the ask is still considered sent at
    // 10_000 (resetting it would make the ask immediately eligible again and defeat the back-off)
    final var lastSentByMessageKey = new java.util.HashMap<Long, Long>();
    state.forEachPendingAsk(
        (lastSentTime, ask) -> lastSentByMessageKey.put(ask.getMessageKey(), lastSentTime));
    assertThat(lastSentByMessageKey).containsExactly(java.util.Map.entry(1L, 10_000L));
  }

  @Test
  void shouldBeNoOpWhenBackingOffMissingAsk() {
    // given no pending ask for the key
    final var state = processingState.getMessageStartProcessInstanceAskState();

    // when / then no exception and nothing is created
    state.backOff(7L, 8L);
    assertThat(state.get(7L, 8L)).isNull();
  }

  @Test
  void shouldSeedRecoveryEligibilityByRejectionCount() {
    // given a fresh ask (never rejected) and a backed-off ask
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));
    state.backOff(2L, 20L);

    // when the partition recovers at a known time
    final long recoveryTime = 50_000L;
    final var context = mock(ReadonlyStreamProcessorContext.class);
    final var clock = mock(StreamClock.class);
    when(context.getClock()).thenReturn(clock);
    when(clock.millis()).thenReturn(recoveryTime);
    ((DbMessageStartProcessInstanceAskState) state).onRecovered(context);

    // then the fresh ask is seeded immediately eligible (0, preserving at-least-once first
    // delivery); the backed-off ask is seeded at the recovery time so it waits its back-off before
    // re-probing instead of all blocked asks storming P_B at once
    final var lastSentByMessageKey = new java.util.HashMap<Long, Long>();
    state.forEachPendingAsk(
        (lastSentTime, ask) -> lastSentByMessageKey.put(ask.getMessageKey(), lastSentTime));
    assertThat(lastSentByMessageKey.get(1L)).isZero();
    assertThat(lastSentByMessageKey.get(2L)).isEqualTo(recoveryTime);
  }

  @Test
  void shouldCapRejectionCount() {
    // given a pending ask
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 2L, "b", "p")));

    // when backed off far more often than the cap (30)
    for (int i = 0; i < 100; i++) {
      state.backOff(1L, 2L);
    }

    // then the persisted count saturates at the cap, so it never overflows when the scheduler
    // computes 2^rejectionCount
    assertThat(state.get(1L, 2L).getRejectionCount()).isEqualTo(30L);
  }

  @Test
  void shouldTrackPendingAsksGaugeAcrossPutAndRemove() {
    // given
    final var state = processingState.getMessageStartProcessInstanceAskState();

    // when two asks are registered
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));

    // then the pending-asks gauge reflects both
    assertThat(pendingAsksGauge()).isEqualTo(2.0);

    // when one is removed
    state.remove(1L, 10L);

    // then the gauge drops to one
    assertThat(pendingAsksGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldNotDoubleCountPendingAsksGaugeWhenPuttingSameAskAgain() {
    // given a registered ask
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));

    // when the same (messageKey, processDefinitionKey) is put again (put upserts)
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));

    // then the gauge counts the ask only once
    assertThat(pendingAsksGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldNotDecrementPendingAsksGaugeWhenRemovingMissingAsk() {
    // given one registered ask
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));

    // when removing an ask that was never registered
    state.remove(7L, 70L);

    // then the gauge is unaffected
    assertThat(pendingAsksGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldDecrementPendingAsksGaugeForEachRemovedAskOnRemoveAll() {
    // given two asks for the same messageKey and one for another
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(7L, 100L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(7L, 200L, "b2", "p2")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(8L, 300L, "b3", "p3")));
    assertThat(pendingAsksGauge()).isEqualTo(3.0);

    // when all asks for messageKey 7 are removed
    state.removeAllByMessageKey(7L);

    // then the gauge drops by exactly the two removed asks
    assertThat(pendingAsksGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldReseedPendingAsksGaugeFromStateOnRecovery() {
    // given two persisted asks
    final var state = processingState.getMessageStartProcessInstanceAskState();
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(1L, 10L, "b1", "p1")));
    state.put(new MessageStartProcessInstanceAsk().wrap(createRecord(2L, 20L, "b2", "p2")));

    // when the partition recovers
    final var context = mock(ReadonlyStreamProcessorContext.class);
    final var clock = mock(StreamClock.class);
    when(context.getClock()).thenReturn(clock);
    when(clock.millis()).thenReturn(50_000L);
    ((DbMessageStartProcessInstanceAskState) state).onRecovered(context);

    // then the gauge is authoritatively seeded from the persisted count
    assertThat(pendingAsksGauge()).isEqualTo(2.0);
  }

  private double pendingAsksGauge() {
    return zeebeDb.getMeterRegistry().get(PENDING_ASKS_GAUGE).gauge().value();
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
