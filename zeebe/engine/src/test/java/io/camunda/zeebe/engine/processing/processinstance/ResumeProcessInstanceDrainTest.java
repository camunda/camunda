/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class ResumeProcessInstanceDrainTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int BUFFERED_COMMAND_COUNT = 5;
  // five 1MB commands exceed the 4MB record batch limit, so an atomic drain would overflow
  private static final String ONE_MEGABYTE_TAG = "x".repeat(1024 * 1024);

  private static final Set<ProcessInstanceIntent> ELEMENT_LIFECYCLE_INTENTS =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATING,
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETING,
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

  // A tag length picked to land inside the narrow window where the command still fits as a
  // single record (so it buffers successfully) but overflows the batch once bundled with DRAINED
  // + the next DRAIN during a drain cycle (three records in one append). Derived empirically from
  // the engine's own framing formula (Sequencer#canWriteEvents, maxFragmentSize = 4 MiB default):
  // the window is roughly tagLen in [4_193_260, 4_193_676); this sits at its midpoint for margin
  // against per-record field overhead (e.g. a longer bpmnProcessId) that a synthetic measurement
  // would not otherwise account for.
  private static final int TAG_LENGTH_NEAR_MAX_FRAGMENT_SIZE = 4_193_450;

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDrainBufferedCommandsOneCycleAtATime() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStart(processId);
    final var children = activatedChildren(processInstanceKey);
    activateJobs(processId);
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
  }

  @Test
  public void shouldStayResumingUntilTheBufferIsDrained() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStart(processId);
    final var children = activatedChildren(processInstanceKey);
    activateJobs(processId);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
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
            bufferedCommandRecordsUntilResumed(processInstanceKey), BufferedCommandIntent.DRAINED);
    assertThat(drained).hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(drained.getLast().getPosition()).isLessThan(resumed.getPosition());

    // suspension marker removed once RESUMED applied
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getSuspensionState()
                .getSuspensionState(processInstanceKey))
        .isNull();
  }

  @Test
  public void shouldReachSameStateAsNeverSuspendedRun() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long controlInstanceKey = deployAndStart(processId);
    final var controlChildren = activatedChildren(controlInstanceKey);
    activateJobs(processId);
    bufferCompleteCommands(controlChildren);

    final long processInstanceKey = start(processId);
    final var children = activatedChildren(processInstanceKey);
    activateJobs(processId);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertThat(elementLifecycle(processInstanceKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlInstanceKey));
  }

  @Test
  public void shouldDrainBufferLargerThanASingleRecordBatch() {
    // given
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferOversizedCommands(children);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    final var bufferedCommandRecords = bufferedCommandRecordsUntilResumed(processInstanceKey);
    assertThat(recordsWithIntent(bufferedCommandRecords, BufferedCommandIntent.DRAINED))
        .hasSize(BUFFERED_COMMAND_COUNT);
    assertThat(bufferedCommandRecords)
        .noneMatch(r -> r.getRejectionType() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
  }

  @Test
  public void shouldHaltDrainWithoutBanOrCrashWhenCommandExceedsBatchSize() {
    // given - the buffered command is close enough to maxFragmentSize that bundling it with the
    // DRAINED event and next DRAIN command overflows the batch
    final long processInstanceKey = deployAndStart();
    final var children = activatedChildren(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    final var oversizedChild = children.getFirst();
    bufferCommandNearMaxFragmentSize(oversizedChild);

    // when - written directly rather than via the fluent resume() client, which by default
    // blocks awaiting RESUMED; that event never arrives in this halted scenario
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.RESUME,
                new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey))
            .key(processInstanceKey));

    // then - the DRAIN is rejected instead of banning the instance or crashing the partition
    final var rejection =
        RecordingExporter.records()
            .onlyCommandRejections()
            .withRejectionType(RejectionType.EXCEEDED_BATCH_RECORD_SIZE)
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.DRAIN)
            .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
            .getFirst();
    assertThat(rejection).isNotNull();

    // and - the buffered command is still there, never drained
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= rejection.getPosition())
                .withValueType(ValueType.BUFFERED_COMMAND)
                .withIntent(BufferedCommandIntent.DRAINED)
                .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
                .asList())
        .isEmpty();

    // and - the marker stays RESUMING (not reverted to SUSPENDED, not removed)
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getSuspensionState()
                .getSuspensionState(processInstanceKey))
        .isEqualTo(SuspensionState.State.RESUMING);

    // and - the partition survived: an unrelated instance still processes normally afterward
    final long otherInstanceKey = deployAndStart();
    final var terminated = ENGINE.processInstance().withInstanceKey(otherInstanceKey).cancel();
    assertThat(terminated).isNotNull();

    // and - across all this, the halted instance never reached RESUMED
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= terminated.getPosition())
                .withValueType(ValueType.PROCESS_INSTANCE)
                .withIntent(ProcessInstanceIntent.RESUMED)
                .filter(r -> r.getKey() == processInstanceKey)
                .asList())
        .isEmpty();
  }

  private static void bufferCommandNearMaxFragmentSize(
      final Record<ProcessInstanceRecordValue> child) {
    final var oversizedValue = new ProcessInstanceRecord();
    oversizedValue.wrap((ProcessInstanceRecord) child.getValue());
    oversizedValue.setTags(Set.of("x".repeat(TAG_LENGTH_NEAR_MAX_FRAGMENT_SIZE)));

    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, oversizedValue)
            .key(child.getKey()));
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
                t.zeebeJobType(processId)
                    .multiInstance(
                        m -> m.zeebeInputCollectionExpression("items").zeebeInputElement("item")))
        .endEvent()
        .done();
  }

  /**
   * Activates jobs before suspending so they are {@code ACTIVATED}, exempting them from suspension
   * and preventing dangling {@code SUSPENDED} entries after element completion races the drain.
   */
  private static void activateJobs(final String jobType) {
    ENGINE.jobs().withType(jobType).withMaxJobsToActivate(BUFFERED_COMMAND_COUNT).activate();
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

  // targets non-existent keys so each command is rejected once drained, not processed
  private static void bufferOversizedCommands(
      final List<Record<ProcessInstanceRecordValue>> children) {
    children.forEach(
        child -> {
          final var oversizedValue = new ProcessInstanceRecord();
          oversizedValue.wrap((ProcessInstanceRecord) child.getValue());
          oversizedValue.setTags(Set.of(ONE_MEGABYTE_TAG));

          ENGINE.writeRecords(
              RecordToWrite.command()
                  .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, oversizedValue)
                  .key(child.getKey() + BUFFERED_COMMAND_COUNT * 1_000_000L));
        });
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

  private static List<String> elementLifecycle(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .filter(r -> ELEMENT_LIFECYCLE_INTENTS.contains(r.getIntent()))
        .map(r -> r.getIntent() + ":" + r.getValue().getElementId())
        .toList();
  }
}
