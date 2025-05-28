/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate.compatibility;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsConditionsAreMet;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class CompatibilityModeOperateAuthorizationIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withProperty("camunda.operate.zeebe.compatibility.enabled", true);

  private static final String DECISION_RESOURCE = "decisions/decision_model.dmn";
  private static final String PROCESS_FOR_DELETION_RESOURCE = "process/manual_process.bpmn";
  private static final String PROCESS_WITH_SERVICE_TASKS = "service_tasks_v1";
  private static final String PROCESS_WITH_SERVICE_TASKS_RESOURCE = "process/service_tasks_v1.bpmn";
  private static final String PROCESS_WITH_INCIDENT = "incident_process_v1";
  private static final String PROCESS_WITH_INCIDENT_RESOURCE = "process/incident_process_v1.bpmn";
  private static final String PROCESS_WITH_VARIABLE = "bpmProcessVariable";
  private static final String PROCESS_WITH_VARIABLE_RESOURCE = "process/bpm_variable_test.bpmn";
  private static final String PROCESS_FOR_MIGRATION_V1 = "migration-process_v1";
  private static final String PROCESS_FOR_MIGRATION_V1_RESOURCE =
      "process/migration-process_v1.bpmn";
  private static final String PROCESS_FOR_MIGRATION_V2_RESOURCE =
      "process/migration-process_v2.bpmn";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME_NO_PERMISSION = "noPermissionUser";
  private static final String TEST_USER_NAME_WITH_PERMISSION = "withPermissionUser";
  private static final String TEST_USER_PASSWORD = "bar";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN_USER_NAME,
          ADMIN_USER_PASSWORD,
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_DEFINITION,
                  List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION, PermissionType.READ_USER_TASK, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.CREATE_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser TEST_USER_NO_PERMISSIONS =
      new TestUser(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser TEST_USER_WITH_PERMISSIONS =
      new TestUser(
          TEST_USER_NAME_WITH_PERMISSION,
          TEST_USER_PASSWORD,
          List.of(
              new Permissions(
                  ResourceType.DECISION_DEFINITION,
                  PermissionType.DELETE_DECISION_INSTANCE,
                  List.of("*")),
              new Permissions(ResourceType.RESOURCE, PermissionType.DELETE_PROCESS, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.UPDATE_PROCESS_INSTANCE,
                  List.of(
                      PROCESS_WITH_INCIDENT, PROCESS_WITH_VARIABLE, PROCESS_WITH_SERVICE_TASKS))));

  private static long processDefinitionForDeletionKey;
  private static long decisionDefinitionForDeletionKey;
  private static long targetMigrationProcessDefinitionKey;

  @BeforeAll
  public static void beforeAll(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient testClientWithPermissions,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient testClientNoPermissions) {
    // given
    // deployed process and decision definitions
    decisionDefinitionForDeletionKey =
        deployResource(adminClient, DECISION_RESOURCE).getDecisions().get(0).getDecisionKey();
    processDefinitionForDeletionKey =
        deployResource(adminClient, PROCESS_FOR_DELETION_RESOURCE)
            .getProcesses()
            .get(0)
            .getProcessDefinitionKey();
    deployResource(adminClient, PROCESS_WITH_VARIABLE_RESOURCE);
    deployResource(adminClient, PROCESS_WITH_SERVICE_TASKS_RESOURCE);
    deployResource(adminClient, PROCESS_WITH_INCIDENT_RESOURCE);
    deployResource(adminClient, PROCESS_FOR_MIGRATION_V1_RESOURCE);
    targetMigrationProcessDefinitionKey =
        deployResource(adminClient, PROCESS_FOR_MIGRATION_V2_RESOURCE)
            .getProcesses()
            .get(0)
            .getProcessDefinitionKey();

    // wait for operate to catch up
    waitForProcessesToBeDeployed(adminClient, 6);
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldDeleteProcessDefinition(final TestUser user, final int expectedResponseCode) {
    // given
    // a user with process delete permissions
    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response =
          operateRestClient.deleteProcessDefinition(processDefinitionForDeletionKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldNotBeAuthorizedToDeleteProcessDefinition(
      final TestUser user, final int expectedResponseCode) {
    // given
    // a user with process delete permissions
    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response =
          operateRestClient.deleteProcessDefinition(processDefinitionForDeletionKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldDeleteDecisionDefinition(final TestUser user, final int expectedResponseCode) {
    // given
    // a client with decision delete permissions
    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response =
          operateRestClient.deleteDecisionDefinition(decisionDefinitionForDeletionKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldCancelProcessInstance(
      final TestUser user,
      final int expectedResponseCode,
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_WITH_SERVICE_TASKS).getProcessInstanceKey();
    // wait for operate to catch up
    waitForProcessInstancesToStart(
        adminClient, filter -> filter.processInstanceKey(processInstanceKey), 1);
    // and a user with process instance update permissions
    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response = operateRestClient.cancelProcessInstance(processInstanceKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  // process instance migrations are batched and accepted without permission checks,
  // so a request from an unauthorized user will return a 200
  @Test
  public void shouldMigrateProcessInstance(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_FOR_MIGRATION_V1).getProcessInstanceKey();
    // wait for operate to catch up
    waitForProcessInstancesToStart(
        adminClient, filter -> filter.processInstanceKey(processInstanceKey), 1);

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(
            TEST_USER_WITH_PERMISSIONS.username(), TEST_USER_WITH_PERMISSIONS.password())) {
      final var migrationRequestBody =
          new CreateBatchOperationRequestDto()
              .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
              .setQuery(
                  new ListViewQueryDto()
                      .setActive(true)
                      .setRunning(true)
                      .setBpmnProcessId(PROCESS_FOR_MIGRATION_V1))
              .setMigrationPlan(
                  new MigrationPlanDto()
                      .setTargetProcessDefinitionKey(
                          String.valueOf(targetMigrationProcessDefinitionKey))
                      .setMappingInstructions(
                          List.of(
                              new MigrationPlanDto.MappingInstruction()
                                  .setSourceElementId("taskA")
                                  .setTargetElementId("taskA2"),
                              new MigrationPlanDto.MappingInstruction()
                                  .setSourceElementId("taskB")
                                  .setTargetElementId("taskB2"))));

      // when
      final var response = operateRestClient.migrateProcessInstance(migrationRequestBody);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(200);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldModifyProcessInstance(
      final TestUser user,
      final int expectedResponseCode,
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_WITH_SERVICE_TASKS).getProcessInstanceKey();
    waitForProcessInstancesToStart(
        adminClient, filter -> filter.processInstanceKey(processInstanceKey), 1);

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {
      final var modificationRequestDto =
          new ModifyProcessInstanceRequestDto()
              .setProcessInstanceKey(String.valueOf(processInstanceKey))
              .setModifications(
                  List.of(
                      new Modification()
                          .setModification(Type.MOVE_TOKEN)
                          .setFromFlowNodeId("taskA")
                          .setToFlowNodeId("taskB")));

      // when
      final var response =
          operateRestClient.modifyProcessInstance(processInstanceKey, modificationRequestDto);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldUpdateProcessInstanceVariable(
      final TestUser user,
      final int expectedResponseCode,
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_WITH_VARIABLE).getProcessInstanceKey();
    final String variableScopeId = String.valueOf(processInstanceKey);
    final String variableName = "process01";
    waitForProcessInstancesToStart(
        adminClient, filter -> filter.processInstanceKey(processInstanceKey), 1);

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response =
          operateRestClient.updateVariable(
              processInstanceKey, variableScopeId, variableName, "\"update\"");

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldResolveIncident(
      final TestUser user,
      final int expectedResponseCode,
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_WITH_INCIDENT).getProcessInstanceKey();
    waitUntilIncidentsConditionsAreMet(
        adminClient,
        f -> f.processInstanceKey(processInstanceKey).state(IncidentState.ACTIVE),
        1,
        "should wait until incidents become active");

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response = operateRestClient.resolveIncident(processInstanceKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
    }
  }

  private static Stream<Arguments> provideUserAndResponseCode() {
    return Stream.of(
        Arguments.of(TEST_USER_WITH_PERMISSIONS, 200), Arguments.of(TEST_USER_NO_PERMISSIONS, 403));
  }
}
