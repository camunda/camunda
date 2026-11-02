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
import io.camunda.zeebe.util.TagUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;

/**
 * Cross-partition acceptance tests for run-scoped message correlation (ADR 0014). Mirrors {@link
 * MessageCorrelationBusinessIdMultiplePartitionsTest}: the run scope is captured from the
 * subscribing instance's reserved run-scope tag at open time, shipped with the OPEN command to the
 * message partition, and applied there as a local filter. The process instance deliberately lives
 * on a partition different from its subscription so the cross-partition carry is exercised.
 */
public final class MessageCorrelationRunScopeMultiplePartitionsTest {

  private static final int PARTITION_COUNT = 3;
  private static final String PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final String RUN_A = "runA";
  private static final String RUN_B = "runB";

  private static final BpmnModelInstance INTERMEDIATE_CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Test
  public void shouldCorrelateMessageWithMatchingRunScopeAcrossPartitions() {
    // given a run-scoped instance whose subscription routes to a different partition than the PI
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final String correlationKey = "order-scope-1";
    final long processInstanceKey = createScopedInstance(correlationKey, RUN_A);
    final var subscription = awaitSubscriptionCreated(processInstanceKey);

    // sanity: the run scope was carried onto the subscription on the message partition
    assertThat(subscription.getPartitionId())
        .isEqualTo(partitionFor(correlationKey, PARTITION_COUNT));
    assertThat(subscription.getValue().getRunScope()).isEqualTo(RUN_A);
    assertCrossPartitionRouting(processInstanceKey, subscription.getPartitionId());

    // when a message carrying the same run scope is published
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(correlationKey)
        .withRunScope(RUN_A)
        .publish();

    // then it correlates and the instance completes
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .isNotNull();
  }

  @Test
  public void shouldNotCorrelateMessageWithMismatchedRunScopeAcrossPartitions() {
    // given a run-A instance
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final String correlationKey = "order-scope-10";
    final long processInstanceKey = createScopedInstance(correlationKey, RUN_A);
    final var subscription = awaitSubscriptionCreated(processInstanceKey);
    assertCrossPartitionRouting(processInstanceKey, subscription.getPartitionId());

    // when a message scoped to a different run is published with TTL 0
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(correlationKey)
        .withRunScope(RUN_B)
        .withTimeToLive(0L)
        .publish();

    // then the message expires without ever correlating to the run-A instance
    assertNoCorrelationUpToMessageExpiry(processInstanceKey, MESSAGE_NAME, correlationKey);
  }

  @Test
  public void shouldCorrelateUnscopedMessageToScopedInstanceAcrossPartitions() {
    // given a run-A instance
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final String correlationKey = "order-scope-3";
    final long processInstanceKey = createScopedInstance(correlationKey, RUN_A);
    final var subscription = awaitSubscriptionCreated(processInstanceKey);
    assertCrossPartitionRouting(processInstanceKey, subscription.getPartitionId());

    // when a message with no run scope is published (unset selector matches any subscription)
    engine.message().withName(MESSAGE_NAME).withCorrelationKey(correlationKey).publish();

    // then it correlates and the instance completes, exactly as today
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .isNotNull();
  }

  private long createScopedInstance(final String correlationKey, final String runScope) {
    return engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withTags(TagUtil.runScopeTag(runScope))
        .withVariable("key", correlationKey)
        .create();
  }

  private Record<MessageSubscriptionRecordValue> awaitSubscriptionCreated(
      final long processInstanceKey) {
    return RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

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

  private static void assertCrossPartitionRouting(
      final long processInstanceKey, final int subscriptionPartitionId) {
    final int piPartitionId = Protocol.decodePartitionId(processInstanceKey);
    assertThat(piPartitionId)
        .as(
            "PI partition (%d) must differ from subscription partition (%d) so the test exercises"
                + " cross-partition correlation; otherwise pick a correlationKey whose hash differs",
            piPartitionId, subscriptionPartitionId)
        .isNotEqualTo(subscriptionPartitionId);
  }
}
