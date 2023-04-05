/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */

package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class SignalIntermediateThrowEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String SIGNAL_NAME_1 = "signalName";

  private static final String PROCESS = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldBroadcastSignalIntermediateThrowEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent("start")
            .intermediateThrowEvent("signal_throw_event")
            .signal(SIGNAL_NAME_1)
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS).create();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .filterRootScope()
            .getFirst();

    final var throwEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_THROW_EVENT)
            .getFirst();

    Assertions.assertThat(throwEvent.getValue())
        .hasProcessDefinitionKey(processInstance.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(PROCESS)
        .hasVersion(processInstance.getValue().getVersion())
        .hasProcessInstanceKey(processInstance.getKey())
        .hasBpmnEventType(BpmnEventType.SIGNAL)
        .hasElementId("signal_throw_event")
        .hasFlowScopeKey(processInstance.getKey());

    final var signalRecord = RecordingExporter.signalRecords(SignalIntent.BROADCASTED).getFirst();
    Assertions.assertThat(signalRecord.getValue()).hasSignalName(SIGNAL_NAME_1);
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent()
            .intermediateThrowEvent("signal_throw_event")
            .zeebeInputExpression("x", "y")
            .signal(SIGNAL_NAME_1)
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS)
            .withVariables(Map.of("x", 1, "y", 2))
            .create();

    // then
    final long flowScopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("signal_throw_event")
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(flowScopeKey)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .contains(tuple("y", "1"));

    final var signalRecord = RecordingExporter.signalRecords(SignalIntent.BROADCASTED).getFirst();
    Assertions.assertThat(signalRecord.getValue())
        .hasSignalName(SIGNAL_NAME_1)
        .hasVariables(Map.of("y", 1));
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent()
            .intermediateThrowEvent("signal_throw_event")
            .zeebeOutputExpression("x", "y")
            .signal(SIGNAL_NAME_1)
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS)
            .withVariables(Map.of("x", 1, "y", 2))
            .create();

    // then
    final long throwEventActivatePosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("signal_throw_event")
            .limit(1)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > throwEventActivatePosition)
                .withScopeKey(processInstanceKey)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .contains(tuple("y", "1"));

    final var signalRecord = RecordingExporter.signalRecords(SignalIntent.BROADCASTED).getFirst();
    Assertions.assertThat(signalRecord.getValue()).hasSignalName(SIGNAL_NAME_1);
  }
}
