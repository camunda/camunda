/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.tenants;

import static io.camunda.it.client.QueryTest.deployResourceForTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class TasklistV1MultiTenancyIT {

  public static final String PASSWORD = "password";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";
  private static final String PROCESS_DEFINITION_ID = "process_with_assigned_user_task";
  private static final String PROCESS_DEFINITION_ID_2 = "bpm_variable_test";
  private long processDefinitionKey1;
  private long processDefinitionKey2;

  @ZeebeIntegration.TestZeebe
  private final TestStandaloneCamunda testInstance =
      new TestStandaloneCamunda()
          .withCamundaExporter()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withMultiTenancyEnabled();

  @BeforeEach
  public void beforeEach() {
    try (final var authorizationsUtil =
        AuthorizationsUtil.create(testInstance, testInstance.getElasticSearchHostAddress())) {
      authorizationsUtil.createUser(USERNAME_1, PASSWORD);
      authorizationsUtil.createUser(USERNAME_2, PASSWORD);
      authorizationsUtil.createTenant(TENANT_ID_1, TENANT_ID_1, "demo");
      authorizationsUtil.createTenant(TENANT_ID_2, TENANT_ID_2, USERNAME_2);

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
                  String.format("process/%s.bpmn", PROCESS_DEFINITION_ID_2),
                  TENANT_ID_2)
              .getProcesses()
              .getFirst();
      assertThat(processTenant2.getTenantId()).isEqualTo(TENANT_ID_2);

      processDefinitionKey2 = processTenant2.getProcessDefinitionKey();

      waitForProcessesToBeDeployed(authorizationsUtil.getDefaultClient(), 2);
    }
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
            testInstance.newTasklistClient().withAuthentication(USERNAME_1, PASSWORD);
        final var tasklistClient2 =
            testInstance.newTasklistClient().withAuthentication(USERNAME_2, PASSWORD)) {

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
