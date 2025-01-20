/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient.CreateProcessInstanceVariable;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class TasklistCreateProcessInstanceIT {

  private static final String PROCESS_ID = "startedByForm";
  @AutoClose private static CamundaClient defaultCamundaClient;
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  @TestZeebe
  private TestStandaloneCamunda standaloneCamunda =
      new TestStandaloneCamunda().withCamundaExporter().withUnauthenticatedAccess();

  @BeforeEach
  public void beforeAll() {
    tasklistRestClient = standaloneCamunda.newTasklistClient();
    defaultCamundaClient =
        standaloneCamunda
            .newClientBuilder()
            .preferRestOverGrpc(true)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();

    // deploy a process with admin user
    deployResource(defaultCamundaClient);
    waitForProcessToBeDeployed(PROCESS_ID);
  }

  @Test
  public void shouldCreateProcessInstance() {
    // given (non-admin) user without any authorizations
    final var variables =
        List.of(
            new CreateProcessInstanceVariable("testVar", "\"testValue\""),
            new CreateProcessInstanceVariable("testVar2", "\"testValue2\""));

    // when
    final var response = tasklistRestClient.createProcessInstance(PROCESS_ID, variables);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  @Test
  public void shouldCreateProcessInstanceViaPublicForm() {
    // given (non-admin) user without any authorizations
    final var variables =
        List.of(
            new CreateProcessInstanceVariable("testVar", "\"testValue\""),
            new CreateProcessInstanceVariable("testVar2", "\"testValue2\""));

    // when
    final var response =
        tasklistRestClient.createProcessInstanceViaPublicForm(PROCESS_ID, variables);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  private void deployResource(final CamundaClient camundaClient) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/startedByFormProcess.bpmn")
        .send()
        .join();
  }

  private void waitForProcessToBeDeployed(final String processDefinitionId) {
    Awaitility.await("should deploy process and export")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  defaultCamundaClient
                      .newProcessDefinitionQuery()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(1);
            });
  }

  private void ensureProcessInstanceCreated(final String processDefinitionId) {
    final var queryResponse =
        defaultCamundaClient
            .newProcessInstanceQuery()
            .filter(f -> f.processDefinitionId(processDefinitionId))
            .send()
            .join();
    Awaitility.await(
            "should have started process instance with id %s".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  defaultCamundaClient
                      .newProcessInstanceQuery()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }
}
