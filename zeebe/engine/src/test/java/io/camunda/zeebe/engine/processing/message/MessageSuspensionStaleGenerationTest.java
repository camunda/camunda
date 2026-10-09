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
 * Reproduction test for <a href="https://github.com/camunda/camunda/issues/61099">#61099</a>: a
 * stale-generation correlate command that arrives after a suspend/resume cycle should release the
 * message-partition correlation lock instead of only writing a local rejection. This race is
 * inherently cross-partition (P1↔P2) and needs the full {@link EngineRule} to reproduce.
 */
public final class MessageSuspensionStaleGenerationTest {

  private static final int PARTITION_COUNT = 2;
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
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStart(processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    final Record<MessageSubscriptionRecordValue> k1Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(k1Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k1Key = k1Created.getKey();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    final var capturedCorrelate = captureFirstCorrelate();

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

    suspendAndResume(processInstanceKey, k1Key, subscriptionPartition);

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

    // when — release the captured, now-stale CORRELATE(K1)
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

    // then
    final var rejection =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .withProcessInstanceKey(processInstanceKey)
            .onlyCommandRejections()
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("superseded subscription generation");

    // K2 must still be able to correlate a new message
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertThat(rejectSentToMessagePartition.get())
        .as("stale-generation CORRELATE must send a REJECT back to release the correlation lock")
        .isTrue();
  }

  /**
   * Intercepts and drops the first CORRELATE sent to the PI partition, so a suspend/resume cycle
   * can race ahead of it instead of it reaching the PI partition immediately.
   */
  private AtomicReference<ProcessMessageSubscriptionRecord> captureFirstCorrelate() {
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
            return false;
          }
          return true;
        });
    return capturedCorrelate;
  }

  /**
   * Suspends and resumes the instance, waiting for the resulting new subscription generation (K2)
   * on both the message and process-instance sides. Returns K2's message-partition key.
   */
  private long suspendAndResume(
      final long processInstanceKey, final long k1Key, final int subscriptionPartition) {
    // suspend deletes K1 without releasing the correlation lock it holds
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    final Record<MessageSubscriptionRecordValue> k2Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();
    assertThat(k2Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k2Key = k2Created.getKey();
    assertThat(k2Key).isNotEqualTo(k1Key);

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(1)
        .await();
    return k2Key;
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
