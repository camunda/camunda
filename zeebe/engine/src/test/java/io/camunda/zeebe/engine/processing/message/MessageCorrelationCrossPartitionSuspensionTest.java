/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Regression coverage for https://github.com/camunda/camunda/issues/60648: a direct {@code
 * MessageCorrelationIntent#CORRELATE} command (backing {@code POST /v2/messages/correlation}) whose
 * only matching subscription belongs to a suspended process instance must resolve promptly, not
 * hang until the gateway-to-broker deadline.
 *
 * <p>{@link MessageSuspensionGateTest} already covers this for the case where the message's
 * partition ({@code P_K = hash(correlationKey)}) is the same partition that owns the target process
 * instance: there, {@link MessageCorrelationCorrelateProcessor}'s own suspension check sees the
 * suspension directly and rejects before any state write. This class covers the case that check
 * cannot see: the target instance lives on a different partition than the message. In that case,
 * {@code SuspensionState} is only known locally to the instance's own partition, so the up-front
 * check on {@code P_K} cannot detect the suspension and lets the command proceed to a real
 * cross-partition correlate attempt, which is deferred (not rejected) once it reaches the suspended
 * instance. The deferral must still resolve the pending request instead of leaving it unanswered -
 * see {@link MessageSubscriptionDeferCorrelationProcessor}.
 */
public final class MessageCorrelationCrossPartitionSuspensionTest {

  private static final int PARTITION_COUNT = 3;

  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectPromptlyWhenSuspendedTargetIsOnAnotherPartitionThanTheMessage() {
    // given - a receive-task instance forced onto P1, subscribed to a message whose correlation
    // key hashes to a different partition (P_K != P1), then suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyHashingToAnotherPartitionThan(START_PARTITION_ID);

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .receiveTask(
                    "receive",
                    t -> t.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .onPartition(START_PARTITION_ID)
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the message is correlated directly; it auto-routes to P_K, which cannot see the
    // suspension of an instance owned by a different partition
    final Record<MessageCorrelationRecordValue> notCorrelated =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectNotCorrelated()
            .correlate();

    // then - the request resolves promptly as not-correlated instead of hanging until the
    // instance resumes; the deferral on the instance's own partition is what triggered it
    Assertions.assertThat(notCorrelated.getValue()).hasCorrelationKey(correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  @Test
  public void shouldResolvePromptlyEvenWhenASiblingRedirectIsAttemptedAcrossPartitions() {
    // given - two instances of the same process share a correlation key that hashes to a
    // different partition than either instance; one instance is suspended, the other is not, so
    // deferring the suspended one's correlation makes MessageSubscriptionDeferCorrelationProcessor
    // attempt exactly one redirect to the active sibling's subscription
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyHashingToAnotherPartitionThan(START_PARTITION_ID);

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .receiveTask(
                    "receive",
                    t -> t.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();

    final long suspendedInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .onPartition(START_PARTITION_ID)
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedInstanceKey)
        .await();

    final long activeInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .onPartition(START_PARTITION_ID)
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(activeInstanceKey)
        .await();

    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).suspend();

    // when - a direct correlate command carries no TTL, so MessageCorrelationCorrelateProcessor
    // expires its published message as soon as it dispatches the (deduplicated, single-per-process)
    // correlate command; by the time the deferral runs on the instance partition and redirects back
    // to the sibling, there is no longer a stored message left for the sibling to pick up either
    final Record<MessageCorrelationRecordValue> notCorrelated =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectNotCorrelated()
            .correlate();

    // then - the request still resolves promptly as not-correlated instead of hanging, and neither
    // instance ever correlates the message
    Assertions.assertThat(notCorrelated.getValue()).hasCorrelationKey(correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
        .withProcessInstanceKey(suspendedInstanceKey)
        .await();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processMessageSubscriptionRecords()
                        .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                        .withMessageName(messageName)
                        .exists()))
        .isFalse();
  }

  /**
   * Finds a correlation key whose subscription partition (per {@link
   * SubscriptionUtil#getSubscriptionPartitionId}) differs from the given partition, so a process
   * instance created on that partition ends up with a message subscription on another one.
   */
  private static String correlationKeyHashingToAnotherPartitionThan(final int partitionId) {
    for (int i = 0; i < 1000; i++) {
      final var candidate = Strings.newRandomValidBpmnId();
      final var candidatePartition =
          SubscriptionUtil.getSubscriptionPartitionId(
              BufferUtil.wrapString(candidate), PARTITION_COUNT);
      if (candidatePartition != partitionId) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "Could not find a correlation key hashing to a different partition than "
            + partitionId
            + " after 1000 attempts");
  }
}
