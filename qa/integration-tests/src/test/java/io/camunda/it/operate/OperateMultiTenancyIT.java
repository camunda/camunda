/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.it.client.QueryTest.deployResourceForTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperateMultiTenancyIT {

  public static final String PASSWORD = "password";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";
  private static final String PROCESS_DEFINITION_ID = "service_tasks_v1";

  @ZeebeIntegration.TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testInstance;

  private static long processDefinitionKey1;
  private static long processDefinitionKey2;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testInstance =
        new TestStandaloneCamunda()
            .withCamundaExporter()
            .withAdditionalProfile(Profile.AUTH_BASIC)
            .withMultiTenancyEnabled();
  }

  @BeforeAll
  public static void beforeAll() throws Exception {
    final var authorizationsUtil =
        AuthorizationsUtil.create(testInstance, testInstance.getElasticSearchHostAddress());
    final var userKey1 = authorizationsUtil.createUser(USERNAME_1, PASSWORD);
    final var userKey2 = authorizationsUtil.createUser(USERNAME_2, PASSWORD);
    authorizationsUtil.createTenant(TENANT_ID_1, TENANT_ID_1, userKey1);
    authorizationsUtil.createTenant(TENANT_ID_2, TENANT_ID_2, userKey2);

    final var processTenant1 =
        deployResourceForTenant(
                authorizationsUtil.getDefaultClient(),
                String.format("process/%s.bpmn", PROCESS_DEFINITION_ID),
                TENANT_ID_1)
            .getProcesses()
            .getFirst();
    assertThat(processTenant1.getTenantId()).isEqualTo(TENANT_ID_1);
    processDefinitionKey1 = processTenant1.getProcessDefinitionKey();

    final var processTenant2 =
        deployResourceForTenant(
                authorizationsUtil.getDefaultClient(),
                String.format("process/%s.bpmn", PROCESS_DEFINITION_ID),
                TENANT_ID_2)
            .getProcesses()
            .getFirst();
    assertThat(processTenant2.getTenantId()).isEqualTo(TENANT_ID_2);
    processDefinitionKey2 = processTenant2.getProcessDefinitionKey();

    waitForProcessesToBeDeployed(authorizationsUtil.getDefaultClient(), 2);
  }

  @Test
  public void shouldGetProcessByKeyOnlyForProcessesInAuthenticatedTenants() {
    final var operateClient1 = testInstance.newOperateClient(USERNAME_1, PASSWORD);
    final var operateClient2 = testInstance.newOperateClient(USERNAME_2, PASSWORD);
    // user 1 can read process definition 1
    assertThat(
            operateClient1
                .internalGetProcessDefinitionByKey(processDefinitionKey1)
                .get()
                .statusCode())
        .isEqualTo(OK.value());

    // user 1 cannot read process definition 2
    assertThat(
            operateClient1
                .internalGetProcessDefinitionByKey(processDefinitionKey2)
                .get()
                .statusCode())
        .isEqualTo(NOT_FOUND.value());

    // user 2 can read process definition 2
    assertThat(
            operateClient2
                .internalGetProcessDefinitionByKey(processDefinitionKey2)
                .get()
                .statusCode())
        .isEqualTo(OK.value());

    // user 2 cannot read process definition 1
    assertThat(
            operateClient2
                .internalGetProcessDefinitionByKey(processDefinitionKey1)
                .get()
                .statusCode())
        .isEqualTo(NOT_FOUND.value());
  }

  private static void waitForProcessesToBeDeployed(
      final ZeebeClient zeebeClient, final int expectedProcessDefinitions) {
    Awaitility.await("Should processes be exported to Elasticsearch")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(zeebeClient.newProcessDefinitionQuery().send().join().items())
                    .hasSize(expectedProcessDefinitions));
  }
}
