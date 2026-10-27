/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient.ProcessInstanceModificationClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ModifyProcessInstanceUnsupportedElementsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final Scenario scenario;

  public ModifyProcessInstanceUnsupportedElementsTest(final Scenario scenario) {
    this.scenario = scenario;
  }

  @Parameters(name = "{0}")
  public static Collection<Object> scenarios() {
    return List.of(
        new Scenario(
            "Activate element that belongs to an event-based gateway",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .eventBasedGateway("event_based_gateway")
                .intermediateCatchEvent(
                    "gateway_timer_event", t -> t.timerWithDuration(Duration.ofHours(1)))
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "gateway_message_event",
                    m -> m.message(msg -> msg.name("msg").zeebeCorrelationKeyExpression("\"key\"")))
                .endEvent()
                .done(),
            instructionBuilder -> instructionBuilder.activateElement("gateway_timer_event"),
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                "'gateway_timer_event'",
                "The activation of events belonging to an event-based gateway is not supported")),
        new Scenario(
            "Activate start events",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event_sub_process",
                    e ->
                        e.startEvent(
                            "event_sub_start", s -> s.timerWithDuration(Duration.ofHours(1))))
                .startEvent("root_start")
                .userTask("A")
                .subProcess(
                    "embedded_sub_process",
                    e -> e.embeddedSubProcess().startEvent("embedded_sub_start").endEvent())
                .done(),
            instructionBuilder ->
                instructionBuilder
                    .activateElement("root_start")
                    .activateElement("event_sub_start")
                    .activateElement("embedded_sub_start")
                    .activateElement("A"),
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                "'root_start', 'event_sub_start', 'embedded_sub_start'",
                "The activation of elements with type 'START_EVENT' is not supported."
                    + " Supported element types are:")),
        new Scenario(
            "Activate sequence flows",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .sequenceFlowId("flow_to_A")
                .userTask("A")
                .sequenceFlowId("flow_from_A")
                .endEvent()
                .done(),
            instructionBuilder ->
                instructionBuilder
                    .activateElement("flow_to_A")
                    .activateElement("flow_from_A")
                    .activateElement("A"),
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                "'flow_to_A', 'flow_from_A'",
                "The activation of elements with type 'SEQUENCE_FLOW' is not supported."
                    + " Supported element types are:")),
        new Scenario(
            "Activate boundary events",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .boundaryEvent(
                    "timer_boundary_on_user_task", t -> t.timerWithDuration(Duration.ofHours(1)))
                .endEvent()
                .moveToActivity("A")
                .subProcess("subprocess", s -> s.embeddedSubProcess().startEvent().done())
                .boundaryEvent(
                    "non_interrupting_msg_boundary_on_subprocess",
                    m ->
                        m.cancelActivity(false)
                            .message(msg -> msg.name("msg").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .moveToActivity("subprocess")
                .endEvent()
                .done(),
            instructionBuilder ->
                instructionBuilder
                    .activateElement("timer_boundary_on_user_task")
                    .activateElement("non_interrupting_msg_boundary_on_subprocess")
                    .activateElement("A"),
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                "'timer_boundary_on_user_task', 'non_interrupting_msg_boundary_on_subprocess'",
                "The activation of elements with type 'BOUNDARY_EVENT' is not supported."
                    + " Supported element types are:")),
        new Scenario(
            "Activate a combination of unsupported elements",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("root_start")
                .userTask("A")
                .boundaryEvent(
                    "timer_boundary_on_user_task", t -> t.timerWithDuration(Duration.ofHours(1)))
                .endEvent()
                .moveToActivity("A")
                .sequenceFlowId("flow_from_A")
                .endEvent()
                .done(),
            instructionBuilder ->
                instructionBuilder
                    .activateElement("root_start")
                    .activateElement("flow_from_A")
                    .activateElement("timer_boundary_on_user_task")
                    .activateElement("A"),
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                "'root_start', 'flow_from_A', 'timer_boundary_on_user_task'",
                "The activation of elements with type 'START_EVENT', 'SEQUENCE_FLOW',"
                    + " 'BOUNDARY_EVENT' is not supported. Supported element types are:")));
  }

  @Test
  public void shouldRejectCommandForScenario() {
    // given
    ENGINE.deployment().withXmlResource(scenario.model()).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var rejection =
        scenario
            .instructionBuilder()
            .apply(ENGINE.processInstance().withInstanceKey(processInstanceKey).modification())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection).hasRejectionType(scenario.expectation.type());
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                "Expected to modify instance of process '%s' but it contains one or more"
                    + " activate instructions for elements that are unsupported: %s. %s",
                PROCESS_ID, scenario.expectation().elementIds(), scenario.expectation().reason()));
  }

  private record Scenario(
      String name,
      BpmnModelInstance model,
      UnaryOperator<ProcessInstanceModificationClient> instructionBuilder,
      Rejection expectation) {

    @Override
    public String toString() {
      return name;
    }
  }

  private record Rejection(RejectionType type, String elementIds, String reason) {}
}
