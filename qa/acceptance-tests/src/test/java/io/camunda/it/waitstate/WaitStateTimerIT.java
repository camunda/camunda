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
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.TimerWaitStateDetails;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies timer wait-state export: only intermediate catch event timers are tracked, boundary
 * timers and timer start events are excluded, migration updates the stored element id, and the
 * wait-state entry is removed when a timer fires.
 */
@MultiDbTest
public class WaitStateTimerIT {

  @MultiDbTestApplication
  static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication().withProperty("zeebe.clock.controlled", "true");

  private static CamundaClient camundaClient;

  @Test
  void shouldExportOnlyIntermediateCatchEventTimerAndNotBoundaryTimer() {
    // given — a process with two concurrent timer branches:
    //   branch 1: intermediate catch event timer (should be exported as a wait state)
    //   branch 2: service task with a boundary timer (boundary should NOT be exported)
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateTimerProcess")
            .startEvent()
            .parallelGateway("fork")
            .intermediateCatchEvent("ice-timer", e -> e.timerWithDuration("PT1H"))
            .parallelGateway("join")
            .moveToLastGateway()
            .serviceTask("svc-task")
            .zeebeJobType("svc")
            .connectTo("join")
            .endEvent()
            // boundary
            .moveToActivity("svc-task")
            .boundaryEvent("boundary-timer")
            .timerWithDuration("PT1H")
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, "waitStateTimerProcess.bpmn");

    final var instance = startProcessInstance(camundaClient, "waitStateTimerProcess");
    final long pik = instance.getProcessInstanceKey();

    // when — wait for exactly one TIMER wait state for this instance
    // when / then — verify all fields of the single TIMER wait state
    final List<ElementInstanceWaitStateResult> items =
        Awaitility.await("exactly one TIMER wait state should appear for the ICE")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .until(
                () ->
                    camundaClient
                        .newElementInstanceWaitStateSearchRequest()
                        .filter(f -> f.processInstanceKey(pik).waitStateType(WaitStateType.TIMER))
                        .send()
                        .join()
                        .items(),
                list -> list.size() == 1);

    final var item = items.getFirst();

    assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.TIMER);
    assertThat(item.getElementType()).isEqualTo(WaitStateElementType.INTERMEDIATE_CATCH_EVENT);
    assertThat(item.getElementId()).isEqualTo("ice-timer");
    assertThat(item.getProcessInstanceKey()).isEqualTo(String.valueOf(pik));
    assertThat(item.getRootProcessInstanceKey()).isEqualTo(String.valueOf(pik));
    assertThat(item.getBpmnProcessId()).isEqualTo("waitStateTimerProcess");

    assertThat(item.getDetails()).isInstanceOf(TimerWaitStateDetails.class);
    final var details = (TimerWaitStateDetails) item.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.TIMER);
    assertThat(details.getDueDate()).isPositive();
    assertThat(details.getRepetitions()).isEqualTo(1); // single-fire timer has 1 remaining

    // cleanup
    cancelInstance(camundaClient, instance);
  }

  @Test
  void shouldNotTrackTimerStartEventSubscriptions() {
    // given — a process whose only timer is the start event; timer subscriptions with
    //   processInstanceKey == -1 must never produce a wait-state entry
    final BpmnModelInstance timerStartProcess =
        Bpmn.createExecutableProcess("timerStartOnlyProcess")
            .startEvent("timer-start")
            .timerWithDuration("PT1H")
            .endEvent()
            .done();

    // Deploy a second process with an ICE to generate known export traffic so we can verify
    // the exporter has processed the timer subscription records before asserting.
    final BpmnModelInstance iceProcess =
        Bpmn.createExecutableProcess("timerStartTrafficProcess")
            .startEvent()
            .intermediateCatchEvent("ice-marker", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, timerStartProcess, "timerStartOnlyProcess.bpmn");
    deployProcessAndWaitForIt(camundaClient, iceProcess, "timerStartTrafficProcess.bpmn");

    final var trafficInstance = startProcessInstance(camundaClient, "timerStartTrafficProcess");
    final long pik = trafficInstance.getProcessInstanceKey();

    // when — wait for the ICE wait state; this proves the exporter has caught up on all
    //   timer records, including the start-event subscription for timerStartOnlyProcess
    Awaitility.await("ICE wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(
                                f -> f.processInstanceKey(pik).waitStateType(WaitStateType.TIMER))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // then — ALL timer wait states in the system must have a positive processInstanceKey;
    //   timer start-event subscriptions (processInstanceKey == -1) must not appear
    final var allTimerWaitStates =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.TIMER))
            .send()
            .join()
            .items();

    assertThat(allTimerWaitStates)
        .hasSize(1)
        .allSatisfy(ws -> assertThat(Long.parseLong(ws.getProcessInstanceKey())).isPositive());

    // cleanup
    cancelInstance(camundaClient, trafficInstance);
  }

  @Test
  void shouldUpdateWaitStateWhenTimerElementIsMigrated() {
    // given — V1 has ICE "ice-v1", V2 has ICE "ice-v2" at the same position
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess("timerMigrationV1")
            .startEvent()
            .intermediateCatchEvent("ice-v1", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess("timerMigrationV2")
            .startEvent()
            .intermediateCatchEvent("ice-v2", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, v1, "timerMigrationV1.bpmn");
    final long v2Key =
        deployProcessAndWaitForIt(camundaClient, v2, "timerMigrationV2.bpmn")
            .getProcessDefinitionKey();

    final var instance = startProcessInstance(camundaClient, "timerMigrationV1");
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("wait state with ice-v1 should appear")
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
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("ice-v1")));

    // when — migrate the process instance, remapping ice-v1 → ice-v2
    camundaClient
        .newMigrateProcessInstanceCommand(pik)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(v2Key)
                .addMappingInstruction("ice-v1", "ice-v2")
                .build())
        .send()
        .join();

    // then — wait state is updated to reflect the new element id
    Awaitility.await("wait state should be updated to ice-v2")
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
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("ice-v2")));

    // cleanup — cancelInstance also waits for process termination
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
  void shouldReplaceWaitStateWhenFirstOfTwoSequentialTimersFires() {
    // given — two sequential ICE timers; when the first fires, the wait state for it must be
    //   removed and a new one for the second timer must appear (ICE timers do not support
    //   timeCycle — the "remove old / add new" lifecycle is verified via sequential timers)
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("timerSequentialProcess")
            .startEvent()
            .intermediateCatchEvent("ice-first", e -> e.timerWithDuration("PT5S"))
            .intermediateCatchEvent("ice-second", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, "timerSequentialProcess.bpmn");

    final var instance = startProcessInstance(camundaClient, "timerSequentialProcess");
    final long pik = instance.getProcessInstanceKey();

    // wait for the first timer wait state
    Awaitility.await("wait state for ice-first should appear")
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
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("ice-first")));

    // when — advance clock past the 5 s duration so the first timer fires
    ActorClockActuator.of(CAMUNDA.actuatorUri("clock").toString())
        .addTime(new AddTimeRequest(Duration.ofSeconds(10).toMillis()));

    // then — wait state for ice-first is removed; wait state for ice-second appears
    Awaitility.await("wait state should transition from ice-first to ice-second")
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
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("ice-second")));

    // cleanup
    cancelInstance(camundaClient, instance);
    Awaitility.await("wait state for ice-second should be removed after cancellation")
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
  void shouldRemoveWaitStateWhenProcessInstanceIsCancelled() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("timerCancelProcess")
            .startEvent()
            .intermediateCatchEvent("ice-cancel", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, "timerCancelProcess.bpmn");

    final var instance = startProcessInstance(camundaClient, "timerCancelProcess");
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("TIMER wait state should appear")
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

    // when — cancel the process instance; the TIMER_CANCELED record must remove the wait state
    cancelInstance(camundaClient, instance);

    // then
    Awaitility.await("TIMER wait state should be removed after cancellation")
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
  void shouldRemoveWaitStateWhenSingleFireTimerFires() {
    // given — an ICE with a single-fire 5 s timer
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("timerSingleFireProcess")
            .startEvent()
            .intermediateCatchEvent("single-ice", e -> e.timerWithDuration("PT5S"))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, "timerSingleFireProcess.bpmn");

    final var instance = startProcessInstance(camundaClient, "timerSingleFireProcess");
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("TIMER wait state should appear")
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

    // when — advance clock past the 5 s duration; timer fires, process completes
    ActorClockActuator.of(CAMUNDA.actuatorUri("clock").toString())
        .addTime(new AddTimeRequest(Duration.ofSeconds(10).toMillis()));

    // then — wait state is removed once the timer is triggered
    Awaitility.await("TIMER wait state should be removed after timer fires")
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
}
