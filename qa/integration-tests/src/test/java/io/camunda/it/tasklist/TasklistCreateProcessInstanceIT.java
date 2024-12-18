/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
public class TasklistCreateProcessInstanceIT {

  private static final String PROCESS_ID = "foo";

  private static final String TEST_USER_NAME = "bar";
  private static final String TEST_USER_PASSWORD = "bar";

  @AutoCloseResource private static ZeebeClient zeebeClient;
  @AutoCloseResource private static TestRestTasklistClient tasklistRestClient;
  @AutoCloseResource private static TestRestOperateClient operateRestClient;

  @TestZeebe
  private TestStandaloneCamunda standaloneCamunda =
      new TestStandaloneCamunda().withAdditionalProfile(Profile.DEFAULT_AUTH_PROFILE);

  @BeforeEach
  public void beforeAll() {
    zeebeClient = standaloneCamunda.newClientBuilder().build();
    operateRestClient = standaloneCamunda.newOperateClient();
    tasklistRestClient = standaloneCamunda.newTasklistClient();

    // create user in Operate storage. Operate is the master for security checks
    operateRestClient.createUser(TEST_USER_NAME, TEST_USER_PASSWORD);

    // deploy a process
    deployResource(zeebeClient);
    waitForProcessToBeDeployed(PROCESS_ID);
  }

  @Test
  public void shouldCreateProcessInstance() {
    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  @Test
  public void shouldCreateProcessInstanceWithoutAuthentication() {
    // when
    final var response = tasklistRestClient.createProcessInstanceViaPublicForm(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  private void deployResource(final ZeebeClient zeebeClient) {
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/process_public_start.bpmn")
        .send()
        .join();
  }

  private void waitForProcessToBeDeployed(final String processDefinitionId) {
    Awaitility.await("should deploy process and export")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = tasklistRestClient.searchProcesses(processDefinitionId);
              assertThat(result.hits()).hasSize(1);
            });
  }

  private void ensureProcessInstanceCreated(final String processDefinitionId) {
    Awaitility.await(
            "should have started process instance with id %s".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  operateRestClient
                      .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
                      .getProcessInstanceWith(PROCESS_ID);
              assertThat(result.isRight()).isTrue();
              assertThat(result.get().processInstances()).hasSize(1);
            });
  }
}
