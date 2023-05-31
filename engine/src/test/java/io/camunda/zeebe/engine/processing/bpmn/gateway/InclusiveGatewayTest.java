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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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
          .manualTask("task1")
          .endEvent("end1")
          .moveToNode("inclusive")
          .sequenceFlowId("s2")
          .conditionExpression("= contains(str,\"b\")")
          .manualTask("task2")
          .endEvent("end2")
          .moveToLastInclusiveGateway()
          .defaultFlow()
          .sequenceFlowId("s3")
          .conditionExpression("= contains(str,\"c\")")
          .manualTask("task3")
          .endEvent("end3")
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
            .conditionExpression("= true")
            .endEvent("end3")
            .done();
    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance1 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance1)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("when s1's condition is true,then s1 sequence flow is taken")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance1, "end1"))
        .doesNotContain(tuple(processInstance1, "end3"));

    // when
    final long processInstance2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance2)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("when s2's condition is true,then s2 sequence flow is taken")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance2, "end2"))
        .doesNotContain(tuple(processInstance1, "end3"));

    // when
    final long processInstance3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance3)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("if no conditions are fulfilled, then the default flow is taken")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance3, "end3"));

    // when
    final long processInstance5 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance5)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(Record::getValue)
        .describedAs(
            "when s1 and s2's conditions are true,then s1 and s2's sequence flows are taken")
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance5, "end1"), tuple(processInstance5, "end2"))
        .doesNotContain(tuple(processInstance1, "end3"));

    // when
    final long processInstance6 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance6)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("when s2's condition is true,then s2 sequence flow is taken, s3 is ignored")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance6, "end2"))
        .doesNotContain(tuple(processInstance1, "end3"));

    // when
    final long processInstance7 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance7)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("when s1's condition is true,then s1 sequence flow is taken, s3 is ignored")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance7, "end1"))
        .doesNotContain(tuple(processInstance1, "end3"));

    // when
    final long processInstance8 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance8)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "when all conditions are true,then all sequence flows are taken,the default sequence flow is ignored")
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(tuple(processInstance8, "end1"), tuple(processInstance8, "end2"))
        .doesNotContain(tuple(processInstance1, "end3"));
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
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
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

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "b").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey2)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
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

    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "c").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey3)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
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

    // when
    final long processInstanceKey4 =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();

    // then
    processEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey4)
            .skipUntil(r -> r.getValue().getElementId().equals("inclusive"))
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
  public void shouldActivateTasksOnInclusiveBranches() {
    // given
    ENGINE.deployment().withXmlResource(INCLUSIVE_PROCESS).deploy();

    // when
    final long processInstanceKey1 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey1)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.MANUAL_TASK))
        .extracting(record -> record.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2");

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey2)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.MANUAL_TASK))
        .extracting(record -> record.getValue().getElementId())
        .containsExactly("task1");

    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "d").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey3)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.MANUAL_TASK))
        .extracting(record -> record.getValue().getElementId())
        .containsExactly("task3");
  }

  @Test
  public void shouldCompleteScopeWhenForkingPathsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(INCLUSIVE_PROCESS).deploy();

    // when
    final long processInstanceKey1 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b,c").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey1)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.END_EVENT))
        .extracting(record -> record.getValue().getElementId())
        .containsExactlyInAnyOrder("end1", "end2");

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey1)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expect to complete the process instance")
        .isTrue();

    // when
    final long processInstanceKey2 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "a,b").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey2)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.END_EVENT))
        .extracting(record -> record.getValue().getElementId())
        .containsExactlyInAnyOrder("end1", "end2");

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey2)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expect to complete the process instance")
        .isTrue();

    // when
    final long processInstanceKey3 =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("str", "d").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey3)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.END_EVENT))
        .extracting(record -> record.getValue().getElementId())
        .containsExactly("end3");

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey3)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expect to complete the process instance")
        .isTrue();
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
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
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("end", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
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
            .asList();

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple("inclusive", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.COMPLETE_ELEMENT));
  }

  @Test
  public void shouldCreateDeploymentInclusiveGatewayWithDefaultFlow() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    // when
    final BpmnModelInstance processDefinition1 =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("inclusive")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastInclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .endEvent("end2")
            .done();

    final Record<DeploymentRecordValue> deployment1 =
        ENGINE.deployment().withXmlResource(processDefinition1).deploy();

    // then
    assertThat(deployment1.getKey())
        .describedAs("Inclusive gateway's default flow should be allowed to have no condition")
        .isNotNegative();

    // when
    final BpmnModelInstance processDefinition2 =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .inclusiveGateway("inclusive")
            .sequenceFlowId("s1")
            .conditionExpression("= contains(str,\"a\")")
            .endEvent("end1")
            .moveToLastInclusiveGateway()
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
            "Inclusive gateway's default flow should be allowed to have a condition, but not be required")
        .isNotNegative();
  }

  @Test
  public void shouldNotEvaluateConditionOfDefaultFlow() {
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
            .conditionExpression("= true")
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
                        tuple("inclusive", ProcessInstanceIntent.ELEMENT_COMPLETED),
                        tuple("s3", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
                        tuple("end3", ProcessInstanceIntent.ELEMENT_COMPLETED)))
        .describedAs("Expect that the default flow's condition is not evaluated")
        .satisfies(
            r ->
                assertThat(r).extracting(Record::getIntent).doesNotContain(IncidentIntent.CREATED));
  }

  @Test
  public void shouldEvaluateConditionWithoutDefaultFlow() {
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
            .sequenceFlowId("s3")
            .conditionExpression("= true")
            .endEvent("end3")
            .done();
    ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstance =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("str", "a,b").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.END_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(Record::getValue)
        .describedAs("when all conditions are true,then all sequence flows are taken")
        .extracting(v -> tuple(v.getProcessInstanceKey(), v.getElementId()))
        .contains(
            tuple(processInstance, "end1"),
            tuple(processInstance, "end2"),
            tuple(processInstance, "end3"));
  }

  private static boolean isServiceTaskInProcess(
      final String activityId, final BpmnModelInstance process) {
    return process.getModelElementsByType(ServiceTask.class).stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}
