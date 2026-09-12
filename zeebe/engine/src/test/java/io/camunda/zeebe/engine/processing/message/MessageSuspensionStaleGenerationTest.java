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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

/**
 * Reproduction tests for <a href="https://github.com/camunda/camunda/issues/61099">#61099</a>:
 * stale-generation correlate/reject commands that arrive after a suspend/resume cycle should
 * release the message-partition correlation lock instead of only writing a local rejection.
 */
public final class MessageSuspensionStaleGenerationTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_PARTITION = 1;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  /**
   * Verifies that a stale CORRELATE(K1) arriving on the PI partition after a suspend/resume cycle
   * is rejected AND a REJECT command is sent back to the message partition to release the
   * correlation lock.
   *
   * <p>Sequence:
   *
   * <ol>
   *   <li>Instance starts on P1, subscription K1 created on P2
   *   <li>A message correlates to K1 on P2 — sets CORRELATING + correlation lock — sends
   *       CORRELATE(K1) to P1
   *   <li>The CORRELATE is intercepted/dropped (simulates delay)
   *   <li>Suspend deletes K1 on P2 (the DELETED applier does NOT release the lock)
   *   <li>Resume creates K2 on P2
   *   <li>The stale CORRELATE(K1) arrives on P1 — the stale-generation guard rejects it and sends a
   *       REJECT back to P2 so the correlation lock is released
   * </ol>
   */
  @Test
  public void shouldSendRejectForStaleGenerationCorrelate() {
    // given — instance on P1 with subscription K1 on a different partition
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStart(processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    // wait for K1 subscription to be fully established on message partition
    final Record<MessageSubscriptionRecordValue> k1Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(k1Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k1Key = k1Created.getKey();

    // and K1 subscription acknowledged on PI partition (so PI side has subscriptionKey = K1)
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // intercept and capture the CORRELATE command (message partition → PI partition)
    final var capturedCorrelate = new AtomicReference<ProcessMessageSubscriptionRecord>();
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            if (capturedCorrelate.get() == null) {
              final var copy = new ProcessMessageSubscriptionRecord();
              copy.wrap((ProcessMessageSubscriptionRecord) command);
              capturedCorrelate.set(copy);
            }
            return false; // drop all CORRELATE commands to PI partition
          }
          return true;
        });

    // publish a message that matches K1 — sets CORRELATING + correlation lock on P2
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // suspend — deletes K1 on message partition (but does NOT release correlation lock)
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // resume — creates K2 on message partition
    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    final Record<MessageSubscriptionRecordValue> k2Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();
    assertThat(k2Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k2Key = k2Created.getKey();
    assertThat(k2Key).isNotEqualTo(k1Key);

    // wait for K2 to be acknowledged on PI partition
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(1)
        .await();

    // install a recording interceptor: allow all, but track if REJECT is sent
    final var rejectSentToMessagePartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == subscriptionPartition
              && valueType == ValueType.MESSAGE_SUBSCRIPTION
              && intent == MessageSubscriptionIntent.REJECT) {
            rejectSentToMessagePartition.set(true);
          }
          return true;
        });

    // when — write the captured stale CORRELATE(K1) to the PI partition
    assertThat(capturedCorrelate.get())
        .as("interceptor should have captured a CORRELATE command")
        .isNotNull();
    assertThat(capturedCorrelate.get().getSubscriptionKey())
        .as("captured CORRELATE carries K1's subscription key")
        .isEqualTo(k1Key);

    engine.writeCommandOnPartition(
        PROCESS_INSTANCE_PARTITION,
        -1L,
        ProcessMessageSubscriptionIntent.CORRELATE,
        capturedCorrelate.get());

    // then — the stale CORRELATE is rejected on P1 (stale generation guard fires)
    final var rejection =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .withProcessInstanceKey(processInstanceKey)
            .onlyCommandRejections()
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("superseded subscription generation");

    // verify K2 is still functional: publish a second message that correlates to K2
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    // the process should complete via K2
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // fixed: REJECT is now sent back to the message partition for the stale CORRELATE,
    // so the correlation lock can be released
    assertThat(rejectSentToMessagePartition.get())
        .as("stale-generation CORRELATE must send a REJECT back to release the correlation lock")
        .isTrue();
  }

  /**
   * Verifies that a stale REJECT(K1) arriving at the message partition after a suspend/resume cycle
   * writes a REJECTED event (releasing the correlation lock via the applier) without deleting the
   * live replacement subscription K2.
   */
  @Test
  public void shouldReleaseCorrelationLockForStaleGenerationRejectWithoutDeletingReplacement() {
    // given — instance on P1 with subscription K1 on a different partition
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStart(processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    // wait for K1 subscription fully established
    final Record<MessageSubscriptionRecordValue> k1Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(k1Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k1Key = k1Created.getKey();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // intercept CORRELATE to PI partition — prevents process from completing
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

    // publish message → K1 CORRELATING + correlation lock on message partition
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    final var correlating =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long messageKey = correlating.getValue().getMessageKey();

    // suspend → K1 DELETED (correlation lock stays), resume → K2 CREATED
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // allow inter-partition commands again before resume
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> true);

    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    final Record<MessageSubscriptionRecordValue> k2Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();
    final long k2Key = k2Created.getKey();
    assertThat(k2Key).isNotEqualTo(k1Key);

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(1)
        .await();

    // when — inject a stale REJECT(K1) on the message partition,
    // simulating what the PI partition would send if the PI-side bug were fixed
    final var rejectRecord =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(correlating.getValue().getElementInstanceKey())
            .setProcessDefinitionKey(correlating.getValue().getProcessDefinitionKey())
            .setBpmnProcessId(BufferUtil.wrapString(correlating.getValue().getBpmnProcessId()))
            .setMessageName(BufferUtil.wrapString(correlating.getValue().getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(correlating.getValue().getCorrelationKey()))
            .setMessageKey(messageKey)
            .setInterrupting(false)
            .setTenantId(correlating.getValue().getTenantId())
            .setSubscriptionKey(k1Key);

    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, rejectRecord);

    // then — REJECTED event is written (applier releases correlation lock)
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECTED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // verify K2 still works: publish a second message that correlates to K2
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    // K2 subscription must still exist — the applier did not delete the replacement
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
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
