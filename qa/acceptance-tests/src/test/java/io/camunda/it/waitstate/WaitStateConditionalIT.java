/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ConditionWaitStateDetails;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies element-instance wait-state search for conditional-subscription wait states, covering
 * intermediate catch events and the removal of wait states when the condition is satisfied.
 */
@MultiDbTest
public class WaitStateConditionalIT {

  private static final String CONDITION_EXPRESSION = "=x > 10";
  private static final String VARIABLE_EVENTS = "create, update";
  private static final String CATCH_EVENT_ID = "conditional-catch";

  private static CamundaClient camundaClient;

  @Test
  void shouldExportConditionWaitStateForIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("condWaitStateIceProcess")
            .startEvent()
            .intermediateCatchEvent(CATCH_EVENT_ID)
            .condition(c -> c.condition(CONDITION_EXPRESSION).zeebeVariableEvents(VARIABLE_EVENTS))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "condWaitStateIceProcess.bpmn");

    // when — start with x=1, condition not satisfied
    final long pik =
        startProcessInstance(camundaClient, "condWaitStateIceProcess", Map.of("x", 1))
            .getProcessInstanceKey();

    // then — a CONDITION wait state is exported for the catch event
    Awaitility.await("condition wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.processInstanceKey(pik))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);

              final var item = items.getFirst();
              assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.CONDITION);
              assertThat(item.getElementId()).isEqualTo(CATCH_EVENT_ID);
              assertThat(item.getElementType())
                  .isEqualTo(WaitStateElementType.INTERMEDIATE_CATCH_EVENT);
              assertThat(item.getProcessInstanceKey()).isEqualTo(pik);
              assertThat(item.getElementInstanceKey()).isNotNull();

              assertThat(item.getDetails()).isInstanceOf(ConditionWaitStateDetails.class);
              final var details = (ConditionWaitStateDetails) item.getDetails();
              assertThat(details.getExpression()).isEqualTo(CONDITION_EXPRESSION);
              assertThat(details.getEvents()).containsExactlyInAnyOrder("create", "update");
            });
  }

  @Test
  void shouldRemoveConditionWaitStateWhenTriggered() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("condWaitStateRemovalProcess")
            .startEvent()
            .intermediateCatchEvent("cond-removal-catch")
            .condition(c -> c.condition(CONDITION_EXPRESSION).zeebeVariableEvents(VARIABLE_EVENTS))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "condWaitStateRemovalProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "condWaitStateRemovalProcess", Map.of("x", 1))
            .getProcessInstanceKey();

    Awaitility.await("condition wait state should appear before trigger")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when — update x to 42, satisfying the condition
    camundaClient.newSetVariablesCommand(pik).variables(Map.of("x", 42)).send().join();

    // then — wait state is removed once the condition fires
    Awaitility.await("condition wait state should be removed after trigger")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldNotExportConditionWaitStateForRootStartEvent() {
    // given — a process whose root start event has a conditional event definition.
    // Root conditional start subscriptions use processInstanceKey == -1 (no active instance)
    // and must not appear in the wait-state index.
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("condRootStartProcess")
            .startEvent("root-start")
            .condition(c -> c.condition("=y > 0"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "condRootStartProcess.bpmn");

    // No instance is started — the subscription is process-scoped, not instance-scoped.
    // Wait a short time and assert nothing appears for this element id.
    Awaitility.await("no CONDITION wait state for root start event")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.elementId("root-start"))
                      .send()
                      .join()
                      .items();
              // Root-level conditional start subscriptions are excluded from the wait-state index
              // because they have no real process-instance context (processInstanceKey == -1).
              final List<ElementInstanceWaitStateResult> conditionItems =
                  items.stream()
                      .filter(i -> i.getWaitStateType() == WaitStateType.CONDITION)
                      .toList();
              assertThat(conditionItems).isEmpty();
            });
  }
}
