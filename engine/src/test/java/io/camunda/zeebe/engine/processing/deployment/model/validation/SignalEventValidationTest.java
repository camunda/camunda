/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public final class SignalEventValidationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeploySignalStartEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId).startEvent("start").signal("signalName").done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Support signal start event process deployment")
        .isNotNegative();
  }

  @Test
  public void shouldRejectDeployNoneReferenceSignalEndEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .endEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, b -> b.setType("type"))
            .signalEventDefinition()
            .done();

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: signalEventDefinition_")
        .contains("ERROR: Must reference a signal");
  }

  @Test
  public void shouldRejectDeployEmptySignalEndEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId).startEvent("start").endEvent().signal("").done();

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: signal_")
        .contains("ERROR: Name must be present and not empty");
  }

  @Test
  public void shouldRejectSignalEndEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .endEvent("signal_end_event")
            .signal("signalName")
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.xml': - Element: signal_end_event
                - ERROR: Elements of type signal end event are currently not supported
            """);
  }

  @Test
  public void shouldRejectDeployEmptySignalThrowEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .intermediateThrowEvent()
            .signal("")
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: signal_")
        .contains("ERROR: Name must be present and not empty");
  }

  @Test
  public void shouldRejectSignalThrowEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .intermediateThrowEvent("signal_throw_event")
            .signal("signalName")
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.xml': - Element: signal_throw_event
                - ERROR: Elements of type signal throw event are currently not supported
            """);
  }

  @Test
  public void shouldDeployMultipleSignalStartEvents() {
    // given
    final BpmnModelInstance processDefinition = processWithMultipleSignalStartEvents();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Support multiple signal star event process deployment")
        .isNotNegative();
  }

  @Test
  public void shouldRejectSignalBoundaryEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .manualTask()
            .boundaryEvent("signal_boundary_event", b -> b.signal(m -> m.name("signalName")))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.xml': - Element: signal_boundary_event
                - ERROR: Elements of type signal boundary event are currently not supported
            """);
  }

  @Ignore("Should be re-enabled when signal boundary events are supported")
  @Test
  public void shouldDeploySignalStartAndMultipleBoundaryEvents() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .signal("start-signal")
            .manualTask("task")
            .boundaryEvent("boundary-1", b -> b.signal(m -> m.name("signalName1")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(m -> m.name("signalName2")))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs(
            "Support signal start event and multiple signal boundary event process deployment")
        .isNotNegative();
  }

  @Ignore(
      "Should be re-enabled when signal event-subprocess & signal boundary events are supported")
  @Test
  public void shouldDeployEventSubProcessWithMultipleSignalEvents() {
    // given
    final BpmnModelInstance processDefinition =
        getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent();

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs(
            "Support event sub process with signal start event and boundary event process deployment")
        .isNotNegative();
  }

  @Test
  public void shouldDeploySignalIntermediateCatchEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent("foo")
            .signal("signalName")
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Support signal intermediate catch event process deployment")
        .isNotNegative();
  }

  @Ignore("Should be re-enabled when signal boundary events are supported")
  @Test
  public void shouldDeploySignalStartAndBoundaryEventEvenWithSameSignal() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .signal(m -> m.id("start-signal").name("signalName"))
            .manualTask()
            .boundaryEvent("boundary-1", b -> b.signal(m -> m.name("signalName")))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs(
            "Support signal start and boundary event even with the same signal name process deployment")
        .isNotNegative();
  }

  @Test
  public void shouldRejectDeployMultipleStartEventsWithSameSignal() {
    // given
    final BpmnModelInstance processDefinition = getProcessWithMultipleStartEventsWithSameSignal();

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: process")
        .contains(
            "ERROR: Multiple signal event definitions with the same name 'signalName' are not allowed.");
  }

  private static BpmnModelInstance
      getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent(
            "event_sub_start",
            a -> a.signal(m -> m.id("event_sub_start_signal").name("signalName1")))
        .subProcess(
            "embedded",
            s ->
                s.boundaryEvent(
                    "boundary-msg", b -> b.signal("signalName2").endEvent("boundary-end")))
        .embeddedSubProcess()
        .startEvent("embedded_sub_start")
        .endEvent("embedded_sub_end")
        .moveToNode("embedded")
        .endEvent("event_sub_end");
    return builder.startEvent("start").endEvent("end").done();
  }

  public static BpmnModelInstance processWithMultipleSignalStartEvents() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    process.startEvent().signal("s1").endEvent();
    process.startEvent().signal("s2").endEvent();
    process.startEvent().signal(s -> s.nameExpression("=\"signal_static_expression\"")).endEvent();
    return process.startEvent().signal("s3").endEvent().done();
  }

  private static BpmnModelInstance getProcessWithMultipleStartEventsWithSameSignal() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    final String signalName = "signalName";
    process.startEvent("start1").signal(m -> m.id("start-signal").name(signalName)).endEvent();
    process.startEvent("start2").signal(signalName).endEvent();
    return process.done();
  }
}
