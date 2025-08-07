/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldActivateProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final ProcessInstanceRecordValue value = process.getValue();
    Assertions.assertThat(value)
        .hasElementId("process")
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasFlowScopeKey(-1)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(value.getCallingElementPath()).isEmpty();
    assertThat(value.getElementInstancePath()).hasSize(1);
    final List<Long> elementInstances = value.getElementInstancePath().get(0);
    assertThat(elementInstances).containsExactly(processInstanceKey);
  }

  @Test
  public void shouldContainTreePathOnProcessActivating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final ProcessInstanceRecordValue value = process.getValue();
    assertThat(value.getCallingElementPath()).isEmpty();
    assertThat(value.getElementInstancePath()).hasSize(1);
    final List<Long> elementInstances = value.getElementInstancePath().get(0);
    assertThat(elementInstances).containsExactly(processInstanceKey);
    assertThat(value.getProcessDefinitionPath()).containsExactly(value.getProcessDefinitionKey());
  }

  @Test
  public void shouldNotContainTreePathOnProcessActivated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final ProcessInstanceRecordValue value = process.getValue();
    assertThat(value.getCallingElementPath()).isEmpty();
    assertThat(value.getElementInstancePath()).isEmpty();
    assertThat(value.getProcessDefinitionPath()).isEmpty();
  }

  @Test
  public void shouldContainTreePathOnStartEventActivating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    final ProcessInstanceRecordValue value = process.getValue();
    assertThat(value.getCallingElementPath()).isEmpty();
    assertThat(value.getElementInstancePath()).hasSize(1);
    final List<Long> elementInstances = value.getElementInstancePath().get(0);
    assertThat(elementInstances).containsExactly(processInstanceKey, process.getKey());
    assertThat(value.getProcessDefinitionPath()).containsExactly(value.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateProcessInstanceWithVariables() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Map.of("x", 1, "y", 2))
            .create();

    // then
    assertThat(
            RecordingExporter.variableRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getValue)
        .allMatch(v -> v.getScopeKey() == processInstanceKey)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldCreateProcessInstanceWithTags() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("my-task", t -> t.zeebeJobType("my-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withTags("businessKey: 1234", "priority: high")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final ProcessInstanceRecord value = (ProcessInstanceRecord) process.getValue();
    Assertions.assertThat(value)
        .hasElementId("process")
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasFlowScopeKey(-1)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final var tags = value.getTags();
    assertThat(tags).containsExactlyInAnyOrder("businessKey: 1234", "priority: high");
  }

  @Test
  public void shouldActivateNoneStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process").startEvent("start").endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final Record<ProcessInstanceRecordValue> startEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue())
        .hasElementId("start")
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateOnlyNoneStartEvent() {
    // given
    final var processBuilder = Bpmn.createExecutableProcess("process");
    processBuilder.startEvent("none-start").endEvent();
    processBuilder.startEvent("timer-start").timerWithCycle("R/PT1H").endEvent();
    processBuilder.startEvent("message-start").message("start").endEvent();
    processBuilder.startEvent("signal-start").signal("signal").endEvent();

    ENGINE.deployment().withXmlResource(processBuilder.done()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("none-start");
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sequenceFlowId("flow")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT));

    final Record<ProcessInstanceRecordValue> sequenceFlow =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SEQUENCE_FLOW)
            .getFirst();

    Assertions.assertThat(sequenceFlow.getValue())
        .hasElementId("flow")
        .hasBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCompleteUndefinedTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process").startEvent().task().endEvent().done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateProcessInstanceForDefaultTenant() {
    // given
    final String processId = "process";
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    Assertions.assertThat(process.getValue()).hasBpmnProcessId(processId).hasTenantId(tenantId);
  }

  @Test
  public void shouldCreateProcessInstanceForCustomTenant() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String tenantId = "foo";
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    Assertions.assertThat(process.getValue()).hasBpmnProcessId(processId).hasTenantId(tenantId);
  }

  // Regression test for https://github.com/camunda/camunda/issues/10536
  @Test
  public void shouldActivateNoneStartEventWhenEventTriggerIsAvailableInState() {
    // given
    final var processId = helper.getBpmnProcessId();
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent("noneStart")
            .endEvent()
            .moveToProcess(processId)
            .startEvent("msgStart")
            .message("msg")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstanceCreation(
                ProcessInstanceCreationIntent.CREATE,
                new ProcessInstanceCreationRecord().setBpmnProcessId(processId).setVersion(1)),
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord().setName("msg").setCorrelationKey("").setTimeToLive(0)));

    final var processInstanceKey =
        RecordingExporter.processInstanceCreationRecords()
            .withBpmnProcessId(processId)
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(
                BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContain(
            tuple(BpmnElementType.START_EVENT, "msgStart", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(
                BpmnElementType.START_EVENT, "msgStart", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.START_EVENT, "msgStart", ProcessInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withBpmnProcessId(processId)
                .filter(r -> r.getValue().getProcessInstanceKey() != processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, processId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.START_EVENT, "msgStart", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, "msgStart", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContain(
            tuple(BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(
                BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.START_EVENT, "noneStart", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}
