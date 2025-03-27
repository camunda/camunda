/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_DEFINITION,
                  List.of("service_tasks_v1", "service_tasks_v2"))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final List<String> processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(process -> deployResource(adminClient, String.format("process/%s", process)));
    waitForProcessesToBeDeployed(adminClient, processes.size());
  }

  @Test
  void searchShouldReturnAuthorizedProcessDefinitions(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var processDefinitions =
        userClient.newProcessDefinitionSearchRequest().send().join().items();

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
    final Executable executeGet =
        () -> userClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join();

    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
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
        userClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join();

    // then
    assertThat(processDefinition).isNotNull();
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("service_tasks_v1");
  }

  private long getProcessDefinitionKey(
      final CamundaClient client, final String processDefinitionId) {
    return client
        .newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId))
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
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
