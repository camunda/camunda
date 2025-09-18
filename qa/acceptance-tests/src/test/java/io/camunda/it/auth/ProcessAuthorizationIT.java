/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.Form;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_DEFINITION_ID_WITH_START_FORM = "Process_11hxie4";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String START_FORM_RESTRICTED = "startFormUser";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_DEFINITION,
                  List.of("service_tasks_v1", "service_tasks_v2"))));

  @UserDefinition
  private static final TestUser START_FORM_USER =
      new TestUser(
          START_FORM_RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("Process_11hxie4"))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "form/form.form");

    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "process_start_form.bpmn");
    processes.forEach(process -> deployResource(adminClient, String.format("process/%s", process)));
    waitForProcessesToBeDeployed(adminClient, processes.size());
  }

  @Test
  void searchShouldReturnAuthorizedProcessDefinitions(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var processDefinitions =
        userClient
            .newProcessDefinitionSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join()
            .items();

    // then
    assertThat(processDefinitions).hasSize(2);
    assertThat(processDefinitions.stream().map(p -> p.getProcessDefinitionId()).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2");
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedProcessDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var processDefinitionKey = getProcessDefinitionKey(adminClient, "incident_process_v1");

    // when
    final ThrowingCallable executeGet =
        () ->
            userClient
                .newProcessDefinitionGetRequest(processDefinitionKey)
                .consistencyPolicy(ConsistencyPolicy.noWait())
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  void getByKeyShouldReturnAuthorizedProcessDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var processDefinitionKey = getProcessDefinitionKey(adminClient, "service_tasks_v1");

    // when
    final var processDefinition =
        userClient
            .newProcessDefinitionGetRequest(processDefinitionKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(processDefinition).isNotNull();
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("service_tasks_v1");
  }

  @Test
  void shouldReturnProcessDefinitionStartForm(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(START_FORM_RESTRICTED) final CamundaClient userClient) {
    final var processDefinitionKey =
        getProcessDefinitionKey(adminClient, PROCESS_DEFINITION_ID_WITH_START_FORM);

    // when
    final var form =
        userClient
            .newProcessDefinitionGetFormRequest(processDefinitionKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(form.getFormId()).isEqualTo("test");
  }

  private static long getProcessDefinitionKey(
      final CamundaClient client, final String processDefinitionId) {
    return client
        .newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy processes and make them searchable")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .consistencyPolicy(ConsistencyPolicy.noWait())
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });

    // also wait for the start form to be searchable, as tests depend on that
    final var startFormProcessDefinitionKey =
        getProcessDefinitionKey(camundaClient, PROCESS_DEFINITION_ID_WITH_START_FORM);

    Awaitility.await("should deploy start form and make it searchable")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final Future<Form> resultFuture =
                  camundaClient
                      .newProcessDefinitionGetFormRequest(startFormProcessDefinitionKey)
                      .consistencyPolicy(ConsistencyPolicy.noWait())
                      .send();

              assertThat(resultFuture).succeedsWithin(Duration.ofSeconds(10)).isNotNull();
            });
  }
}
