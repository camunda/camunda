/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;

/**
 * Cross-partition variant of {@link MessageCorrelationBusinessIdTest}: ensures the asymmetric
 * business-id filter behaves identically when the process instance lives on a partition different
 * from the message partition. The PI partition ships the business id with the OPEN command and the
 * message partition applies the filter against the locally stored value — the design's central
 * anti-chatter rule.
 */
public final class MessageCorrelationBusinessIdMultiplePartitionsTest {

  private static final int PARTITION_COUNT = 3;
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance INTERMEDIATE_CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Test
  public void shouldCorrelateMessageWithBusinessIdAcrossPartitions() {
    // given a process instance whose correlation key routes its subscription to a specific
    // partition; the PI itself may live on a different partition
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final String correlationKey = "order-x-1";
    final String businessId = "biz-42";
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId(businessId)
            .withVariable("key", correlationKey)
            .create();
    final var subscription = awaitSubscriptionCreated(processInstanceKey);

    // sanity: the subscription was opened on the message partition (P_K), and that partition has
    // the business id stored locally — this is what makes the filter on-partition.
    assertThat(subscription.getPartitionId())
        .isEqualTo(partitionFor(correlationKey, PARTITION_COUNT));
    assertThat(subscription.getValue().getBusinessId()).isEqualTo(businessId);
    // and the PI lives on a different partition than its subscription — otherwise this test would
    // silently degenerate into a single-partition scenario and would not actually exercise the
    // cross-partition rule it claims to.
    assertCrossPartitionRouting(processInstanceKey, subscription.getPartitionId());

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey(correlationKey)
        .withBusinessId(businessId)
        .publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .isNotNull();
  }

  @Test
  public void shouldNotCorrelateMessageWithMismatchedBusinessIdAcrossPartitions() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final String correlationKey = "order-x-2";
    final String businessId = "biz-42";
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId(businessId)
            .withVariable("key", correlationKey)
            .create();
    final var subscription = awaitSubscriptionCreated(processInstanceKey);
    // sanity: same cross-partition guarantee as the positive case.
    assertCrossPartitionRouting(processInstanceKey, subscription.getPartitionId());

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey(correlationKey)
        .withBusinessId("other-biz")
        .withTimeToLive(0L)
        .publish();

    // then the message lifecycle completes (EXPIRED on the message partition) without ever
    // correlating to the PI on the other partition.
    assertNoCorrelationUpToMessageExpiry(processInstanceKey, "message", correlationKey);
  }

  private static Record<MessageSubscriptionRecordValue> awaitSubscriptionCreated(
      final long processInstanceKey) {
    return RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  /**
   * Bound the record stream on the EXPIRED of the specific message identified by {@code
   * messageName} + {@code correlationKey} (a deterministic terminal the caller published, typically
   * with TTL=0) and assert that no CORRELATING/CORRELATED records for {@code processInstanceKey}
   * appeared up to that point.
   */
  private static void assertNoCorrelationUpToMessageExpiry(
      final long processInstanceKey, final String messageName, final String correlationKey) {
    final Predicate<Record<?>> messageExpired =
        r ->
            r.getIntent() == MessageIntent.EXPIRED
                && r.getValue() instanceof final MessageRecordValue v
                && messageName.equals(v.getName())
                && correlationKey.equals(v.getCorrelationKey());
    final boolean correlated =
        RecordingExporter.records()
            .limit(messageExpired::test)
            .messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(MessageSubscriptionIntent.CORRELATED)
            .exists();
    assertThat(correlated).isFalse();
    final boolean correlating =
        RecordingExporter.records()
            .limit(messageExpired::test)
            .messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(MessageSubscriptionIntent.CORRELATING)
            .exists();
    assertThat(correlating).isFalse();
  }

  private static int partitionFor(final String correlationKey, final int count) {
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), count);
  }

  /**
   * Assert that the PI's partition (derived from {@code processInstanceKey}) differs from the
   * subscription partition, so the test actually exercises the cross-partition path. If the chosen
   * {@code correlationKey} / {@code businessId} happen to hash to the same partition, this fails
   * loudly instead of silently degenerating into a single-partition scenario.
   */
  private static void assertCrossPartitionRouting(
      final long processInstanceKey, final int subscriptionPartitionId) {
    final int piPartitionId = Protocol.decodePartitionId(processInstanceKey);
    assertThat(piPartitionId)
        .as(
            "PI partition (%d) must differ from subscription partition (%d) so the test exercises"
                + " cross-partition correlation; otherwise pick correlationKey/businessId values"
                + " whose hashes differ",
            piPartitionId, subscriptionPartitionId)
        .isNotEqualTo(subscriptionPartitionId);
  }
}
