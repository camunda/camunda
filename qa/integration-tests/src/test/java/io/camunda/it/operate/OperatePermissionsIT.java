/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static io.camunda.it.client.QueryTest.deployResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperatePermissionsIT {

  static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(STANDALONE_CAMUNDA);

  private static final String SUPER_USER_USERNAME = "super";
  private static final String RESTRICTED_USER_USERNAME = "restricted";
  private static final String PROCESS_DEFINITION_ID_1 = "service_tasks_v1";
  private static final String PROCESS_DEFINITION_ID_2 = "incident_process_v1";
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  @UserDefinition
  private static final User SUPER_USER =
      new User(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED_USER_USERNAME,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of(PROCESS_DEFINITION_ID_1))));

  @BeforeAll
  public static void beforeAll(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient superUserClient) throws Exception {
    final List<String> processes = List.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2);
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(superUserClient, String.format("process/%s.bpmn", process))
                    .getProcesses()));
    assertThat(DEPLOYED_PROCESSES).hasSize(processes.size());

    waitForProcessesToBeDeployed(superUserClient, DEPLOYED_PROCESSES.size());
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void shouldGetProcessByKeyOnlyForAuthorizedProcesses(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient superClient,
      @Authenticated(RESTRICTED_USER_USERNAME) final CamundaClient restrictedClient) {
    // super user can read all process definitions
    DEPLOYED_PROCESSES.stream()
        .map(Process::getProcessDefinitionKey)
        .forEach(
            key ->
                assertThatNoException()
                    .isThrownBy(
                        () -> superClient.newProcessDefinitionGetRequest(key).send().join()));

    // restricted user can read process definition 1
    assertThatNoException()
        .isThrownBy(
            () ->
                restrictedClient
                    .newProcessDefinitionGetRequest(
                        DEPLOYED_PROCESSES.get(0).getProcessDefinitionKey())
                    .send()
                    .join());

    // restricted user cannot read process definition 2
    assertThatException()
        .isThrownBy(
            () ->
                restrictedClient
                    .newProcessDefinitionGetRequest(
                        DEPLOYED_PROCESSES.get(1).getProcessDefinitionKey())
                    .send()
                    .join());
  }

  private static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    Awaitility.await("Should processes be exported to Elasticsearch")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newProcessDefinitionQuery().send().join().items())
                    .hasSize(expectedProcessDefinitions));
  }
}
