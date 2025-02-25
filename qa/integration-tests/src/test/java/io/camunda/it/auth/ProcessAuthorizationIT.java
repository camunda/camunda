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
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.Permissions;
import io.camunda.it.utils.CamundaClientTestFactory.User;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

@TestInstance(Lifecycle.PER_CLASS)
class ProcessAuthorizationIT {

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_DEFINITION,
                  List.of("service_tasks_v1", "service_tasks_v2"))));

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER);

  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    if (!initialized) {
      final List<String> processes =
          List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
      processes.forEach(
          process -> deployResource(adminClient, String.format("process/%s", process)));
      waitForProcessesToBeDeployed(adminClient, processes.size());
      initialized = true;
    }
  }

  @TestTemplate
  void searchShouldReturnAuthorizedProcessDefinitions(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var processDefinitions = userClient.newProcessDefinitionQuery().send().join().items();

    // then
    assertThat(processDefinitions).hasSize(2);
    assertThat(processDefinitions.stream().map(p -> p.getProcessDefinitionId()).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2");
  }

  @TestTemplate
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

  @TestTemplate
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
        .newProcessDefinitionQuery()
        .filter(f -> f.processDefinitionId(processDefinitionId))
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
