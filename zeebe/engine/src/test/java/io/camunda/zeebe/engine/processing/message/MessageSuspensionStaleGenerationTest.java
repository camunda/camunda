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

  /**
   * Reproduces a second review comment on #61099: {@code MessageSubscriptionRejectedV2Applier}
   * calls {@code removeMessageCorrelation} unconditionally, with no check for whether a newer
   * generation (K2) has since re-claimed the very message this reject concerns. A duplicate/delayed
   * stale REJECT(K1) for a message K2 already correlated therefore clears the "message already
   * correlated to this process" lock that K2's completed correlation relies on, even though nothing
   * re-acquires it afterwards — leaving the message eligible to be selected again for the same
   * process.
   *
   * <p>Duplicate stale REJECT(K1) commands for the same message are not merely theoretical: {@code
   * PendingMessageSubscriptionCheckScheduler} resends an un-acked CORRELATE(K1) every 30s while
   * it's pending, and {@code ProcessMessageSubscriptionCorrelateProcessor#isStaleGeneration} sends
   * a REJECT for every copy of a stale CORRELATE it sees, with no deduplication.
   */
  @Test
  public void shouldNotReleaseCorrelationLockForDuplicateStaleGenerationRejectOfSameMessage() {
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

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECTED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .asList();

    // then — a second instance of the SAME process (same bpmnProcessId), matching the same
    // message name + correlation key, must not be able to pick up the message: if the duplicate
    // reject wrongly cleared the lock, this bystander's own CREATE-time scan would grab it
    final long bystanderProcessInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(subscriptionPartition)
            .create();
    final long bystanderSubscriptionKey =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(bystanderProcessInstanceKey)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.messageSubscriptionRecords(
                            MessageSubscriptionIntent.CORRELATING)
                        .withRecordKey(bystanderSubscriptionKey)
                        .exists()))
        .as(
            "duplicate stale REJECT must not clear the lock K2's completed correlation relies"
                + " on, letting a bystander subscription of the same process correlate the same"
                + " message")
        .isFalse();
  }

  /**
   * Reproduces a third review comment on #61099: both {@code
   * MessageSubscriptionRejectProcessor#hasAlreadyBeenCorrelated} and {@code
   * MessageSubscriptionRejectedV2Applier#newerGenerationAlreadyCorrelatedMessage} inferred "K2
   * already correlated this message" from key ordering alone (messageKey <= / >= K2's last
   * correlated key). That assumption breaks when K2's own CREATE-time scan skips a message that's
   * still locked by K1's stale claim and correlates a *later* one instead: K2 never touched the
   * locked message, but its last-correlated key is still numerically ahead of it.
   *
   * <p>Sequence: M1 is published and locks K1. Suspend/resume replaces K1 with K2 without releasing
   * M1's lock. M2 is published while M1 is still locked. K2's own CREATE-time scan finds M1 locked,
   * skips it, and correlates M2 instead — so K2's last-correlated key (M2) is numerically past M1,
   * even though K2 never saw M1. The stale REJECT(K1, M1) then arrives: with the key-ordering
   * check, {@code M1 <= M2} wrongly reads as "K2 already has it", so the reject neither reroutes M1
   * to K2 nor releases M1's lock — M1 is silently lost until its TTL expires, the exact symptom
   * #61099 was filed for, reappearing through this side door.
   */
  @Test
  public void shouldRerouteLockedMessageAfterReplacementSkippedAheadToLaterMessage() {
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

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on M1
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

    // M1: locks K1 and stays locked (K1 never acks it, since the CORRELATE is dropped)
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    final var correlatingM1 =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long m1Key = correlatingM1.getValue().getMessageKey();
    final long elementInstanceKey = correlatingM1.getValue().getElementInstanceKey();
    final var bpmnProcessIdBuffer =
        BufferUtil.wrapString(correlatingM1.getValue().getBpmnProcessId());

    // M2: published while M1 is still locked, so it's buffered and waiting for the next scan
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // suspend deletes K1 without releasing the lock M1 holds
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

    // sanity: K2's own CREATE-time scan must skip locked M1 and correlate M2 instead — this is
    // the exact setup the review comment describes, not just an assumption
    final var correlatedM2 =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
            .withProcessInstanceKey(processInstanceKey)
            .withRecordKey(k2Key)
            .getFirst();
    assertThat(correlatedM2.getValue().getMessageKey())
        .as("K2's CREATE-time scan should skip the still-locked M1 and correlate M2 instead")
        .isNotEqualTo(m1Key);

    final var staleRejectForM1 =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(correlatingM1.getValue().getProcessDefinitionKey())
            .setBpmnProcessId(BufferUtil.wrapString(correlatingM1.getValue().getBpmnProcessId()))
            .setMessageName(BufferUtil.wrapString(correlatingM1.getValue().getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(correlatingM1.getValue().getCorrelationKey()))
            .setMessageKey(m1Key)
            .setInterrupting(false)
            .setTenantId(correlatingM1.getValue().getTenantId())
            .setSubscriptionKey(k1Key);

    // when — the stale REJECT for M1 (the message K2 skipped, not the one it just correlated)
    // arrives
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleRejectForM1);

    // then — M1 must still reach K2, even though K2's last-correlated key (M2) is numerically
    // past it
    final var correlatedM1 =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
            .withProcessInstanceKey(processInstanceKey)
            .withRecordKey(k2Key)
            .skip(1)
            .getFirst();
    assertThat(correlatedM1.getValue().getMessageKey())
        .as("the skipped-over M1 must be rerouted to K2, not lost")
        .isEqualTo(m1Key);
  }

  /**
   * Reproduces the residual case behind two more review comments on #61099: the guard that decides
   * whether a stale REJECT may release the correlation lock compared this reject's message key
   * against the replacement's *current* subscription snapshot only. Once the replacement (K2)
   * correlates a second, unrelated message (M2), its snapshot no longer reflects the first message
   * (M1) it legitimately correlated earlier — so a very late duplicate REJECT(K1, M1) reads as "K2
   * never touched M1" and releases M1's lock anyway, even though K2's own completed correlation of
   * M1 still relies on it.
   *
   * <p>Sequence: M1 locks K1, suspend/resume replaces it with K2, the first stale REJECT(K1, M1)
   * reroutes M1 to K2 which correlates it. K2's non-interrupting boundary event re-arms, and a
   * fresh M2 correlates to K2 directly. Only then does a very late duplicate REJECT(K1, M1) arrive.
   */
  @Test
  public void shouldNotReleaseLockAfterReplacementMovedOnToLaterMessage() {
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
    final long k1Key = k1Created.getKey();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on M1
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

    // M1: locks K1 and stays locked (K1 never acks it, since the CORRELATE is dropped)
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    final var correlatingM1 =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long m1Key = correlatingM1.getValue().getMessageKey();
    final long elementInstanceKey = correlatingM1.getValue().getElementInstanceKey();

    // suspend deletes K1 without releasing the lock M1 holds
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

    final var staleRejectForM1 =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(correlatingM1.getValue().getProcessDefinitionKey())
            .setBpmnProcessId(BufferUtil.wrapString(correlatingM1.getValue().getBpmnProcessId()))
            .setMessageName(BufferUtil.wrapString(correlatingM1.getValue().getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(correlatingM1.getValue().getCorrelationKey()))
            .setMessageKey(m1Key)
            .setInterrupting(false)
            .setTenantId(correlatingM1.getValue().getTenantId())
            .setSubscriptionKey(k1Key);

    // when — the first stale REJECT(K1, M1) reroutes M1 to K2, which correlates it
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleRejectForM1);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(k2Key)
        .await();

    // K2's non-interrupting boundary event re-arms; a fresh, unrelated M2 correlates to it
    // directly
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(k2Key)
        .skip(1)
        .await();

    // a very late duplicate REJECT(K1, M1) arrives only now, after K2 has moved on to M2
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleRejectForM1);

    final List<Record<MessageSubscriptionRecordValue>> recordsUpToBarrier =
        RecordingExporter.messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitByCount(r -> r.getIntent() == MessageSubscriptionIntent.REJECTED, 2)
            .asList();

    // then — the very late duplicate reject must not re-drive K2 into a second CORRELATING for a
    // message it already handled: the guard compares against the lock's own recorded owner, not
    // K2's now-stale (message-key: M2) subscription row
    final long correlatingEventsForM1OnK2 =
        recordsUpToBarrier.stream()
            .filter(r -> r.getIntent() == MessageSubscriptionIntent.CORRELATING)
            .filter(r -> r.getKey() == k2Key)
            .filter(r -> r.getValue().getMessageKey() == m1Key)
            .count();
    assertThat(correlatingEventsForM1OnK2)
        .as(
            "a very late duplicate stale REJECT must not re-correlate a message K2 already"
                + " handled, just because K2 has since moved on to a later message")
        .isEqualTo(1);

    // and — a bystander instance of the same process must not be able to pick up M1 either: if
    // the duplicate reject wrongly cleared M1's lock, this bystander's own CREATE-time scan would
    // grab it
    final long bystanderProcessInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(subscriptionPartition)
            .create();
    final long bystanderSubscriptionKey =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(bystanderProcessInstanceKey)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.messageSubscriptionRecords(
                            MessageSubscriptionIntent.CORRELATING)
                        .withRecordKey(bystanderSubscriptionKey)
                        .exists()))
        .as(
            "a very late duplicate stale REJECT must not release the lock on a message the"
                + " replacement already correlated and moved on from, letting a bystander"
                + " subscription of the same process correlate it again")
        .isFalse();
  }

  /**
   * Reproduces a fourth review comment on #61099: a different generation's claim on a message can
   * still be legitimate even after its own subscription row is gone (e.g. torn down by a second
   * suspend). {@code findSubscriptionToCorrelate}'s old check only asked "does some EXISTING row
   * already claim this message" — if that row doesn't exist, a duplicate/delayed stale reject for
   * the same message finds the scan wide open and (wrongly) reroutes it to any other eligible,
   * unrelated subscription of the same process, double-delivering a message the row-less generation
   * still legitimately owns.
   */
  @Test
  public void shouldNotRerouteToBystanderWhenOwningGenerationsSubscriptionRowIsGone() {
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
    final long k1Key = k1Created.getKey();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // keep the CORRELATE from reaching the PI partition, so K1 stays locked on M1
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            return false;
          }
          return true;
        });

    // M1: locks K1 and stays locked
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    final var correlatingM1 =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long m1Key = correlatingM1.getValue().getMessageKey();
    final long elementInstanceKey = correlatingM1.getValue().getElementInstanceKey();

    // suspend deletes K1 without releasing the lock M1 holds
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

    final var staleRejectForM1 =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(correlatingM1.getValue().getProcessDefinitionKey())
            .setBpmnProcessId(BufferUtil.wrapString(correlatingM1.getValue().getBpmnProcessId()))
            .setMessageName(BufferUtil.wrapString(correlatingM1.getValue().getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(correlatingM1.getValue().getCorrelationKey()))
            .setMessageKey(m1Key)
            .setInterrupting(false)
            .setTenantId(correlatingM1.getValue().getTenantId())
            .setSubscriptionKey(k1Key);

    // the first stale REJECT(K1, M1) reroutes M1 to K2, which correlates it fully
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleRejectForM1);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(k2Key)
        .await();

    // K2's own subscription row is now torn down by a second suspend — but the correlation lock
    // it claimed for M1 (a separate piece of state) is untouched by that
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(1)
        .await();

    // an unrelated bystander instance of the SAME process, matching the same message name +
    // correlation key, is alive and eligible right now
    final long bystanderProcessInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(subscriptionPartition)
            .create();
    final long bystanderSubscriptionKey =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(bystanderProcessInstanceKey)
            .getFirst()
            .getKey();

    // when — a very late duplicate of the original stale REJECT(K1, M1) finally arrives, after
    // K2's own row is gone
    engine.writeCommandOnPartition(
        subscriptionPartition, -1L, MessageSubscriptionIntent.REJECT, staleRejectForM1);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECTED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .asList();

    // then — the bystander, unrelated to K1/K2's generation, must not be wrongly handed a message
    // that K2's (now row-less) claim still legitimately owns
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.messageSubscriptionRecords(
                            MessageSubscriptionIntent.CORRELATING)
                        .withRecordKey(bystanderSubscriptionKey)
                        .exists()))
        .as(
            "a stale reject must not reroute a message to an unrelated bystander subscription"
                + " just because the rightful owner's own subscription row was torn down (e.g. by"
                + " suspend) while its ownership marker is still valid")
        .isFalse();
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
