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
import io.camunda.client.api.statistics.response.ProcessInstanceWaitStateStatistics;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code GET /v2/process-instances/{processInstanceKey}/statistics/wait-states} reports
 * the waiting count for job, timer, and message wait states.
 */
@MultiDbTest
public class ProcessInstanceWaitStateStatisticsIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnWaitingCountForJobWaitState() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateStatsJob")
            .startEvent()
            .serviceTask("service-task", t -> t.zeebeJobType("never-activated"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateStatsJob.bpmn");
    final long pik =
        startProcessInstance(camundaClient, "waitStateStatsJob").getProcessInstanceKey();
    waitForWaitStates(pik, 1);

    // when
    final var actual = statistics(pik);

    // then
    assertThat(actual)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getElementId()).isEqualTo("service-task");
              assertThat(s.getWaitingCount()).isEqualTo(1L);
            });
  }

  @Test
  void shouldReturnWaitingCountForTimerWaitState() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateStatsTimer")
            .startEvent()
            .intermediateCatchEvent("timer-catch", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateStatsTimer.bpmn");
    final long pik =
        startProcessInstance(camundaClient, "waitStateStatsTimer").getProcessInstanceKey();
    waitForWaitStates(pik, 1);

    // when
    final var actual = statistics(pik);

    // then
    assertThat(actual)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getElementId()).isEqualTo("timer-catch");
              assertThat(s.getWaitingCount()).isEqualTo(1L);
            });
  }

  @Test
  void shouldReturnWaitingCountForMessageWaitState() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateStatsMessage")
            .startEvent()
            .intermediateCatchEvent(
                "message-catch",
                e ->
                    e.message(
                        m -> m.name("waitStateMsg").zeebeCorrelationKeyExpression("\"key-1\"")))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateStatsMessage.bpmn");
    final long pik =
        startProcessInstance(camundaClient, "waitStateStatsMessage").getProcessInstanceKey();
    waitForWaitStates(pik, 1);

    // when
    final var actual = statistics(pik);

    // then
    assertThat(actual)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getElementId()).isEqualTo("message-catch");
              assertThat(s.getWaitingCount()).isEqualTo(1L);
            });
  }

  // Barrier: wait until the expected number of wait states for this instance are visible in
  // secondary storage before asserting on the statistics endpoint.
  private static void waitForWaitStates(final long processInstanceKey, final int expected) {
    Awaitility.await(
            "%d wait states visible for instance %d".formatted(expected, processInstanceKey))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newElementInstanceWaitStateSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items()
                    .size(),
            size -> size == expected);
  }

  private static List<ProcessInstanceWaitStateStatistics> statistics(
      final long processInstanceKey) {
    return camundaClient
        .newProcessInstanceWaitStateStatisticsRequest(processInstanceKey)
        .send()
        .join();
  }
}
