/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * A message subscription closed by suspend is acknowledged by the subscription's partition with a
 * {@code ProcessMessageSubscription.DELETE}. When that acknowledgement arrives while the instance
 * is already RESUMING, the delete processor buffers the REOPEN and re-triggers a DRAIN, and both
 * must carry the instance's ordinal. A single partition writes the acknowledgement as a local
 * follow-up command, so only the cross-partition hop can hold it back until the resume has started.
 */
public final class StorageOrdinalCrossPartitionSuspensionTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_PARTITION = 1;
  private static final int FIXED_ORDINAL = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAssignConfiguredOrdinalToDrainRetriggeredByCloseAckWhileResuming() {
    // given: an instance on one partition with its message subscription on another
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final long processInstanceKey = deployAndStart(processId, messageName, correlationKey);
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // and: the close acknowledgement sent back by the subscription partition is held
    final var heldAck = new AtomicReference<ProcessMessageSubscriptionRecord>();
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.DELETE) {
            final var copy = new ProcessMessageSubscriptionRecord();
            copy.wrap((ProcessMessageSubscriptionRecord) command);
            heldAck.set(copy);
            return false;
          }
          return true;
        });
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(heldAck.get())
                    .as("the close acknowledgement must have been held")
                    .isNotNull());

    // when: the instance resumes before the acknowledgement arrives, so the drain parks
    engine.writeCommandOnPartition(
        PROCESS_INSTANCE_PARTITION,
        processInstanceKey,
        ProcessInstanceIntent.RESUME,
        new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMING)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    final var parkedDrain =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.DRAIN)
            .onlyCommandRejections()
            .filter(r -> bufferedCommand(r).getProcessInstanceKey() == processInstanceKey)
            .getFirst();
    assertThat(parkedDrain.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);

    // and: the held acknowledgement is released while the instance is RESUMING
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> true);
    engine.writeCommandOnPartition(
        PROCESS_INSTANCE_PARTITION, -1L, ProcessMessageSubscriptionIntent.DELETE, heldAck.get());

    // then: the DRAIN re-triggered by the acknowledgement carries the ordinal
    final var retriggeredDrain =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.DRAIN)
            .onlyCommands()
            .filter(r -> r.getPosition() > parkedDrain.getPosition())
            .filter(r -> bufferedCommand(r).getProcessInstanceKey() == processInstanceKey)
            .getFirst();
    assertThat(bufferedCommand(retriggeredDrain).getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: the REOPEN buffered by the acknowledgement carries the ordinal
    final var reopenBuffered =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r ->
                    bufferedCommand(r).getProcessInstanceKey() == processInstanceKey
                        && bufferedCommand(r).getIntent()
                            == ProcessMessageSubscriptionIntent.REOPEN)
            .getFirst();
    assertThat(bufferedCommand(reopenBuffered).getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: the resume completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  private static BufferedCommandRecordValue bufferedCommand(final Record<?> record) {
    return (BufferedCommandRecordValue) record.getValue();
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

  private long deployAndStart(
      final String processId, final String messageName, final String correlationKey) {
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
    return engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .onPartition(PROCESS_INSTANCE_PARTITION)
        .create();
  }
}
