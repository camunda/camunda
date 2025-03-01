/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_INSTANCE;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.it.client.QueryTest.deployResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperatePermissionsIT {

  private static final String SUPER_USER = "super";
  private static final String RESTRICTED_USER = "restricted";
  private static final String PROCESS_DEFINITION_ID_1 = "service_tasks_v1";
  private static final String PROCESS_DEFINITION_ID_2 = "incident_process_v1";

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  @ZeebeIntegration.TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testInstance;

  @AutoClose private static TestRestOperateClient superOperateClient;
  @AutoClose private static TestRestOperateClient restrictedOperateClient;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testInstance =
        new TestStandaloneCamunda()
            .withCamundaExporter()
            .withAuthorizationsEnabled()
            .withAuthenticationMethod(AuthenticationMethod.BASIC);
  }

  @BeforeAll
  public static void beforeAll() throws Exception {
    final var authorizationsUtil =
        AuthorizationsUtil.create(testInstance, testInstance.getElasticSearchHostAddress());
    final var defaultClient = authorizationsUtil.getDefaultClient();
    // create super user that can read all process definitions
    final var superCamundaClient =
        authorizationsUtil.createUserAndClient(
            SUPER_USER,
            "password",
            new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
            new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")));
    superOperateClient = testInstance.newOperateClient(SUPER_USER, "password");
    // create restricted user that can only read process definition 1
    authorizationsUtil.createUserWithPermissions(
        RESTRICTED_USER,
        "password",
        new Permissions(
            PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_DEFINITION_ID_1)));
    restrictedOperateClient = testInstance.newOperateClient(RESTRICTED_USER, "password");

    final List<String> processes = List.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2);
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(defaultClient, String.format("process/%s.bpmn", process))
                    .getProcesses()));
    assertThat(DEPLOYED_PROCESSES).hasSize(processes.size());

    waitForProcessesToBeDeployed(superCamundaClient, DEPLOYED_PROCESSES.size());
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void shouldGetProcessByKeyOnlyForAuthorizedProcesses() {
    // super user can read all process definitions
    DEPLOYED_PROCESSES.stream()
        .map(Process::getProcessDefinitionKey)
        .forEach(
            key ->
                assertThat(
                        superOperateClient
                            .internalGetProcessDefinitionByKey(key)
                            .get()
                            .statusCode())
                    .isEqualTo(OK.value()));

    // restricted user can read process definition 1
    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(0).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(OK.value());

    // restricted user cannot read process definition 2
    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(1).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(FORBIDDEN.value());
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
