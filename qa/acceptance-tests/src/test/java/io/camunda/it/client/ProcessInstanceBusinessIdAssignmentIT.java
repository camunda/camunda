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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@MultiDbTest
class ProcessInstanceBusinessIdAssignmentIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldAssignBusinessIdToRunningProcessInstance() throws Exception {
    // given - a running process instance that currently has no business id
    final var processId = "business-id-assignment-process";
    final var businessId = "order-98765";
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

    // when - the business id is assigned late via the dedicated REST endpoint
    final var response =
        assignBusinessId(processInstanceKey, "{\"businessId\":\"%s\"}".formatted(businessId));

    // then - the endpoint accepts the assignment and it propagates to secondary storage
    assertThat(response.statusCode()).isEqualTo(204);

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

    // and - re-sending the identical business id succeeds idempotently
    final var idempotentResponse =
        assignBusinessId(processInstanceKey, "{\"businessId\":\"%s\"}".formatted(businessId));
    assertThat(idempotentResponse.statusCode()).isEqualTo(204);
  }

  private HttpResponse<String> assignBusinessId(final long processInstanceKey, final String body)
      throws Exception {
    final var base = camundaClient.getConfiguration().getRestAddress().toString();
    final var separator = base.endsWith("/") ? "" : "/";
    final var request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    base
                        + separator
                        + "v2/process-instances/%d/business-id-assignment"
                            .formatted(processInstanceKey)))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }
}
