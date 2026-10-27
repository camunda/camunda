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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers an instance that ends while {@code RESUMING}: a drained command (here a timer that became
 * due while suspended) ends the instance before the resume chain reaches {@code COMPLETE_RESUMING}.
 * The resume must still end with exactly one {@code RESUMED}, so batch resume items complete.
 */
public final class ResumeProcessInstanceEndsWhileResumingTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteResumedWhenTerminateEndEventEndsInstance() {
    // given - a parked job on one branch, a timer leading to a terminate end event on the other
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
            .endEvent()
            .moveToNode("fork")
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
            .endEvent("terminate", e -> e.terminate())
            .done();
    final long processInstanceKey = suspendWithDueTimer(process, processId);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertResumedOnceBeforeEnd(processInstanceKey);
  }

  @Test
  public void shouldCompleteInstanceWithEndListenerWhenEndingWhileResuming() {
    // given - same as above, plus a process end listener; its job can't complete while RESUMING
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .zeebeEndExecutionListener(listenerType)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
            .endEvent()
            .moveToNode("fork")
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
            .endEvent("terminate", e -> e.terminate())
            .done();
    final long processInstanceKey = suspendWithDueTimer(process, processId);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then
    assertResumedOnceBeforeEnd(processInstanceKey);
  }

  @Test
  public void shouldWriteResumedOnceWhenResumeFinishesBeforeInstanceEnds() {
    // given - a parked job whose task is interrupted by a timer leading to a plain end event; here
    // COMPLETE_RESUMING runs before the instance completes, so the end must not write RESUMED again
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
            .boundaryEvent("timer", b -> b.timerWithDuration("PT1S"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();
    final long processInstanceKey = suspendWithDueTimer(process, processId);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertResumedOnceBeforeEnd(processInstanceKey);
  }

  private static long suspendWithDueTimer(final BpmnModelInstance process, final String processId) {
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    return processInstanceKey;
  }

  private static void assertResumedOnceBeforeEnd(final long processInstanceKey) {
    final List<Record<ProcessInstanceRecordValue>> records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .toList();

    final var resumed =
        records.stream().filter(r -> r.getIntent() == ProcessInstanceIntent.RESUMED).toList();
    assertThat(resumed).hasSize(1);

    final var processEnd =
        records.stream()
            .filter(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getBpmnElementType() == BpmnElementType.PROCESS)
            .findFirst()
            .orElseThrow();
    assertThat(resumed.getFirst().getPosition()).isLessThan(processEnd.getPosition());
  }
}
