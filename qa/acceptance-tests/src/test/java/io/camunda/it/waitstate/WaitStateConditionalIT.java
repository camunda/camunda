/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

import static io.camunda.it.util.TestHelper.cancelInstance;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
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
 * Verifies element-instance wait-state search for conditional-subscription wait states. Only
 * intermediate conditional catch events are tracked; boundary events, event-subprocess start
 * events, and root conditional start events are excluded.
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
              assertThat(item.getProcessInstanceKey()).isEqualTo(String.valueOf(pik));
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

  @Test
  void shouldUpdateConditionWaitStateElementIdWhenProcessInstanceIsMigrated() {
    // given — V1 has ICE "cond-v1", V2 has ICE "cond-v2" at the same position
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess("condMigrationV1")
            .startEvent()
            .intermediateCatchEvent("cond-v1")
            .condition(c -> c.condition("=x > 10"))
            .endEvent()
            .done();
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess("condMigrationV2")
            .startEvent()
            .intermediateCatchEvent("cond-v2")
            .condition(c -> c.condition("=x > 10"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, v1, "condMigrationV1.bpmn");
    final long v2Key =
        deployProcessAndWaitForIt(camundaClient, v2, "condMigrationV2.bpmn")
            .getProcessDefinitionKey();

    final var instance = startProcessInstance(camundaClient, "condMigrationV1", Map.of("x", 1));
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("wait state with cond-v1 should appear")
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
                    .hasSize(1)
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("cond-v1")));

    // when — migrate the process instance, remapping cond-v1 → cond-v2
    camundaClient
        .newMigrateProcessInstanceCommand(pik)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(v2Key)
                .addMappingInstruction("cond-v1", "cond-v2")
                .build())
        .send()
        .join();

    // then — wait state is updated to reflect the new element id
    Awaitility.await("wait state should be updated to cond-v2")
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
                    .hasSize(1)
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("cond-v2")));

    // cleanup
    cancelInstance(camundaClient, instance);
    Awaitility.await("wait state should be removed after cancellation")
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
  void shouldNotTrackConditionWaitStateForBoundaryConditionalEvent() {
    // given — a process with two concurrent branches:
    //   branch 1: intermediate catch event condition (should be exported as a wait state)
    //   branch 2: service task with a boundary conditional event (boundary should NOT be exported)
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("condBoundaryProcess")
            .startEvent()
            .parallelGateway("fork")
            .intermediateCatchEvent("cond-ice")
            .condition(c -> c.condition("=x > 10"))
            .parallelGateway("join")
            .moveToLastGateway()
            .serviceTask("svc-task")
            .zeebeJobType("svc")
            .connectTo("join")
            .endEvent()
            .moveToActivity("svc-task")
            .boundaryEvent("cond-boundary")
            .condition(c -> c.condition("=y > 10"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "condBoundaryProcess.bpmn");

    final var instance =
        startProcessInstance(camundaClient, "condBoundaryProcess", Map.of("x", 1, "y", 1));
    final long pik = instance.getProcessInstanceKey();

    // when — wait for exactly one CONDITION wait state (the ICE, not the boundary)
    final List<ElementInstanceWaitStateResult> items =
        Awaitility.await("exactly one CONDITION wait state should appear — the ICE only")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .until(
                () ->
                    camundaClient
                        .newElementInstanceWaitStateSearchRequest()
                        .filter(
                            f -> f.processInstanceKey(pik).waitStateType(WaitStateType.CONDITION))
                        .send()
                        .join()
                        .items(),
                list -> list.size() == 1);

    // then — only the ICE is tracked; boundary event is excluded
    assertThat(items)
        .singleElement()
        .satisfies(item -> assertThat(item.getElementId()).isEqualTo("cond-ice"));

    cancelInstance(camundaClient, instance);
  }

  @Test
  void shouldNotTrackConditionWaitStateForEventSubProcessStartEvent() {
    // given — a process with an ICE (to produce known exporter traffic) plus an event subprocess
    //   whose conditional start event should NOT produce a CONDITION wait state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("condEventSubProcess")
            .eventSubProcess(
                "cond-sub",
                sp ->
                    sp.startEvent("cond-sub-start")
                        .interrupting(false)
                        .condition("=z > 10")
                        .endEvent())
            .startEvent()
            .intermediateCatchEvent("cond-ice-traffic")
            .condition(c -> c.condition("=x > 10"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "condEventSubProcess.bpmn");

    final var instance =
        startProcessInstance(camundaClient, "condEventSubProcess", Map.of("x", 1, "z", 1));
    final long pik = instance.getProcessInstanceKey();

    // when — wait for the ICE wait state; this proves the exporter has processed all conditional
    //   subscription records including the event-subprocess start event subscription
    Awaitility.await("ICE CONDITION wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(pik)
                                        .waitStateType(WaitStateType.CONDITION))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // then — exactly one CONDITION wait state (the ICE) — the event-subprocess start is excluded
    final var allConditionWs =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(pik).waitStateType(WaitStateType.CONDITION))
            .send()
            .join()
            .items();

    assertThat(allConditionWs)
        .hasSize(1)
        .singleElement()
        .satisfies(item -> assertThat(item.getElementId()).isEqualTo("cond-ice-traffic"));

    cancelInstance(camundaClient, instance);
  }
}
