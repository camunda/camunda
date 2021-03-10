/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessInstanceTokenTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String processId;

  @Before
  public void setUp() {
    processId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCompleteInstanceAfterEndEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end").done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterEventWithoutOutgoingSequenceFlows() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent("start").done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "start");
  }

  @Test
  public void shouldCompleteInstanceAfterActivityWithoutOutgoingSequenceFlows() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "task");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelSplit() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .serviceTask("task-2", t -> t.zeebeJobType("task-2"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelJoin() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
                .parallelGateway("join")
                .endEvent("end")
                .moveToNode("fork")
                .serviceTask("task-2", t -> t.zeebeJobType("task-2"))
                .connectTo("join")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterMessageIntermediateCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "catch",
                    e -> e.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'key':'123'}")
            .create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();
    ENGINE.message().withName("msg").withCorrelationKey("123").publish();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterTimerIntermediateCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent("catch", e -> e.timerWithDuration("PT0.1S"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterSubProcessEnded() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .subProcess(
                    "sub",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task-2", t -> t.zeebeJobType("task-2"))
                            .endEvent("end-sub"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterEventBasedGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .eventBasedGateway("gateway")
                .intermediateCatchEvent(
                    "catch-1",
                    e -> e.message(m -> m.name("msg-1").zeebeCorrelationKeyExpression("key")))
                .endEvent("end-2")
                .moveToNode("gateway")
                .intermediateCatchEvent(
                    "catch-2",
                    e -> e.message(m -> m.name("msg-2").zeebeCorrelationKeyExpression("key")))
                .endEvent("end-3")
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'key':'123'}")
            .create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();
    ENGINE.message().withName("msg-1").withCorrelationKey("123").publish();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterInterruptingBoundaryEventTriggered() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToActivity("task")
                .boundaryEvent("timeout", b -> b.cancelActivity(true).timerWithDuration("PT0.1S"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterNonInterruptingBoundaryEventTriggered() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
                .endEvent("end-1")
                .moveToActivity("task-1")
                .boundaryEvent("timeout", b -> b.cancelActivity(false).timerWithCycle("R1/PT0.1S"))
                .serviceTask("task-2", t -> t.zeebeJobType("task-2"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-1");
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "catch",
                    e -> e.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("key", "123")))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.message().withName("msg").withCorrelationKey("123").publish();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnActivity() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .serviceTask(
                    "task-2", t -> t.zeebeJobType("task-2").zeebeOutputExpression("result", "r"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("result", "123")))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnExclusiveGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .exclusiveGateway("gateway")
                .defaultFlow()
                .endEvent("end-2")
                .moveToNode("gateway")
                .sequenceFlowId("to-end-3")
                .conditionExpression("x < 21")
                .endEvent("end-3")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("x", 123)))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThatProcessInstanceCompletedAfter(processInstanceKey, "end-2");
  }

  private void assertThatProcessInstanceCompletedAfter(
      final long processInstanceKey, final String elementId) {
    final Record<ProcessInstanceRecordValue> lastEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementId)
            .getFirst();

    final Record<ProcessInstanceRecordValue> completedEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(processId)
            .getFirst();

    assertThat(completedEvent.getPosition()).isGreaterThan(lastEvent.getPosition());
  }
}
