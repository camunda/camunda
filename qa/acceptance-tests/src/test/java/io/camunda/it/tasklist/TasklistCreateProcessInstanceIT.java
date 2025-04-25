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
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient.CreateProcessInstanceVariable;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistCreateProcessInstanceIT {

  private static final String PROCESS_ID = "startedByForm";
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  private static CamundaClient camundaClient;

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication().withUnauthenticatedAccess();

  @BeforeAll
  public static void beforeAll() {
    tasklistRestClient = CAMUNDA_APPLICATION.newTasklistClient();

    // deploy a process with admin user
    deployResource(camundaClient);
    waitForProcessToBeDeployed(camundaClient, PROCESS_ID);
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
    ensureProcessInstanceCreated(camundaClient, PROCESS_ID);
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
    ensureProcessInstanceCreated(camundaClient, PROCESS_ID);
  }

  private static void deployResource(final CamundaClient camundaClient) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/startedByFormProcess.bpmn")
        .send()
        .join();
  }

  private static void waitForProcessToBeDeployed(
      final CamundaClient camundaClient, final String processDefinitionId) {
    Awaitility.await("should deploy process and export")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(1);
            });
  }

  private void ensureProcessInstanceCreated(
      final CamundaClient camundaClient, final String processDefinitionId) {
    Awaitility.await(
            "should have started process instance with id %s".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }
}
