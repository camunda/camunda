/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsConditionsAreMet;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class JobPriorityResolutionIT {

  private static CamundaClient client;

  @Test
  void shouldAcceptDecimalScaleIntegerPriorityAsTaskLevelFeelExpression() {
    // given
    final String processId = "prio-resolution-decimal-scale";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("prio-decimal-job").zeebeJobPriority("=1.0"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, process, processId + ".bpmn");

    // when
    final long processInstanceKey = startProcessInstance(client, processId).getProcessInstanceKey();

    // then
    final Job job = waitForJobs(client, List.of(processInstanceKey)).getFirst();
    assertThat(job.getPriority()).isEqualTo(1);
  }

  @Test
  void shouldFallBackToProcessLevelDefaultPriorityWhenTaskLevelAbsent() {
    // given
    final String processId = "prio-resolution-process-default";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .zeebeJobPriority("7")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("prio-process-default-job"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, process, processId + ".bpmn");

    // when
    final long processInstanceKey = startProcessInstance(client, processId).getProcessInstanceKey();

    // then
    final Job job = waitForJobs(client, List.of(processInstanceKey)).getFirst();
    assertThat(job.getPriority()).isEqualTo(7);
  }

  @Test
  void shouldRaiseIncidentForFractionalPriorityInsteadOfCreatingJob() {
    // given
    final String processId = "prio-resolution-fractional-incident";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType("prio-fractional-job").zeebeJobPriority("=1.5"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, process, processId + ".bpmn");

    // when
    final long processInstanceKey = startProcessInstance(client, processId).getProcessInstanceKey();

    // then
    waitUntilIncidentsConditionsAreMet(
        client,
        f -> f.state(IncidentState.ACTIVE).processInstanceKey(processInstanceKey),
        1,
        "should wait until the priority incident is active");
    final var incident =
        client
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.ACTIVE).processInstanceKey(processInstanceKey))
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(incident.getErrorType()).isEqualTo(IncidentErrorType.EXTRACT_VALUE_ERROR);
    waitForJobs(client, f -> f.processInstanceKey(processInstanceKey), 0);
  }
}
