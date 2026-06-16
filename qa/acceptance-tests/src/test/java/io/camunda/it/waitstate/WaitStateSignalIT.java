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
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.SignalWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies the element instance wait-state search command for signal-based wait states. Each test
 * deploys its own isolated process to avoid interference with shared state.
 *
 * <p>Start-event and boundary-event signal subscription filtering is covered at the unit level in
 * {@code SignalBasedWaitStateTransformerTest}.
 */
@MultiDbTest
public class WaitStateSignalIT {

  private static final String SIGNAL_NAME = "mySignal";
  private static final String CATCH_TASK = "signal-catch";

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnSignalWaitState() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("signalWaitStateProcess")
            .startEvent()
            .intermediateCatchEvent(CATCH_TASK)
            .signal(SIGNAL_NAME)
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "signalWaitStateProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "signalWaitStateProcess").getProcessInstanceKey();

    // when
    Awaitility.await("signal wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<ElementInstanceWaitStateResult> items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.processInstanceKey(pik))
                      .send()
                      .join()
                      .items();

              // then
              assertThat(items).hasSize(1);
              final var item = items.getFirst();
              assertThat(item.getElementId()).isEqualTo(CATCH_TASK);
              assertThat(item.getElementType())
                  .isEqualTo(WaitStateElementType.INTERMEDIATE_CATCH_EVENT);
              assertThat(item.getDetails()).isInstanceOf(SignalWaitStateDetails.class);
              final var signalDetails = (SignalWaitStateDetails) item.getDetails();
              assertThat(signalDetails.getWaitStateType()).isEqualTo(WaitStateType.SIGNAL);
              assertThat(signalDetails.getSignalName()).isEqualTo(SIGNAL_NAME);
            });
  }

  @Test
  void shouldFilterByWaitStateTypeSignal() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("signalFilterProcess")
            .startEvent()
            .intermediateCatchEvent("signal-filter-catch")
            .signal("filterSignal")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "signalFilterProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "signalFilterProcess").getProcessInstanceKey();

    Awaitility.await("signal wait state should appear")
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

    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.SIGNAL).processInstanceKey(pik))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDetails()).isInstanceOf(SignalWaitStateDetails.class);
  }

  @Test
  void shouldRemoveWaitStateWhenSignalIsReceived() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("signalRemovalProcess")
            .startEvent()
            .intermediateCatchEvent("removal-catch")
            .signal("removalSignal")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "signalRemovalProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "signalRemovalProcess").getProcessInstanceKey();

    Awaitility.await("signal wait state should appear")
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

    // when — broadcast the signal to progress the instance
    camundaClient.newBroadcastSignalCommand().signalName("removalSignal").send().join();

    // then — wait state is removed
    Awaitility.await("wait state should be removed after signal broadcast")
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
  void shouldReturnEmptyWhenFilteringByJobTypeForSignalProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("signalNegativeProcess")
            .startEvent()
            .intermediateCatchEvent("negative-catch")
            .signal("negativeSignal")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "signalNegativeProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "signalNegativeProcess").getProcessInstanceKey();

    Awaitility.await("signal wait state should appear")
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

    // when — filter by JOB type while the instance is waiting on a signal
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.JOB).processInstanceKey(pik))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldNotTrackSignalBoundaryEventSubscriptionAsWaitState() {
    // given — process with a service task that has a signal boundary event attached
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("signalBoundaryProcess")
            .startEvent()
            .serviceTask("boundary-task", t -> t.zeebeJobType("boundary-job"))
            .boundaryEvent("signal-boundary")
            .signal("boundarySignal")
            .endEvent()
            .moveToActivity("boundary-task")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "signalBoundaryProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "signalBoundaryProcess").getProcessInstanceKey();

    // when — wait for the process to be at the service task (a JOB wait state appears)
    Awaitility.await("job wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik).waitStateType(WaitStateType.JOB))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // then — the boundary event subscription must never appear as a SIGNAL wait state.
    // Use a stabilisation window: assert continuously for 2 s so a late-arriving record would fail.
    Awaitility.await("no signal wait state should appear for a boundary event subscription")
        .during(Duration.ofSeconds(2))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(
                                f -> f.processInstanceKey(pik).waitStateType(WaitStateType.SIGNAL))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }
}
