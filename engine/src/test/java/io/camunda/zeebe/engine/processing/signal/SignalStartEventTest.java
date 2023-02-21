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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class SignalStartEventTest {

  private static final String SIGNAL_NAME_1 = "a";
  private static final String SIGNAL_NAME_2 = "b";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldBroadcastSignalToStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("wf")
            .startEvent("start")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).broadcast();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .filterRootScope()
            .getFirst();

    final var startEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue())
        .hasProcessDefinitionKey(processInstance.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(processInstance.getValue().getBpmnProcessId())
        .hasVersion(processInstance.getValue().getVersion())
        .hasProcessInstanceKey(processInstance.getKey())
        .hasBpmnEventType(BpmnEventType.SIGNAL)
        .hasElementId("start")
        .hasFlowScopeKey(processInstance.getKey());
  }

  @Test
  public void shouldCreateNewInstanceWithSignalVariables() {
    // given
    final var process =
        Bpmn.createExecutableProcess("wf")
            .startEvent("start")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 1, "y", 2)).broadcast();

    // then
    assertThat(RecordingExporter.variableRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .hasSize(2)
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final var process =
        Bpmn.createExecutableProcess("wf")
            .startEvent("start")
            .zeebeOutputExpression("x", "y")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 1, "y", 2)).broadcast();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filterRootScope()
            .getFirst();

    assertThat(RecordingExporter.variableRecords().withScopeKey(processInstance.getKey()).limit(1))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .contains(tuple("y", "1"));
  }

  @Test
  public void shouldCreateInstanceOfLatestVersion() {
    // given
    final var process1 =
        Bpmn.createExecutableProcess("wf").startEvent("v1").signal(SIGNAL_NAME_1).endEvent().done();

    final var process2 =
        Bpmn.createExecutableProcess("wf").startEvent("v2").signal(SIGNAL_NAME_1).endEvent().done();
    engine.deployment().withXmlResource(process1).deploy();

    engine.deployment().withXmlResource(process2).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).broadcast();

    // then
    final var startEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue()).hasElementId("v2");
  }

  @Test
  public void shouldCreateNewInstanceWithMultipleStartEvents() {
    // given
    final var process = Bpmn.createExecutableProcess("wf");
    process.startEvent().signal(SIGNAL_NAME_1).endEvent("end");
    process.startEvent().signal(SIGNAL_NAME_2).connectTo("end");

    engine.deployment().withXmlResource(process.done()).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 1)).broadcast();

    engine.signal().withSignalName(SIGNAL_NAME_2).withVariables(Map.of("x", 2)).broadcast();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .containsExactly("1", "2");
  }

  @Test
  public void shouldTriggerOnlySignalStartEvent() {
    // given
    final var process = Bpmn.createExecutableProcess("process");
    process.startEvent("none-start").endEvent();
    process.startEvent("message-start").message("test").endEvent();
    process.startEvent("signal-start").signal(SIGNAL_NAME_1).endEvent();
    process.startEvent("timer-start").timerWithCycle("R/PT1H").endEvent();

    engine.deployment().withXmlResource(process.done()).deploy();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).broadcast();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("signal-start");
  }

  @Test
  public void shouldCreateMultipleInstances() {
    // given
    final var process =
        Bpmn.createExecutableProcess("wf")
            .startEvent("start")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 1)).broadcast();

    // when
    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 2)).broadcast();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .containsExactly("1", "2");
  }

  @Test
  public void shouldCreateMultipleInstancesForDifferentResources() {
    // given
    final var process1 =
        Bpmn.createExecutableProcess("wf_1")
            .startEvent("start")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    final var process2 =
        Bpmn.createExecutableProcess("wf_2")
            .startEvent("start")
            .signal(SIGNAL_NAME_1)
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process1).withXmlResource(process2).deploy();

    engine.signal().withSignalName(SIGNAL_NAME_1).withVariables(Map.of("x", 1)).broadcast();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.START_EVENT)
                .limit(2))
        .extracting(r -> r.getValue().getBpmnProcessId())
        .containsExactly("wf_1", "wf_2");
  }

  @Test
  public void shouldNotCreateInstanceDirectly() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("SignalStartEventOnly")
                .startEvent("signal-start")
                .signal("start")
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId("SignalStartEventOnly").expectRejection().create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withBpmnProcessId("SignalStartEventOnly")
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to create instance of process with none start event, but there is no such event");
  }
}
