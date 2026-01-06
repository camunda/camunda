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
import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deleteTenant;
import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.startProcessInstanceForTenant;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForTenantDeletion;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestRestOperateClient.ProcessInstanceResult;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
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

  @Test
  public void shouldNotReturnProcessesFromDeletedTenants(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {

    setupTwoTenantsWithProcessesBeforeDeletion(camundaClient);

    try (final var operateClient = STANDALONE_CAMUNDA.newOperateClient(ADMIN, ADMIN)) {
      // given

      Awaitility.await()
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .untilAsserted(
              () -> {
                final var result = searchProcessInstancesV1(operateClient);
                assertSearchResultsTenants(result, TENANT_A, TENANT_TO_BE_DELETED);
              });

      // when
      deleteTenant(camundaClient, TENANT_TO_BE_DELETED);
      waitForTenantDeletion(camundaClient, TENANT_TO_BE_DELETED);

      // then
      Awaitility.await()
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .untilAsserted(
              () -> {
                final var afterDeletionResult = searchProcessInstancesV1(operateClient);
                assertSearchResultsTenants(afterDeletionResult, TENANT_A);
              });
    }
  }

  private void setupTwoTenantsWithProcessesBeforeDeletion(final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A, TENANT_A, ADMIN);
    createTenant(adminClient, TENANT_TO_BE_DELETED, TENANT_TO_BE_DELETED, ADMIN);

    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);

    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_TO_BE_DELETED);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_TO_BE_DELETED);
    waitForProcessInstances(adminClient, f -> f.processDefinitionId(PROCESS_ID), 2);
  }

  private Either<Exception, ProcessInstanceResult> searchProcessInstancesV1(
      final TestRestOperateClient operateClient) throws Exception {
    final var response = operateClient.sendV1SearchRequest("v1/process-instances", "{}");
    return operateClient.mapResult(response, ProcessInstanceResult.class);
  }

  private void assertSearchResultsTenants(
      final Either<Exception, ProcessInstanceResult> result,
      final String... expectedProcessInstanceTenants) {

    assertThat(result.isRight()).isTrue();

    final var processInstanceResults = result.get();
    final Set<String> tenants =
        processInstanceResults.processInstances().stream()
            .map(ProcessInstance::getTenantId)
            .collect(Collectors.toSet());
    assertThat(tenants).containsExactlyInAnyOrder(expectedProcessInstanceTenants);
  }
}
