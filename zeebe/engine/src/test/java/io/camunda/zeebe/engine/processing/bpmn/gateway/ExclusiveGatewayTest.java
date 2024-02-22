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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
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

public final class ExclusiveGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSplitOnExclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .conditionExpression("foo < 5")
            .endEvent("a")
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .conditionExpression("foo >= 5 and foo < 10")
            .endEvent("b")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s3")
            .endEvent("c")
            .done();
    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();
    final long processInstance3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 12).create();

    final List<Long> processInstanceKeys =
        Arrays.asList(processInstance1, processInstance2, processInstance3);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .valueFilter(r -> processInstanceKeys.contains(r.getProcessInstanceKey()))
                .withElementType(BpmnElementType.END_EVENT)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(
            tuple(processInstance1, "a"),
            tuple(processInstance2, "b"),
            tuple(processInstance3, "c"));
  }

  @Test
  public void shouldJoinOnExclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .conditionExpression("foo < 5")
            .exclusiveGateway("joinRequest")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();

    // then

    List<String> takenSequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance1)
            .limit(3)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s1").doesNotContain("s2");

    takenSequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance2)
            .limit(3)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s2").doesNotContain("s1");
  }

  @Test
  public void shouldSetSourceRecordPositionCorrectOnJoinXor() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .conditionExpression("foo < 5")
            .exclusiveGateway("joinRequest")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    // then
    List<Record<ProcessInstanceRecordValue>> sequenceFlows =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withProcessInstanceKey(processInstance1)
            .limit(3)
            .asList();

    List<Record<ProcessInstanceRecordValue>> gateWays =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withProcessInstanceKey(processInstance1)
            .limit(2)
            .asList();

    // assert that gateway activation originates from the same command as the sequence flow taken
    // and that the correct flow was taken
    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getSourceRecordPosition());
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getSourceRecordPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s1");

    // when
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();
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
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
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
  public void testProcessInstanceStatesWithExclusiveGateway() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .conditionExpression("foo < 5")
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .endEvent("b")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldSplitIfDefaultFlowIsDeclaredFirst() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway()
            .defaultFlow()
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .conditionExpression("foo < 5")
            .endEvent("b")
            .done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 10).create();

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
        Bpmn.createExecutableProcess(processId).startEvent().exclusiveGateway("xor").done();

    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 10).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> completedEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToProcessInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_COMPLETED),
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
                .exclusiveGateway("xor")
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
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("foo", 10).create();
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
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.EXCLUSIVE_GATEWAY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }

  @Test
  public void shouldCreateDeploymentExclusiveGatewayWithDefaultFlow() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    // when
    final BpmnModelInstance processDefinition1 =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .endEvent("end2")
            .done();

    final Record<DeploymentRecordValue> deployment1 =
        ENGINE.deployment().withXmlResource(processDefinition1).deploy();

    // then
    assertThat(deployment1.getKey())
        .describedAs("Exclusive gateway's default flow should be allowed to have no condition")
        .isNotNegative();

    // when
    final BpmnModelInstance processDefinition2 =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .endEvent("end2")
            .done();

    final Record<DeploymentRecordValue> deployment2 =
        ENGINE.deployment().withXmlResource(processDefinition2).deploy();

    // then
    assertThat(deployment2.getKey())
        .describedAs(
            "Exclusive gateway's default flow should be allowed to have a condition, but not be required")
        .isNotNegative();
  }

  @Test
  public void shouldNotEvaluateConditionOfDefaultFlow() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .conditionExpression("= contains(str,\"b\")")
            .endEvent("end2")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s3")
            .conditionExpression("= nonexisting_variable")
            .endEvent("end3")
            .done();
    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "d").create();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .describedAs("Expect that the default flow is taken")
        .satisfies(
            record ->
                assertThat(
                        record.stream().filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE))
                    .extracting(
                        r -> ((ProcessInstanceRecordValue) r.getValue()).getElementId(),
                        Record::getIntent)
                    .containsSubsequence(
                        tuple("xor", ProcessInstanceIntent.ELEMENT_COMPLETED),
                        tuple("s3", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
                        tuple("end3", ProcessInstanceIntent.ELEMENT_COMPLETED)))
        .describedAs(
            "Expect that the default flow's condition `= nonexisting_variable` is not evaluated and no incident is created")
        .satisfies(
            r ->
                assertThat(r).extracting(Record::getIntent).doesNotContain(IncidentIntent.CREATED));
  }
}
