/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractBoundaryEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class EscalationEventValidationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<SubProcessBuilder> builder) {
    return Bpmn.createExecutableProcess().startEvent().subProcess("sp", builder).endEvent().done();
  }

  @Test
  public void shouldDeployProcessModelWithEscalationThrowEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .intermediateThrowEvent()
            .escalation("escalationCode")
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> result =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getValue().getProcessesMetadata()).hasSize(1);
    final ProcessMetadataValue deployedProcess = result.getValue().getProcessesMetadata().get(0);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
  }

  @Test
  public void shouldDeployProcessModelWithEscalationStartEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .eventSubProcess(
                "sub", s -> s.startEvent("start-1").escalation("escalationCode").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> result =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getValue().getProcessesMetadata()).hasSize(1);
    final ProcessMetadataValue deployedProcess = result.getValue().getProcessesMetadata().get(0);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
  }

  @Test
  public void shouldDeployProcessModelWithEscalationEndEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .endEvent("end", builder -> builder.escalation("escalationCode"))
            .done();

    // when
    final Record<DeploymentRecordValue> result =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getValue().getProcessesMetadata()).hasSize(1);
    final ProcessMetadataValue deployedProcess = result.getValue().getProcessesMetadata().get(0);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
  }

  @Test
  public void shouldDeployProcessModelWithEscalationBoundaryEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .callActivity("call", builder -> builder.zeebeProcessId("sub"))
            .boundaryEvent("catch", builder -> builder.escalation("escalationCode"))
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> result =
        ENGINE.deployment().withXmlResource(processDefinition).deploy();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getValue().getProcessesMetadata()).hasSize(1);
    final ProcessMetadataValue deployedProcess = result.getValue().getProcessesMetadata().get(0);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
  }

  @Test
  public void
      shouldRejectDeploymentIfEscalationBoundaryEventIsNotAttachedToChildProcessOrCallActivity() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("task", builder -> builder.zeebeJobType("type"))
            .boundaryEvent("catch", AbstractBoundaryEventBuilder::escalation)
            .endEvent()
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "An escalation boundary event should only be attached to a subprocess, or a call activity.");
  }

  @Test
  public void shouldRejectDeploymentIfMissingEscalationRefOnIntermediateThrowingEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .intermediateThrowEvent()
            .escalationEventDefinition("escalation")
            .escalationEventDefinitionDone()
            .endEvent()
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason()).contains("Must reference an escalation");
  }

  @Test
  public void shouldRejectDeploymentIfEscalationThrowEventWithoutEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .intermediateThrowEvent()
            .escalationEventDefinition("escalation")
            .escalationCode("")
            .escalationEventDefinitionDone()
            .endEvent()
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains("ERROR: EscalationCode must be present and not empty");
  }

  @Test
  public void shouldRejectDeploymentIfMultipleEscalationBoundaryEventsWithoutEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation().endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation().endEvent())
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "The same scope can not contain more than one escalation catch event without escalation code. An escalation catch event without escalation code catches all escalations.");
  }

  @Test
  public void shouldRejectDeploymentIfMultipleEscalationBoundaryEventsWithSameEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation("escalationCode").endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation("escalationCode").endEvent())
            .endEvent()
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "Multiple escalation catch events with the same escalation code 'escalationCode' are not supported on the same scope.");
  }

  @Test
  public void shouldRejectDeploymentIfMissingEscalationRefOnEscalationEndEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .endEvent("end", AbstractThrowEventBuilder::escalationEventDefinition)
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains("ERROR: Must reference an escalation");
  }

  @Test
  public void shouldRejectDeploymentIfEscalationEndEventWithoutEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .endEvent("end", builder -> builder.escalation(""))
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains("ERROR: EscalationCode must be present and not empty");
  }

  @Test
  public void shouldRejectDeploymentIfEscalationEndEventWithSameEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess()
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation("escalationCode").endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation("escalationCode").endEvent())
            .done();
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "Multiple escalation catch events with the same escalation code 'escalationCode' are not supported on the same scope.");
  }

  @Test
  public void shouldRejectDeploymentIfMultipleEscalationEventSubprocessWithoutEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        process(
            sp -> {
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("")
                  .endEvent();
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("")
                  .endEvent();
              sp.embeddedSubProcess().startEvent().endEvent();
            });

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "The same scope can not contain more than one escalation catch event without escalation code. An escalation catch event without escalation code catches all escalations.");
  }

  @Test
  public void shouldRejectDeploymentIfMultipleEscalationEventSubprocessWithSameEscalationCode() {
    // given
    final BpmnModelInstance processDefinition =
        process(
            sp -> {
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("escalation")
                  .endEvent();
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("escalation")
                  .endEvent();
              sp.embeddedSubProcess().startEvent().endEvent();
            });

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "Multiple escalation catch events with the same escalation code 'escalation' are not supported on the same scope.");
  }
}
