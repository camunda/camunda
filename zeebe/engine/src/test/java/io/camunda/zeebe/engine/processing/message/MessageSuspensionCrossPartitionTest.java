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
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Suspension removes the message-side {@code MessageSubscription} and re-opens it on resume. Both
 * the removal and the re-open are driven by inter-partition commands from the suspend/resume
 * processors on the process instance's partition to the subscription's partition. A single
 * partition collapses that hop into a local write, so it never exercises the cross-partition path.
 * These tests force the subscription onto a different partition than the instance to cover it.
 */
public final class MessageSuspensionCrossPartitionTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_PARTITION = 1;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldReopenCrossPartitionSubscriptionAndPickUpBufferedMessageOnResume() {
    // given - an instance on one partition with its message subscription on another, then suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStart(processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    final Record<MessageSubscriptionRecordValue> created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    // the subscription genuinely lives on a different partition than the instance
    assertThat(created.getPartitionId()).isEqualTo(subscriptionPartition);
    assertThat(subscriptionPartition).isNotEqualTo(PROCESS_INSTANCE_PARTITION);

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // suspend must reach across partitions to delete the message-side subscription
    final Record<MessageSubscriptionRecordValue> deleted =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(deleted.getPartitionId()).isEqualTo(subscriptionPartition);

    // publish while suspended: no subscriber on the remote partition → message buffers with a TTL
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    // when - resume; the resume processor re-opens the subscription across partitions and the
    // subscription-create handler picks up the buffered message immediately
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the subscription is re-created on the remote partition and correlates the buffer,
    // completing the instance
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getPartitionId)
        .containsExactly(subscriptionPartition, subscriptionPartition);
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldReturnNotFoundForCrossPartitionDirectCorrelateWhileSuspended() {
    // given - an instance on one partition with its message subscription on another, then
    // suspended.
    // Regression for #60648: a direct/synchronous correlate to a suspended instance whose
    // subscription lives on a remote partition must get a prompt terminal rejection instead of
    // hanging until the gateway deadline.
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStart(processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    final Record<MessageSubscriptionRecordValue> created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    // the subscription genuinely lives on a different partition than the instance
    assertThat(created.getPartitionId()).isEqualTo(subscriptionPartition);
    assertThat(subscriptionPartition).isNotEqualTo(PROCESS_INSTANCE_PARTITION);

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // suspend deletes the message-side subscription on the remote partition
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - a direct correlate routes to the subscription partition (by correlation key), where
    // the
    // subscription no longer exists
    final Record<MessageCorrelationRecordValue> rejection =
        engine
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - prompt NOT_FOUND, not a hung request: the suspended instance has no message-side
    // subscription, so the existing not-found path fires on the subscription partition
    assertThat(rejection.getPartitionId()).isEqualTo(subscriptionPartition);
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  private long deployAndStart(
      final String processId,
      final String messageName,
      final String correlationKey,
      final int partitionId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(processId).onPartition(partitionId).create();
  }

  /** Generates a correlation key whose subscription partition differs from the given one. */
  private String correlationKeyForPartitionOtherThan(final int partitionId) {
    for (int i = 0; ; i++) {
      final String candidate = "key-" + i;
      if (getSubscriptionPartitionId(candidate) != partitionId) {
        return candidate;
      }
    }
  }

  private int getSubscriptionPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = engine.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionIds.size());
  }
}
