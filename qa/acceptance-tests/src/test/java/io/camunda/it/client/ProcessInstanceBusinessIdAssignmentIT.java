/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AssignProcessInstanceBusinessIdCommandStep1;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@MultiDbTest
class ProcessInstanceBusinessIdAssignmentIT {

  private static CamundaClient camundaClient;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAssignBusinessIdToRunningProcessInstance(final boolean useRest) {
    // given - a running process instance that currently has no business id
    final var transport = useRest ? "rest" : "grpc";
    final long processInstanceKey =
        startProcessInstance("business-id-assignment-" + transport + "-process");
    final var businessId = "order-" + transport + "-98765";

    // when - the business id is assigned late via the Java client over the chosen transport
    assignBusinessId(processInstanceKey, businessId, useRest).send().join();

    // then - the assignment propagates to secondary storage
    awaitBusinessId(processInstanceKey, businessId);

    // and - re-sending the identical business id succeeds idempotently
    assignBusinessId(processInstanceKey, businessId, useRest).send().join();
  }

  private AssignProcessInstanceBusinessIdCommandStep1.AssignProcessInstanceBusinessIdCommandStep2
      assignBusinessId(
          final long processInstanceKey, final String businessId, final boolean useRest) {
    final var command = camundaClient.newAssignProcessInstanceBusinessIdCommand(processInstanceKey);
    return (useRest ? command.useRest() : command.useGrpc()).businessId(businessId);
  }

  private long startProcessInstance(final String processId) {
    final var processDefinition =
        Bpmn.createExecutableProcess(processId).startEvent().userTask("task").endEvent().done();
    deployProcessAndWaitForIt(camundaClient, processDefinition, processId + ".bpmn");

    final long processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceKey),
        instances -> assertThat(instances).hasSize(1));

    return processInstanceKey;
  }

  private void awaitBusinessId(final long processInstanceKey, final String businessId) {
    await("business id is reflected in secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getBusinessId())
                    .isEqualTo(businessId));
  }
}
