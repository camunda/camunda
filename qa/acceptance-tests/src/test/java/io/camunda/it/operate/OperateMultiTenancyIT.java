/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "os")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateMultiTenancyIT {

  public static final String PASSWORD = "password";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";
  private static final String PROCESS_DEFINITION_ID = "service_tasks_v1";
  private static long procDefKey1;
  private static long procDefKey2;

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  @UserDefinition
  private static final TestUser USER1 = new TestUser(USERNAME_1, PASSWORD, List.of());

  @UserDefinition
  private static final TestUser USER2 = new TestUser(USERNAME_2, PASSWORD, List.of());

  @BeforeAll
  public static void beforeAll(
      @Authenticated(InitializationConfiguration.DEFAULT_USER_USERNAME)
          final CamundaClient adminClient,
      @Authenticated(USERNAME_1) final CamundaClient user1Client,
      @Authenticated(USERNAME_2) final CamundaClient user2Client) {
    createTenant(
        adminClient,
        TENANT_ID_1,
        TENANT_ID_1,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_1);
    createTenant(
        adminClient,
        TENANT_ID_2,
        TENANT_ID_2,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_2);

    final var processTenant1 =
        deployResourceForTenant(
                adminClient, String.format("process/%s.bpmn", PROCESS_DEFINITION_ID), TENANT_ID_1)
            .getProcesses()
            .getFirst();
    assertThat(processTenant1.getTenantId()).isEqualTo(TENANT_ID_1);
    procDefKey1 = processTenant1.getProcessDefinitionKey();

    final var processTenant2 =
        deployResourceForTenant(
                adminClient, String.format("process/%s.bpmn", PROCESS_DEFINITION_ID), TENANT_ID_2)
            .getProcesses()
            .getFirst();
    assertThat(processTenant2.getTenantId()).isEqualTo(TENANT_ID_2);
    procDefKey2 = processTenant2.getProcessDefinitionKey();

    waitForProcessesToBeDeployed(adminClient, 2);
  }

  @Test
  public void shouldGetProcessByKeyOnlyForProcessesInAuthenticatedTenants() {
    try (final var operateClient1 = TEST_INSTANCE.newOperateClient(USERNAME_1, PASSWORD);
        final var operateClient2 = TEST_INSTANCE.newOperateClient(USERNAME_2, PASSWORD)) {
      // user 1 can read process definition 1
      assertThat(operateClient1.internalGetProcessDefinitionByKey(procDefKey1).get().statusCode())
          .isEqualTo(OK.value());

      // user 1 cannot read process definition 2
      assertThat(operateClient1.internalGetProcessDefinitionByKey(procDefKey2).get().statusCode())
          .isEqualTo(NOT_FOUND.value());

      // user 2 can read process definition 2
      assertThat(operateClient2.internalGetProcessDefinitionByKey(procDefKey2).get().statusCode())
          .isEqualTo(OK.value());

      // user 2 cannot read process definition 1
      assertThat(operateClient2.internalGetProcessDefinitionByKey(procDefKey1).get().statusCode())
          .isEqualTo(NOT_FOUND.value());
    }
  }

  public static void createTenant(
      final CamundaClient client,
      final String tenantId,
      final String tenantName,
      final String... usernames) {
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantName).send().join();
    for (final var username : usernames) {
      client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
    }
  }

  private static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    Awaitility.await("Should processes be exported")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newProcessDefinitionSearchRequest().send().join().items())
                    .hasSize(expectedProcessDefinitions));
  }
}
