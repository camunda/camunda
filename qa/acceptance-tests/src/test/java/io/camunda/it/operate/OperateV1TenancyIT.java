/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.util.Either;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateV1TenancyIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final List<Permissions> AUTHORIZED_PERMISSIONS =
      List.of(
          new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
          new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
          new Permissions(PROCESS_DEFINITION, DELETE_PROCESS_INSTANCE, List.of("*")),
          new Permissions(DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of("*")),
          new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
          new Permissions(DECISION_REQUIREMENTS_DEFINITION, READ, List.of("*")));

  private static final String ADMIN = "multiTenancyAdmin";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_TO_BE_DELETED = "tenantToBeDeleted";
  private static final String PROCESS_ID = "service_tasks_v1";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, ADMIN, AUTHORIZED_PERMISSIONS);

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_TO_BE_DELETED);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_TO_BE_DELETED);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_TO_BE_DELETED);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_TO_BE_DELETED);
    waitForProcessBeingExported(adminClient);
  }

  @Test
  public void shouldNotReturnProcessesFromDeletedTenants(
      @Authenticated(ADMIN) final CamundaClient camundaClient) throws Exception {

    try (final var operateClient = STANDALONE_CAMUNDA.newOperateClient(ADMIN, ADMIN)) {
      // given
      verifySearch(
          operateClient,
          processInstanceResult -> {
            assertThat(processInstanceResult.total).isEqualTo(2);
            assertThat(processInstanceResult.tenantIds)
                .containsExactlyInAnyOrder(TENANT_A, TENANT_TO_BE_DELETED);
          });

      // when
      deleteTenant(camundaClient, TENANT_TO_BE_DELETED);

      // then
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                verifySearch(
                    operateClient,
                    processInstanceResult -> {
                      assertThat(processInstanceResult.total).isEqualTo(1);
                      assertThat(processInstanceResult.tenantIds).containsExactly(TENANT_A);
                    });
              });
    }
  }

  private void verifySearch(
      final TestRestOperateClient client, final Consumer<ProcessInstanceResults> assertions)
      throws Exception {
    final HttpResponse<String> searchResponse =
        client.sendV1SearchRequest("v1/process-instances", "{}");
    final Either<Exception, Map> result = client.mapResult(searchResponse, Map.class);

    assertThat(result.isRight()).isTrue();

    final Map<String, Object> responseBody = result.get();

    final var processInstanceResults =
        new ProcessInstanceResults(
            (int) responseBody.get("total"),
            ((List<Map<String, Object>>) responseBody.get("items"))
                .stream().map(r -> r.get("tenantId").toString()).collect(Collectors.toSet()));

    assertions.accept(processInstanceResults);
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void deleteTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newDeleteTenantCommand(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void deployResource(
      final CamundaClient camundaClient, final String resourceName, final String tenant) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void waitForProcessBeingExported(final CamundaClient camundaClient) {
    waitForProcessBeingExported(camundaClient, 2);
  }

  private static void waitForProcessBeingExported(
      final CamundaClient camundaClient, final int expectedDefinitions) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newProcessInstanceSearchRequest()
                          .filter(filter -> filter.processDefinitionId(fn -> fn.in(PROCESS_ID)))
                          .send()
                          .join()
                          .items())
                  .hasSize(expectedDefinitions);
            });
  }

  private record ProcessInstanceResults(long total, Set<String> tenantIds) {}
}
