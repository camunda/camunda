/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateInternalApiPermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static CamundaClient camundaClient;

  private static final String SUPER_USER_USERNAME = "super";
  private static final String RESTRICTED_USER_USERNAME = "restricted";
  private static final String PROCESS_DEFINITION_ID_1 = "service_tasks_v1";
  private static final String PROCESS_DEFINITION_ID_2 = "incident_process_v1";
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  @UserDefinition
  private static final TestUser SUPER_USER =
      new TestUser(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED_USER_USERNAME,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_DEFINITION_ID_1))));

  @BeforeAll
  public static void beforeAll(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient superUserClient,
      @Authenticated(RESTRICTED_USER_USERNAME) final CamundaClient restrictedUserClient)
      throws Exception {
    final List<String> processes = List.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2);
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s.bpmn", process))
                    .getProcesses()));
    assertThat(DEPLOYED_PROCESSES).hasSize(processes.size());

    waitForProcessesToBeDeployed(camundaClient, DEPLOYED_PROCESSES.size());
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void shouldGetProcessByKeyOnlyForAuthorizedProcesses() {
    // super user can read all process definitions
    final var operateClient =
        STANDALONE_CAMUNDA.newOperateClient(SUPER_USER.username(), SUPER_USER.password());

    assertThat(
            DEPLOYED_PROCESSES.stream()
                .map(Process::getProcessDefinitionKey)
                .allMatch(
                    (key) ->
                        operateClient.internalGetProcessDefinitionByKey(key).get().statusCode()
                            == 200))
        .isTrue();

    // restricted user can read process definition 1
    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER.username(), RESTRICTED_USER.password());

    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(0).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(200);

    // restricted user cannot read process definition 2
    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(1).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(403);
  }

  private static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    Awaitility.await("Should processes be exported to Elasticsearch")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newProcessDefinitionSearchRequest().send().join().items())
                    .hasSize(expectedProcessDefinitions));
  }
}
