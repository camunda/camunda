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
import static io.camunda.it.util.TestHelper.waitForDecisionsToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsConditionsAreMet;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
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
public class CompatibilityModeOperateZeebeAuthorizationIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          // use to enable Operate's operation executor
          .withAdditionalProfile(Profile.TEST_EXECUTOR)
          .withAdditionalProperties(
              Map.of(
                  "camunda.operate.zeebe.compatibility.enabled",
                  false,
                  "camunda.operate.operationExecutor.executorEnabled",
                  true))
          .withExporter(
              "recordingExporter", cfg -> cfg.setClassName(RecordingExporter.class.getName()));

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
              new Permissions(
                  ResourceType.DECISION_DEFINITION,
                  PermissionType.READ_DECISION_DEFINITION,
                  List.of("*")),
              new Permissions(
                  ResourceType.DECISION_REQUIREMENTS_DEFINITION, PermissionType.READ, List.of("*")),
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
                      PROCESS_WITH_INCIDENT, PROCESS_WITH_VARIABLE, PROCESS_WITH_SERVICE_TASKS)),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.MODIFY_PROCESS_INSTANCE,
                  List.of(
                      PROCESS_WITH_INCIDENT, PROCESS_WITH_VARIABLE, PROCESS_WITH_SERVICE_TASKS)),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.CANCEL_PROCESS_INSTANCE,
                  List.of(
                      PROCESS_WITH_INCIDENT, PROCESS_WITH_VARIABLE, PROCESS_WITH_SERVICE_TASKS))));

  private static long processDefinitionForDeletionKey;
  private static DeploymentEvent decisionDeploymentEvent;
  private static long targetMigrationProcessDefinitionKey;

  @BeforeAll
  public static void beforeAll(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient testClientWithPermissions,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient testClientNoPermissions) {
    // given
    // deployed decision definitions
    decisionDeploymentEvent = deployResource(adminClient, DECISION_RESOURCE);
    // wait for Operate to catch up
    waitForDecisionsToBeDeployed(
        adminClient,
        decisionDeploymentEvent.getDecisions().size(),
        decisionDeploymentEvent.getDecisionRequirements().size());
    // deploy process definitions
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
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the process definition to be deleted
        RecordingExporter.setMaximumWaitTime(25_000L);
        assertThat(
                RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                    .withResourceKey(processDefinitionForDeletionKey)
                    .count())
            .isEqualTo(1L);
        RecordingExporter.setMaximumWaitTime(5000);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideUserAndResponseCode")
  public void shouldDeleteDecisionDefinition(final TestUser user, final int expectedResponseCode) {
    // given
    // a client with decision delete permissions
    final var decision = decisionDeploymentEvent.getDecisions().get(0);
    final var decisionKey = decision.getDecisionKey();
    final var decisionRequirementsKey = decision.getDecisionRequirementsKey();

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response = operateRestClient.deleteDecisionDefinition(decisionKey);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the decision definition to be deleted
        RecordingExporter.setMaximumWaitTime(25_000L);
        assertThat(
                RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                    .withResourceKey(decisionRequirementsKey)
                    .limit(1L)
                    .count())
            .isEqualTo(1L);
        RecordingExporter.setMaximumWaitTime(5000);
      }
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
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the process instance to be canceled
        assertThat(
                RecordingExporter.processInstanceRecords()
                    .withIntent(ProcessInstanceIntent.TERMINATE_ELEMENT)
                    .withRecordKey(processInstanceKey)
                    .count())
            .isEqualTo(1L);
      }
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
              .setName("authorized-batch")
              .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
              .setQuery(
                  new ListViewQueryDto()
                      .setActive(true)
                      .setRunning(true)
                      .setIncidents(true)
                      .setFinished(true)
                      .setCompleted(true)
                      .setCanceled(true))
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

      // TODO: re-enable this assertion once batch operations are picked up by the Operate executor
      //      RecordingExporter.setMaximumWaitTime(25_000L);
      //      assertThat(
      //              RecordingExporter.processInstanceMigrationRecords(
      //                      ProcessInstanceMigrationIntent.MIGRATED)
      //                  .withRecordKey(processInstanceKey)
      //                  .count())
      //          .isEqualTo(1L);
      //      RecordingExporter.setMaximumWaitTime(5000);
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
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the process instance to be modified
        assertThat(
                RecordingExporter.processInstanceModificationRecords()
                    .withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withProcessInstanceKey(processInstanceKey)
                    .count())
            .isEqualTo(1L);
      }
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
    final var variableValue = 3;
    waitForProcessInstancesToStart(
        adminClient, filter -> filter.processInstanceKey(processInstanceKey), 1);

    try (final var operateRestClient =
        STANDALONE_CAMUNDA.newOperateClient(user.username(), user.password())) {

      // when
      final var response =
          operateRestClient.updateVariable(
              processInstanceKey, variableScopeId, variableName, variableValue);

      // then
      assertThat(response).isRight();
      final var responseBody = response.get();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody.statusCode()).isEqualTo(expectedResponseCode);
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the variable to be updated
        final var variableUpdateRecord =
            RecordingExporter.variableRecords()
                .withIntent(VariableIntent.UPDATED)
                .withScopeKey(processInstanceKey)
                .withName(variableName)
                .limit(1L)
                .getFirst();
        assertThat(variableUpdateRecord).isNotNull();
        assertThat(variableUpdateRecord.getValue().getValue()).isEqualTo("3");
      }
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
      if (expectedResponseCode == 200) {
        // if the request is successful, we expect the incident to be resolved
        assertThat(
                RecordingExporter.incidentRecords()
                    .withIntent(IncidentIntent.RESOLVED)
                    .withProcessInstanceKey(processInstanceKey)
                    .count())
            .isEqualTo(1L);
      }
    }
  }

  private static Stream<Arguments> provideUserAndResponseCode() {
    // the exceptional path arguments are first, so that we can
    // re-use the same process and decision definitions in the deletion tests
    return Stream.of(
        Arguments.of(TEST_USER_NO_PERMISSIONS, 403), Arguments.of(TEST_USER_WITH_PERMISSIONS, 200));
  }
}
