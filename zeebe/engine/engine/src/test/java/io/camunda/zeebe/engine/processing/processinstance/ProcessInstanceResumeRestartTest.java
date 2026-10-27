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
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers restarting a resume that is stuck in {@code RESUMING} — accepting a fresh {@code RESUME}
 * on a {@code RESUMING} (not just {@code SUSPENDED}) instance, and the safety of two overlapping
 * drain chains that can result from it.
 */
public final class ProcessInstanceResumeRestartTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int BUFFERED_COMMAND_COUNT = 5;
  private static final int TAG_LENGTH_NEAR_MAX_FRAGMENT_SIZE = 4_193_450;

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestartHaltedDrainWithoutDuplicateResumingEvent() {
    // given - a halted resume: the drain is stuck in RESUMING because its one buffered command
    // overflows the batch when re-emitted (see ResumeProcessInstanceDrainTest for the derivation
    // of this tag length)
    final long processInstanceKey = deployAndStart();
    final var child = activatedChildren(processInstanceKey).getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferOversizedCommand(child);
    resume(processInstanceKey);
    final var firstRejection = awaitDrainRejection(processInstanceKey, 1);

    // when - a second RESUME is sent while still RESUMING
    resume(processInstanceKey);

    // then - accepted and restarts the chain: a second DRAIN halts on the same command again,
    // without a second RESUMING event being written
    awaitDrainRejection(processInstanceKey, 2);
    final var resumingEvents =
        RecordingExporter.records()
            .limit(r -> r.getPosition() >= firstRejection.getPosition())
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.RESUMING)
            .filter(r -> r.getKey() == processInstanceKey)
            .asList();
    assertThat(resumingEvents).hasSize(1);
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getSuspensionState()
                .getSuspensionState(processInstanceKey))
        .isEqualTo(SuspensionState.State.RESUMING);
  }

  @Test
  public void shouldDrainExactlyOnceWithTwoOverlappingResumeChains() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStart(processId);
    final var children = activatedChildren(processInstanceKey);
    // a real job only reaches SUSPENDED from ACTIVATABLE, and completing its element normally
    // requires the job to have been activated first, which exempts it from suspension - without
    // this, the raw COMPLETE_ELEMENT commands buffered below would leave each job dangling
    // SUSPENDED with its owning element already gone, a state the index-seeking resume can reach
    // and would race the drain chain ahead of COMPLETE_RESUMING
    ENGINE.jobs().withType(processId).withMaxJobsToActivate(BUFFERED_COMMAND_COUNT).activate();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferCompleteCommands(children);

    // when - two RESUME commands queued before either is processed; by the time the second is
    // processed the marker is already RESUMING, so it restarts the chain rather than being
    // rejected or re-writing RESUMING
    ENGINE.writeRecords(resumeCommand(processInstanceKey), resumeCommand(processInstanceKey));

    // then - exactly one RESUMING and one RESUMED despite two RESUME commands
    final var resumed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= resumed.getPosition())
                .withValueType(ValueType.PROCESS_INSTANCE)
                .withIntent(ProcessInstanceIntent.RESUMING)
                .filter(r -> r.getKey() == processInstanceKey)
                .asList())
        .hasSize(1);
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= resumed.getPosition())
                .withValueType(ValueType.PROCESS_INSTANCE)
                .withIntent(ProcessInstanceIntent.RESUMED)
                .filter(r -> r.getKey() == processInstanceKey)
                .asList())
        .hasSize(1);

    // and - every buffered command drained exactly once, none reprocessed by the second chain
    final var drained =
        RecordingExporter.records()
            .limit(r -> r.getPosition() >= resumed.getPosition())
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.DRAINED)
            .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
            .asList();
    assertThat(commandKeys(drained)).doesNotHaveDuplicates().hasSize(BUFFERED_COMMAND_COUNT);

    // and - each child element completed exactly once, not applied twice by the overlapping chain
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(BUFFERED_COMMAND_COUNT)
                .asList())
        .hasSize(BUFFERED_COMMAND_COUNT);
  }

  private static void resume(final long processInstanceKey) {
    ENGINE.writeRecords(resumeCommand(processInstanceKey));
  }

  private static RecordToWrite resumeCommand(final long processInstanceKey) {
    return RecordToWrite.command()
        .processInstance(
            ProcessInstanceIntent.RESUME,
            new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey))
        .key(processInstanceKey);
  }

  private static Record<RecordValue> awaitDrainRejection(
      final long processInstanceKey, final int occurrence) {
    return RecordingExporter.records()
        .onlyCommandRejections()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.DRAIN)
        .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
        .limit(occurrence)
        .asList()
        .getLast();
  }

  private static long deployAndStart() {
    return deployAndStart(Strings.newRandomValidBpmnId());
  }

  private static long deployAndStart(final String processId) {
    ENGINE.deployment().withXmlResource(parallelMultiInstanceProcess(processId)).deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariable("items", IntStream.range(0, BUFFERED_COMMAND_COUNT).boxed().toList())
        .create();
  }

  private static BpmnModelInstance parallelMultiInstanceProcess(final String processId) {
    // job type reuses the already-unique processId instead of a literal shared across every test
    // method on the same class-scoped ENGINE, so activating jobs by type in one test can't pick up
    // another test's leftover jobs
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

  private static void bufferOversizedCommand(final Record<ProcessInstanceRecordValue> child) {
    final var oversizedValue = new ProcessInstanceRecord();
    oversizedValue.wrap((ProcessInstanceRecord) child.getValue());
    oversizedValue.setTags(Set.of("x".repeat(TAG_LENGTH_NEAR_MAX_FRAGMENT_SIZE)));

    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, oversizedValue)
            .key(child.getKey()));
  }

  private static long processInstanceKeyOf(final Record<RecordValue> record) {
    return ((BufferedCommandRecordValue) record.getValue()).getProcessInstanceKey();
  }

  private static List<Long> commandKeys(final List<Record<RecordValue>> records) {
    return records.stream()
        .map(r -> ((BufferedCommandRecordValue) r.getValue()).getCommandKey())
        .toList();
  }
}
