/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Verifies the chunked drain: on RESUME the instance moves to RESUMING, the commands buffered while
 * it was suspended are replayed one {@code DRAIN} cycle at a time, and RESUMED closes the chain
 * once the buffer is empty.
 */
public final class ResumeProcessInstanceDrainTest {

  /** Number of parallel multi-instance children, and therefore of buffered commands, per test. */
  private static final int BUFFERED_COMMAND_COUNT = 5;

  /**
   * Together, five commands of this size exceed the 4MB a single record batch can hold, so an
   * unbounded atomic drain could not write them out in one go.
   */
  private static final String OVERSIZED_TAG = "x".repeat(1024 * 1024);

  private static final Set<ProcessInstanceIntent> ELEMENT_LIFECYCLE_INTENTS =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATING,
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETING,
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDrainBufferedCommandsOneCycleAtATime() {
    // given - an instance with several buffered commands
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - draining takes one cycle per buffered command, plus the final cycle that finds the
    // buffer empty and writes RESUMED
    final var bufferedCommandRecords = bufferedCommandRecordsUntilResumed(processInstanceKey);
    assertThat(
            recordsWithIntent(bufferedCommandRecords, ProcessInstanceBufferedCommandIntent.DRAIN))
        .hasSize(BUFFERED_COMMAND_COUNT + 1);

    // and every buffered command is drained, in the order it was buffered
    final var buffered =
        commandKeys(
            recordsWithIntent(
                bufferedCommandRecords, ProcessInstanceBufferedCommandIntent.BUFFERED));
    final var drained =
        commandKeys(
            recordsWithIntent(
                bufferedCommandRecords, ProcessInstanceBufferedCommandIntent.DRAINED));
    assertThat(buffered).hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(drained).containsExactlyElementsOf(buffered);
  }

  @Test
  public void shouldStayResumingUntilTheBufferIsDrained() {
    // given
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - RESUMING is written before the drain starts, RESUMED only after the last command was
    // drained
    final var resuming =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var resumed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(resuming.getPosition()).isLessThan(resumed.getPosition());

    final var drained =
        recordsWithIntent(
            bufferedCommandRecordsUntilResumed(processInstanceKey),
            ProcessInstanceBufferedCommandIntent.DRAINED);
    assertThat(drained).hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(drained.getLast().getPosition()).isLessThan(resumed.getPosition());

    // and the suspension marker is gone once RESUMED has been applied
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getSuspensionState()
                .getSuspensionState(processInstanceKey))
        .isNull();
  }

  @Test
  public void shouldReachSameStateAsNeverSuspendedRun() {
    // given - a control run that is never suspended
    final String processId = Strings.newRandomValidBpmnId();
    final long controlInstanceKey = deployAndStart(processId);
    bufferCompleteCommands(activatedChildren(controlInstanceKey));

    // and a run of the same process definition that is suspended while the same commands arrive,
    // then resumed
    final long processInstanceKey = start(processId);
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - both instances go through the same element lifecycle and complete
    assertThat(elementLifecycle(processInstanceKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlInstanceKey));
  }

  @Test
  public void shouldDrainBufferLargerThanASingleRecordBatch() {
    // given - a buffer whose commands together do not fit into a single record batch
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferOversizedCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - every command is drained and the instance resumes, without a record batch overflow
    final var bufferedCommandRecords = bufferedCommandRecordsUntilResumed(processInstanceKey);
    assertThat(
            recordsWithIntent(bufferedCommandRecords, ProcessInstanceBufferedCommandIntent.DRAINED))
        .hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(bufferedCommandRecords)
        .noneMatch(r -> r.getRejectionType() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
  }

  @Test
  public void shouldRaiseIncidentAndKeepDrainingWhenACommandCannotBeReplayed() {
    // given - a buffered command that alone does not fit into a record batch together with the
    // DRAINED event that removes it, so replaying it always fails
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferUndrainableCommand(children.getFirst());
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - an incident is raised on the process instance instead of stranding it
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.MESSAGE_SIZE_EXCEEDED)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(processInstanceKey);

    // and the drain moves past it: every buffered command is removed and the instance resumes
    final var bufferedCommandRecords = bufferedCommandRecordsUntilResumed(processInstanceKey);
    assertThat(
            recordsWithIntent(bufferedCommandRecords, ProcessInstanceBufferedCommandIntent.DRAINED))
        .hasSize(BUFFERED_COMMAND_COUNT + 1);
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getSuspensionState()
                .getSuspensionState(processInstanceKey))
        .isNull();
  }

  private static long deployAndStart() {
    return deployAndStart(Strings.newRandomValidBpmnId());
  }

  private static long deployAndStart(final String processId) {
    ENGINE.deployment().withXmlResource(parallelMultiInstanceProcess(processId)).deploy();
    return start(processId);
  }

  private static long start(final String processId) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariable("items", IntStream.range(0, BUFFERED_COMMAND_COUNT).boxed().toList())
        .create();
  }

  private static BpmnModelInstance parallelMultiInstanceProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(
            "task",
            t ->
                t.zeebeJobType("type")
                    .multiInstance(
                        m -> m.zeebeInputCollectionExpression("items").zeebeInputElement("item")))
        .endEvent()
        .done();
  }

  private static List<Record<ProcessInstanceRecordValue>> activatedChildren(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .limit(BUFFERED_COMMAND_COUNT)
        .asList();
  }

  /**
   * Writes one internal {@code COMPLETE_ELEMENT} command per child. While the instance is suspended
   * these are classified {@code BUFFER} by {@code BpmnStreamProcessor} and end up in the buffer; on
   * a running instance they complete the children right away.
   */
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

  /**
   * Buffers commands that are individually about a megabyte, written one by one so the log append
   * itself stays within its own limits. They target element instance keys that do not exist, so
   * replaying them is rejected instead of carrying the oversized payload into the element
   * lifecycle: what is under test here is that the drain manages to write them out at all.
   */
  private static void bufferOversizedCommands(
      final List<Record<ProcessInstanceRecordValue>> children) {
    for (final var child : children) {
      final var oversizedValue = new ProcessInstanceRecord();
      oversizedValue.wrap((ProcessInstanceRecord) child.getValue());
      oversizedValue.setTags(Set.of(OVERSIZED_TAG));

      ENGINE.writeRecords(
          RecordToWrite.command()
              .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, oversizedValue)
              .key(child.getKey() + BUFFERED_COMMAND_COUNT * 1_000_000L));
    }
  }

  /**
   * Buffers a single command that can never be replayed: at roughly 3MB it still fits into the
   * record batch that buffers it, but not into the one the drain needs for the replayed command and
   * the DRAINED event that removes it together.
   */
  private static void bufferUndrainableCommand(final Record<ProcessInstanceRecordValue> child) {
    final var undrainableValue = new ProcessInstanceRecord();
    undrainableValue.wrap((ProcessInstanceRecord) child.getValue());
    undrainableValue.setTags(Set.of(OVERSIZED_TAG.repeat(3)));

    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, undrainableValue)
            .key(child.getKey() + BUFFERED_COMMAND_COUNT * 1_000_000L));
  }

  private static List<Record<RecordValue>> bufferedCommandRecordsUntilResumed(
      final long processInstanceKey) {
    final var resumed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return RecordingExporter.records()
        .limit(r -> r.getPosition() >= resumed.getPosition())
        .withValueType(ValueType.PROCESS_INSTANCE_BUFFERED_COMMAND)
        .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
        .asList();
  }

  private static List<Record<RecordValue>> recordsWithIntent(
      final List<Record<RecordValue>> records, final ProcessInstanceBufferedCommandIntent intent) {
    return records.stream().filter(r -> r.getIntent() == intent).toList();
  }

  private static List<Long> commandKeys(final List<Record<RecordValue>> records) {
    return records.stream()
        .map(r -> ((ProcessInstanceBufferedCommandRecordValue) r.getValue()).getCommandKey())
        .toList();
  }

  private static long processInstanceKeyOf(final Record<RecordValue> record) {
    return ((ProcessInstanceBufferedCommandRecordValue) record.getValue()).getProcessInstanceKey();
  }

  private static List<String> elementLifecycle(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .filter(r -> ELEMENT_LIFECYCLE_INTENTS.contains(r.getIntent()))
        .map(r -> r.getIntent() + ":" + r.getValue().getElementId())
        .toList();
  }
}
