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
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@MultiDbTest
@Disabled
public class TasklistV1MultiTenancyIT {

  public static final String PASSWORD = "password";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";
  private static final String PROCESS_DEFINITION_ID = "process_with_assigned_user_task";
  private static final String PROCESS_DEFINITION_ID_2 = "bpm_variable_test";
  @UserDefinition private static final User USER1 = new User(USERNAME_1, PASSWORD, List.of());
  @UserDefinition private static final User USER2 = new User(USERNAME_2, PASSWORD, List.of());
  private static long processDefinitionKey1;
  private static long processDefinitionKey2;

  @MultiDbTestApplication
  private static final TestSimpleCamundaApplication CAMUNDA_APPLICATION =
      new TestSimpleCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withMultiTenancyEnabled();

  @BeforeAll
  public static void beforeAll(
      @Authenticated(USERNAME_1) final CamundaClient userOneClient,
      @Authenticated(USERNAME_2) final CamundaClient userTwoClient) {

    // User ONE <-> Tenant ONE
    userOneClient
        .newCreateTenantCommand()
        .tenantId(TENANT_ID_1)
        .name(TENANT_ID_1)
        .send()
        .join()
        .getTenantKey();

    userOneClient.newAssignUserToTenantCommand(TENANT_ID_1).username("demo").send().join();

    // User TWO <-> Tenant TWO
    userTwoClient
        .newCreateTenantCommand()
        .tenantId(TENANT_ID_2)
        .name(TENANT_ID_2)
        .send()
        .join()
        .getTenantKey();

    userTwoClient.newAssignUserToTenantCommand(TENANT_ID_2).username(USERNAME_2).send().join();

    // deploy
    final var processTenant1 =
        deployResourceForTenant(
                userOneClient, String.format("process/%s.bpmn", PROCESS_DEFINITION_ID), TENANT_ID_1)
            .getProcesses()
            .getFirst();
    assertThat(processTenant1.getTenantId()).isEqualTo(TENANT_ID_1);
    processDefinitionKey1 = processTenant1.getProcessDefinitionKey();

    final var processTenant2 =
        deployResourceForTenant(
                userTwoClient,
                String.format("process/%s.bpmn", PROCESS_DEFINITION_ID_2),
                TENANT_ID_2)
            .getProcesses()
            .getFirst();
    assertThat(processTenant2.getTenantId()).isEqualTo(TENANT_ID_2);

    processDefinitionKey2 = processTenant2.getProcessDefinitionKey();
  }

  // This test is disable for now because of the class TasklistSecurityStubsConfiguration
  //  Tasklist security package is excluded from the configuration of C8 single application to avoid
  // the conflicts with the existing Operate WebSecurity configuration. This will be solved after
  // the
  // creation of a common Security layer.
  @Test
  @Disabled
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
}
