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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.TimerWaitStateDetails;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

/**
 * Verifies that only intermediate catch event timers are exported as TIMER wait states. Boundary
 * timers are excluded by the transformer.
 */
@MultiDbTest
public class WaitStateTimerIT {

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

    final long pik =
        startProcessInstance(camundaClient, "waitStateTimerProcess").getProcessInstanceKey();

    TestHelper.waitForWaitStates(camundaClient, 2);

    // when
    final var items =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(pik).waitStateType(WaitStateType.TIMER))
            .send()
            .join()
            .items();

    assertThat(items).hasSize(1);
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
  }
}
