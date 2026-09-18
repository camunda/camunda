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

    // capture the CORRELATE instead of letting it reach the PI partition, simulating the delay
    // that lets the upcoming suspend/resume cycle race ahead of it
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
   * Verifies that a stale REJECT(K1) arriving at the message partition after a suspend/resume cycle
   * writes a REJECTED event (releasing the correlation lock via the applier) without deleting the
   * live replacement subscription K2.
   */
  @Test
  public void shouldReleaseCorrelationLockForStaleGenerationRejectWithoutDeletingReplacement() {
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

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on this message
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

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

    // suspend deletes K1 without releasing the correlation lock it holds
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

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

    // when — inject a stale REJECT(K1) directly, simulating what the PI partition would send
    // once its own stale-CORRELATE handling is fixed
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

    // then
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECTED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // K2 must still exist and be able to correlate a new message
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
  }

  /**
   * Verifies that a stale REJECT(K1) also reroutes the message that was buffered against K1 to the
   * live replacement subscription K2, so the process completes using that same message without it
   * ever being republished.
   */
  @Test
  public void shouldDeliverOriginalMessageToReplacementAfterStaleGenerationReject() {
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

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on this message
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

    // the message that gets stuck against K1: published once, never republished later
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
    final long elementInstanceKey = correlating.getValue().getElementInstanceKey();

    // suspend deletes K1 without releasing the correlation lock it holds
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

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

    // when — inject the stale REJECT(K1) directly, releasing the lock it holds on `messageKey`
    final var rejectRecord =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
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

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECTED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then — completes via K2 correlating the same message, without publishing a second one
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  /**
   * Reproduces the race raised in review of #61099: {@code findSubscriptionToCorrelate} matches
   * candidates by {@code !isCorrelating} only, with no check against the message the candidate
   * already correlated. A duplicate/delayed stale REJECT(K1) for a message that the replacement K2
   * already correlated therefore finds K2 eligible again (it's back to not-correlating) and
   * re-drives it into CORRELATING for the very same message.
   *
   * <p>Uses a non-interrupting boundary event so K2 survives past its own CORRELATED (an
   * interrupting/single-shot subscription would be deleted, sidestepping the race).
   *
   * <p>Sequence:
   *
   * <ol>
   *   <li>K1 (boundary subscription) locks a message, then suspend/resume replaces it with K2
   *   <li>A first stale REJECT(K1) reroutes the buffered message to K2, which correlates it
   *   <li>A second, duplicate stale REJECT(K1) for the same message arrives later
   *   <li>A third REJECT is written purely as an ordering barrier — per-partition command
   *       processing is sequential, so once its REJECTED event exists, any follow-up the second
   *       REJECT produced is already visible
   * </ol>
   */
  @Test
  public void shouldNotReCorrelateReplacementForDuplicateStaleGenerationRejectOfSameMessage() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    final long processInstanceKey =
        deployAndStartWithNonInterruptingBoundaryEvent(
            processId, messageName, correlationKey, PROCESS_INSTANCE_PARTITION);

    final Record<MessageSubscriptionRecordValue> k1Created =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(k1Created.getPartitionId()).isEqualTo(subscriptionPartition);
    final long k1Key = k1Created.getKey();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on this message
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

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
    final long elementInstanceKey = correlating.getValue().getElementInstanceKey();

    // suspend deletes K1 without releasing the correlation lock it holds
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

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

    final var staleReject =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(correlating.getValue().getProcessDefinitionKey())
            .setBpmnProcessId(BufferUtil.wrapString(correlating.getValue().getBpmnProcessId()))
            .setMessageName(BufferUtil.wrapString(correlating.getValue().getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(correlating.getValue().getCorrelationKey()))
            .setMessageKey(messageKey)
            .setInterrupting(false)
            .setTenantId(correlating.getValue().getTenantId())
            .setSubscriptionKey(k1Key);

    // when — release the stale lock so K2 correlates the buffered message for the first time
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleReject);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(k2Key)
        .await();

    // a second, duplicate/delayed stale REJECT(K1) for the SAME message arrives later
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleReject);

    // ordering barrier (see javadoc)
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleReject);

    final List<Record<MessageSubscriptionRecordValue>> recordsUpToBarrier =
        RecordingExporter.messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitByCount(r -> r.getIntent() == MessageSubscriptionIntent.REJECTED, 3)
            .asList();

    // then — the duplicate stale REJECT must not re-drive K2 into CORRELATING for a message it
    // already correlated
    final long correlatingEventsForK2 =
        recordsUpToBarrier.stream()
            .filter(r -> r.getIntent() == MessageSubscriptionIntent.CORRELATING)
            .filter(r -> r.getKey() == k2Key)
            .count();
    assertThat(correlatingEventsForK2)
        .as("duplicate stale REJECT must not re-correlate a message K2 already handled")
        .isEqualTo(1);
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

  /**
   * Non-interrupting boundary events keep their message subscription open (not deleted) after
   * correlating, unlike the single-shot subscription {@link #deployAndStart} produces.
   */
  private long deployAndStartWithNonInterruptingBoundaryEvent(
      final String processId,
      final String messageName,
      final String correlationKey,
      final int partitionId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType("task"))
                .boundaryEvent(
                    "msg",
                    b ->
                        b.cancelActivity(false)
                            .message(
                                m ->
                                    m.name(messageName)
                                        .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent("msgEnd")
                .moveToActivity("task")
                .endEvent("taskEnd")
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
