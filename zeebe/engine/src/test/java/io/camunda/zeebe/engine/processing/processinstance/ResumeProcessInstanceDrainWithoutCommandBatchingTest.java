/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class ResumeProcessInstanceDrainWithoutCommandBatchingTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  private static final int BUFFERED_COMMAND_COUNT = 10;

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDrainWhenEveryCycleIsCommittedSeparately() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStart(processId);
    final var children = activatedChildren(processInstanceKey);
    ENGINE.jobs().withType(processId).withMaxJobsToActivate(BUFFERED_COMMAND_COUNT).activate();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - one DRAIN cycle per command; the last cycle finds the buffer empty and hands off to
    // COMPLETE_RESUMING directly instead of an extra empty DRAIN cycle
    final var bufferedCommandRecords = bufferedCommandRecordsUntilResumed(processInstanceKey);
    assertThat(recordsWithIntent(bufferedCommandRecords, BufferedCommandIntent.DRAIN))
        .hasSize(BUFFERED_COMMAND_COUNT);

    final var buffered =
        commandKeys(recordsWithIntent(bufferedCommandRecords, BufferedCommandIntent.BUFFERED));
    final var drained =
        commandKeys(recordsWithIntent(bufferedCommandRecords, BufferedCommandIntent.DRAINED));
    assertThat(buffered).hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(drained).containsExactlyElementsOf(buffered);
    assertThat(bufferedCommandRecords)
        .noneMatch(r -> r.getRejectionType() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private static long deployAndStart(final String processId) {
    // job type reuses the already-unique processId rather than a literal, so activating jobs by
    // type in this test can't pick up another test method's leftover jobs
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(processId)
                            .multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariable("items", IntStream.range(0, BUFFERED_COMMAND_COUNT).boxed().toList())
        .create();
  }

  private static List<Record<ProcessInstanceRecordValue>> activatedChildren(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .limit(BUFFERED_COMMAND_COUNT)
        .asList();
  }

  private static void bufferCompleteCommands(
      final List<Record<ProcessInstanceRecordValue>> children) {
    ENGINE.writeRecords(
        children.stream()
            .map(
                child ->
                    RecordToWrite.command()
                        .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, child.getValue())
                        .key(child.getKey()))
            .toArray(RecordToWrite[]::new));
  }

  private static List<Record<RecordValue>> bufferedCommandRecordsUntilResumed(
      final long processInstanceKey) {
    final var resumed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return RecordingExporter.records()
        .limit(r -> r.getPosition() >= resumed.getPosition())
        .withValueType(ValueType.BUFFERED_COMMAND)
        .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
        .asList();
  }

  private static List<Record<RecordValue>> recordsWithIntent(
      final List<Record<RecordValue>> records, final BufferedCommandIntent intent) {
    return records.stream().filter(r -> r.getIntent() == intent).toList();
  }

  private static List<Long> commandKeys(final List<Record<RecordValue>> records) {
    return records.stream()
        .map(r -> ((BufferedCommandRecordValue) r.getValue()).getCommandKey())
        .toList();
  }

  private static long processInstanceKeyOf(final Record<RecordValue> record) {
    return ((BufferedCommandRecordValue) record.getValue()).getProcessInstanceKey();
  }
}
