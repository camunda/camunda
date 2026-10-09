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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * {@link EngineRule} is a {@code @Rule}, not a {@code @ClassRule}, so each test restarts a fresh
 * engine instead of leaking state across the restart cycle.
 */
public final class SuspendResumeReplayTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRemainSuspendedAfterRestart() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(model).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // snapshot before suspending, so recovery must rebuild the marker from the SUSPENDED event
    engine.snapshot();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.stop();
    RecordingExporter.reset();
    engine.start();

    // then - via observable behavior, not by reading SuspensionState directly: state classes
    // aren't thread-safe against the concurrently running stream processor
    final var rejection =
        engine.job().withType(jobType).ofInstance(processInstanceKey).expectRejection().complete();
    assertThat(rejection.getRejectionType())
        .describedAs("suspension marker must survive restart")
        .isEqualTo(RejectionType.INVALID_STATE);

    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    engine.job().withType(jobType).ofInstance(processInstanceKey).complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();
  }

  @Test
  public void shouldDrainBufferedCommandAfterRestart() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobTypeA = Strings.newRandomValidBpmnId();
    final var jobTypeB = Strings.newRandomValidBpmnId();
    final var model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("taskA", t -> t.zeebeJobType(jobTypeA))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("taskB", t -> t.zeebeJobType(jobTypeB))
            .connectTo("join")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(model).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final Record<ProcessInstanceRecordValue> taskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("taskA")
            .getFirst();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // activate taskA's job so it's ACTIVATED, not ACTIVATABLE, before suspending - otherwise
    // suspension parks it, and draining the injected command below removes its owning element
    // without ever completing it, leaving it dangling (see ResumeProcessInstanceDrainTest)
    engine.jobs().withType(jobTypeA).withMaxJobsToActivate(1).activate();

    // snapshot before suspending and before the command buffers, so recovery must rebuild both
    // from the SUSPENDED and BUFFERED events
    engine.snapshot();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // simulates the internal completion signal a call activity or conditional trigger would send
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskA.getValue())
            .key(taskA.getKey()));
    RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
        .await();

    // when
    engine.stop();
    RecordingExporter.reset();
    engine.start();

    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.BUFFERED_COMMAND)
                .withIntent(BufferedCommandIntent.DRAINED)
                .filter(r -> processInstanceKeyOf(r) == processInstanceKey)
                .exists())
        .describedAs("buffered command must survive restart and drain on resume")
        .isTrue();

    engine.job().withType(jobTypeB).ofInstance(processInstanceKey).complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private static long processInstanceKeyOf(final Record<?> record) {
    return ((BufferedCommandRecordValue) record.getValue()).getProcessInstanceKey();
  }
}
