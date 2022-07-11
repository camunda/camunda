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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class InclusiveGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance INCLUSIVE_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .inclusiveGateway("inclusive")
          .sequenceFlowId("s1")
          .conditionExpression("= contains(str,\"a\")")
          .serviceTask("task1", b -> b.zeebeJobType("type1"))
          .endEvent("end1")
          .moveToNode("inclusive")
          .sequenceFlowId("s2")
          .conditionExpression("= contains(str,\"b\")")
          .serviceTask("task2", b -> b.zeebeJobType("type2"))
          .endEvent("end2")
          .moveToLastInclusiveGateway()
          .defaultFlow()
          .sequenceFlowId("s3")
          .conditionExpression("= contains(str,\"c\")")
          .serviceTask("task3", b -> b.zeebeJobType("type3"))
          .endEvent("end3")
          .done();
  private static final BpmnModelInstance INCLUSIVE_JOIN_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .inclusiveGateway("fork")
          .defaultFlow()
          .sequenceFlowId("flow1")
          .conditionExpression("= contains(str,\"a\")")
          .serviceTask("task1", b -> b.zeebeJobType("type1"))
          .inclusiveGateway("join")
          .endEvent("end")
          .moveToNode("fork")
          .sequenceFlowId("flow2")
          .conditionExpression("= contains(str,\"b\")")
          .serviceTask("task2", b -> b.zeebeJobType("type2"))
          .connectTo("join")
          .moveToNode("fork")
          .sequenceFlowId("flow3")
          .conditionExpression("= contains(str,\"c\")")
          .serviceTask("task3", b -> b.zeebeJobType("type3"))
          .connectTo("join")
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSplitOnInclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("inclusive")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .endEvent("end2")
            .moveToLastInclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s3")
            .conditionExpression("= contains(str,\"c\")")
            .endEvent("end3")
            .done();
    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b,c").create();
    final long processInstance3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "d").create();

    final List<Long> processInstanceKeys =
        Arrays.asList(processInstance1, processInstance2, processInstance3);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .valueFilter(r -> processInstanceKeys.contains(r.getProcessInstanceKey()))
                .withElementType(BpmnElementType.END_EVENT)
                .limit(6))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(
            tuple(processInstance1, "end1"),
            tuple(processInstance1, "end2"),
            tuple(processInstance2, "end1"),
            tuple(processInstance2, "end2"),
            tuple(processInstance2, "end3"),
            tuple(processInstance3, "end3"));
  }

  @Test
  public void shouldJoinOnInclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("split")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .inclusiveGateway("joinRequest")
            .moveToLastInclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "d").create();
    final long processInstance3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b,c").create();

    // then

    List<String> takenSequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance1)
            .limit(4)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s1").contains("s2");

    takenSequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstance2)
            .limitToProcessInstanceCompleted()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s2").doesNotContain("s1");

    takenSequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance3)
            .limit(4)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s2").doesNotContain("s1");
  }

  @Test
  public void shouldSetSourceRecordPositionCorrectOnJoinInclusive() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("inclusive")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .inclusiveGateway("joinRequest")
            .moveToLastInclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();
    // then
    List<Record<ProcessInstanceRecordValue>> sequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance1)
            .limit(4)
            .asList();

    List<Record<ProcessInstanceRecordValue>> gateWays =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withElementType(BpmnElementType.INCLUSIVE_GATEWAY)
            .withProcessInstanceKey(processInstance1)
            .limit(2)
            .asList();

    // assert that gateway activation originates from the same command as the sequence flow taken
    // and that the correct flow was taken
    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getSourceRecordPosition());
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getSourceRecordPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s2");
    assertThat(sequenceFlows.get(2).getValue().getElementId()).isEqualTo("s1");

    // when
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b").create();
    // then
    sequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance2)
            .limit(3)
            .collect(Collectors.toList());

    gateWays =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withElementType(BpmnElementType.INCLUSIVE_GATEWAY)
            .withProcessInstanceKey(processInstance2)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getSourceRecordPosition());
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getSourceRecordPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s2");

    // when
    final long processInstance3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "d").create();
    // then
    sequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance2)
            .limit(3)
            .collect(Collectors.toList());

    gateWays =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withElementType(BpmnElementType.INCLUSIVE_GATEWAY)
            .withProcessInstanceKey(processInstance2)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getSourceRecordPosition());
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getSourceRecordPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s2");
  }

  @Test
  public void testProcessInstanceStatesWithInclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("inclusive")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("a")
            .moveToLastInclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .endEvent("b")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a").create();

    // then
    List<Record<ProcessInstanceRecordValue>> processEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey2)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);

    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "c").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey3)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);

    // when
    final long processInstanceKey4 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey4)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldSplitTakingTheDefaultFlowIfNoConditionsAreTrue() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway()
            .defaultFlow()
            .endEvent("a")
            .moveToLastInclusiveGateway()
            .conditionExpression("= contains(str,\"b\")")
            .endEvent("b")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a").create();

    // then
    final List<Record<ProcessInstanceRecordValue>> completedEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.END_EVENT)
            .collect(Collectors.toList());

    assertThat(completedEvents).extracting(r -> r.getValue().getElementId()).containsExactly("a");
  }

  @Test
  public void shouldEndScopeIfGatewayHasNoOutgoingFlows() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId).startEvent().inclusiveGateway("inclusive").done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();

    // then
    final List<Record<ProcessInstanceRecordValue>> completedEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentsWhenTerminating() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .inclusiveGateway("inclusive")
                .sequenceFlowId("s1")
                .defaultFlow()
                .endEvent("default-end")
                .moveToLastGateway()
                .sequenceFlowId("s2")
                .conditionExpression("nonexisting_variable")
                .endEvent("non-default-end")
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();
    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.INCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }

  @Test
  public void shouldActivateTasksOnInclusiveBranches() {
    // given
    ENGINE.deployment().withXmlResource(INCLUSIVE_PROCESS).deploy();

    // when
    final long processInstanceKey1 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b,c").create();

    // then
    List<Record<ProcessInstanceRecordValue>> taskEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey1)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), INCLUSIVE_PROCESS))
            .limit(3)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(3);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2", "task3");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(2).getKey());
    assertThat(taskEvents.get(1).getKey()).isNotEqualTo(taskEvents.get(2).getKey());

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,c").create();

    // then
    taskEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey2)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), INCLUSIVE_PROCESS))
            .limit(3)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task3");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "d").create();

    // then
    taskEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey3)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), INCLUSIVE_PROCESS))
            .limit(3)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(1);
    assertThat(taskEvents).extracting(e -> e.getValue().getElementId()).containsExactly("task3");
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(INCLUSIVE_PROCESS).deploy();
    final long processInstanceKey1 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b,c").create();
    ENGINE.job().ofInstance(processInstanceKey1).withType("type1").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey1).withType("type2").complete();
    ENGINE.job().ofInstance(processInstanceKey1).withType("type3").complete();

    // then
    List<Record<ProcessInstanceRecordValue>> completedEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey1)
            .withElementType(BpmnElementType.END_EVENT)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .limit(3)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactly("end1", "end2", "end3");

    RecordingExporter.processInstanceRecords()
        .withElementId(PROCESS_ID)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b").create();
    ENGINE.job().ofInstance(processInstanceKey2).withType("type1").complete();

    ENGINE.job().ofInstance(processInstanceKey2).withType("type2").complete();

    // then
    completedEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey2)
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

    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "d").create();

    ENGINE.job().ofInstance(processInstanceKey3).withType("type3").complete();

    // then
    completedEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey3)
            .withElementType(BpmnElementType.END_EVENT)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactly("end3");

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
            .inclusiveGateway("inclusive")
            .exclusiveGateway("join")
            .endEvent("end")
            .moveToNode("inclusive")
            .connectTo("join")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

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
  public void shouldPassThroughInclusiveGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .inclusiveGateway("inclusive")
            .sequenceFlowId("flow2")
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("flow2", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteScopeOnInclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .inclusiveGateway("inclusive")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.COMPLETE_ELEMENT));
  }

  @Test
  public void shouldMergeInclusiveBranches() {
    // given
    ENGINE.deployment().withXmlResource(INCLUSIVE_JOIN_PROCESS).deploy();

    // when
    final long processInstanceKey1 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b,c").create();
    ENGINE.job().ofInstance(processInstanceKey1).withType("type1").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey1).withType("type2").complete();
    ENGINE.job().ofInstance(processInstanceKey1).withType("type3").complete();
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey1)
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
        .containsSubsequence(
            tuple("flow3", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING))
        .containsOnlyOnce(tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldOnlyTriggerGatewayWhenAllBranchesAreActivated() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .inclusiveGateway("fork")
            .exclusiveGateway("exclusiveJoin")
            .moveToLastGateway()
            .connectTo("exclusiveJoin")
            .sequenceFlowId("joinFlow1")
            .inclusiveGateway("join")
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeJobType("type"))
            .sequenceFlowId("joinFlow2")
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // waiting until we have signalled the first incoming sequence flow twice
    // => this should not trigger the gateway yet
    RecordingExporter.processInstanceRecords()
        .limit(r -> "joinFlow1".equals(r.getValue().getElementId()))
        .limit(2)
        .skip(1)
        .getFirst();

    // when
    // we complete the job
    ENGINE.job().ofInstance(processInstanceKey).withType("type").complete();

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
            .inclusiveGateway("fork")
            .inclusiveGateway("join-fork")
            .moveToNode("fork")
            .connectTo("join-fork")
            .serviceTask("task1", b -> b.zeebeJobType("type1"))
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeJobType("type2"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

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

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

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

  private static boolean isServiceTaskInProcess(
      final String activityId, final BpmnModelInstance process) {
    return process.getModelElementsByType(ServiceTask.class).stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}
