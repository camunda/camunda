/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;

public final class ParallelGatewayTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance FORK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .serviceTask("task1", b -> b.zeebeJobType("type1"))
          .endEvent("end1")
          .moveToNode("fork")
          .serviceTask("task2", b -> b.zeebeJobType("type2"))
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance FORK_JOIN_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .sequenceFlowId("flow1")
          .parallelGateway("join")
          .endEvent("end")
          .moveToNode("fork")
          .sequenceFlowId("flow2")
          .connectTo("join")
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldActivateTasksOnParallelBranches() {
    // given
    engine.deployment().withXmlResource(FORK_PROCESS).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> taskEvents =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    engine.deployment().withXmlResource(FORK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    engine.job().ofInstance(processInstanceKey).withType("type1").complete();

    // when
    engine.job().ofInstance(processInstanceKey).withType("type2").complete();

    // then
    final List<Record<ProcessInstanceRecordValue>> completedEvents =
        RecordingExporter.processInstanceRecords()
            .withElementType(BpmnElementType.END_EVENT)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactly("end1", "end2");

    RecordingExporter.processInstanceRecords()
        .withElementId(PROCESS_ID)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();
  }

  @Test
  public void shouldCompleteScopeWithMultipleTokensOnSamePath() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .connectTo("join")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldPassThroughParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .sequenceFlowId("flow2")
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple("fork", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("fork", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("fork", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("fork", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("flow2", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteScopeOnParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple("fork", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.COMPLETE_ELEMENT));
  }

  @Test
  public void shouldMergeParallelBranches() {
    // given
    engine.deployment().withXmlResource(FORK_JOIN_PROCESS).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("flow1", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING))
        .containsSubsequence(
            tuple("flow2", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING))
        .containsOnlyOnce(tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldOnlyTriggerGatewayWhenAllBranchesAreActivated() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("exclusiveJoin")
            .moveToLastGateway()
            .connectTo("exclusiveJoin")
            .sequenceFlowId("joinFlow1")
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeJobType("type"))
            .sequenceFlowId("joinFlow2")
            .connectTo("join")
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // waiting until we have signalled the first incoming sequence flow twice
    // => this should not trigger the gateway yet
    RecordingExporter.processInstanceRecords()
        .limit(r -> "joinFlow1".equals(r.getValue().getElementId()))
        .limit(2)
        .skip(1)
        .getFirst();

    // when
    // we complete the job
    engine.job().ofInstance(processInstanceKey).withType("type").complete();

    // then
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .limit(
                r ->
                    "join".equals(r.getValue().getElementId())
                        && ProcessInstanceIntent.ELEMENT_COMPLETED == r.getIntent())
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("joinFlow1", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow1", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow2", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldMergeAndSplitInOneGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .parallelGateway("fork")
            .parallelGateway("join-fork")
            .moveToNode("fork")
            .connectTo("join-fork")
            .serviceTask("task1", b -> b.zeebeJobType("type1"))
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeJobType("type2"))
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> elementInstances =
        RecordingExporter.processInstanceRecords()
            .filter(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                        && r.getValue().getBpmnElementType() == BpmnElementType.SERVICE_TASK)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(elementInstances)
        .extracting(e -> e.getValue().getElementId())
        .contains("task1", "task2");
  }

  @Test
  public void shouldSplitWithUncontrolledFlow() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask("task1", b -> b.zeebeJobType("type1"))
            .moveToNode("start")
            .serviceTask("task2", b -> b.zeebeJobType("type2"))
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> taskEvents =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), process))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  // Regression test for https://github.com/camunda/zeebe/issues/6778
  @Test
  public void shouldRejectActivateCommandWhenSequenceFlowIsTakenTwice() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("splitting")
            .parallelGateway("joining")
            .moveToNode("splitting")
            .exclusiveGateway("exclusive")
            .moveToNode("splitting")
            .connectTo("exclusive")
            .moveToNode("exclusive")
            .connectTo("joining")
            .moveToNode("joining")
            .endEvent("endEvent")
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    final long key = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(key)
            .limit("endEvent", ProcessInstanceIntent.ELEMENT_COMPLETED)
            .toList();

    assertThat(
            records.stream()
                .filter(
                    r -> r.getValue().getBpmnElementType().equals(BpmnElementType.PARALLEL_GATEWAY))
                .filter(r -> r.getIntent().equals(ProcessInstanceIntent.ACTIVATE_ELEMENT))
                .filter(r -> r.getRecordType().equals(RecordType.COMMAND_REJECTION)))
        .describedAs("activate command should be rejected twice")
        .hasSize(2)
        .extracting(Record::getRejectionType, Record::getRejectionReason)
        .describedAs("rejection should contain correct rejection reason")
        .containsOnly(
            Tuple.tuple(
                RejectionType.INVALID_STATE,
                "Expected to be able to activate parallel gateway 'joining', but not all sequence flows have been taken."));

    assertThat(
            records.stream()
                .filter(r -> r.getValue().getElementId().equals("joining"))
                .filter(r -> r.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATED)))
        .describedAs("joining gateway should only be activated once")
        .hasSize(1);
  }

  private static boolean isServiceTaskInProcess(
      final String activityId, final BpmnModelInstance process) {
    return process.getModelElementsByType(ServiceTask.class).stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}
