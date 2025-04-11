/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.tenants;

import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@MultiDbTest
@Disabled
public class TasklistV1MultiTenancyIT {

  public static final String PASSWORD = "password";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";
  private static final String PROCESS_DEFINITION_ID = "process_with_assigned_user_task";
  private static final String PROCESS_DEFINITION_ID_2 = "bpm_variable_test";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN_USER_NAME,
          ADMIN_USER_PASSWORD,
          List.of(
              new Permissions(ResourceTypeEnum.RESOURCE, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_PROCESS_DEFINITION,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_USER_TASK,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(ResourceTypeEnum.USER, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));

  @UserDefinition private static final User USER1 = new User(USERNAME_1, PASSWORD, List.of());

  @UserDefinition private static final User USER2 = new User(USERNAME_2, PASSWORD, List.of());
  private static long processDefinitionKey1;
  private static long processDefinitionKey2;

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withMultiTenancyEnabled();

  @BeforeAll
  public static void beforeAll(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(USERNAME_1) final CamundaClient userOneClient,
      @Authenticated(USERNAME_2) final CamundaClient userTwoClient) {

    // User ONE <-> Tenant ONE
    adminClient.newCreateTenantCommand().tenantId(TENANT_ID_1).name(TENANT_ID_1).send().join();
    adminClient.newAssignUserToTenantCommand(TENANT_ID_1).username("demo").send().join();

    // User TWO <-> Tenant TWO
    adminClient.newCreateTenantCommand().tenantId(TENANT_ID_2).name(TENANT_ID_2).send().join();
    adminClient.newAssignUserToTenantCommand(TENANT_ID_2).username(USERNAME_2).send().join();

    // deploy
    final var processTenant1 =
        deployResourceForTenant(
                adminClient, String.format("process/%s.bpmn", PROCESS_DEFINITION_ID), TENANT_ID_1)
            .getProcesses()
            .getFirst();
    assertThat(processTenant1.getTenantId()).isEqualTo(TENANT_ID_1);
    processDefinitionKey1 = processTenant1.getProcessDefinitionKey();

    final var processTenant2 =
        deployResourceForTenant(
                adminClient, String.format("process/%s.bpmn", PROCESS_DEFINITION_ID_2), TENANT_ID_2)
            .getProcesses()
            .getFirst();
    assertThat(processTenant2.getTenantId()).isEqualTo(TENANT_ID_2);
    processDefinitionKey2 = processTenant2.getProcessDefinitionKey();
    waitForProcessesToBeDeployed(adminClient, 2);
  }

  @Test
  public void shouldGetProcessByKeyOnlyForProcessesInAuthenticatedTenants() {
    try (final var tasklistClient1 =
            CAMUNDA_APPLICATION.newTasklistClient().withAuthentication(USERNAME_1, PASSWORD);
        final var tasklistClient2 =
            CAMUNDA_APPLICATION.newTasklistClient().withAuthentication(USERNAME_2, PASSWORD)) {

      // user 1 can read process definition 1
      assertThat(tasklistClient1.getProcessDefinition(processDefinitionKey1).statusCode())
          .isEqualTo(OK.value());

      // user 1 cannot read process definition 2
      assertThat(tasklistClient1.getProcessDefinition(processDefinitionKey2).statusCode())
          .isEqualTo(NOT_FOUND.value());

      // user 2 can read process definition 1
      assertThat(tasklistClient2.getProcessDefinition(processDefinitionKey1).statusCode())
          .isEqualTo(OK.value());

      // user 2 cannot read process definition 2
      assertThat(tasklistClient2.getProcessDefinition(processDefinitionKey2).statusCode())
          .isEqualTo(NOT_FOUND.value());
    }
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
