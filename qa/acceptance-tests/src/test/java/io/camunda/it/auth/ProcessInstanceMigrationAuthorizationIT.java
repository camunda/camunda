/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessInstanceMigrationAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String SOURCE_ID = "migration_authz_source";
  private static final String TARGET_ID = "migration_authz_target";
  private static final String ADMIN = "admin";
  private static final String SOURCE_ONLY = "sourceOnly";
  private static final String SOURCE_AND_TARGET = "sourceAndTarget";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser SOURCE_ONLY_USER =
      new TestUser(
          SOURCE_ONLY,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(SOURCE_ID)),
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of(SOURCE_ID)),
              new Permissions(BATCH, CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser SOURCE_AND_TARGET_USER =
      new TestUser(
          SOURCE_AND_TARGET,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(SOURCE_ID)),
              new Permissions(
                  PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of(SOURCE_ID, TARGET_ID)),
              new Permissions(BATCH, CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, READ, List.of("*"))));

  @Test
  void shouldRejectMigrationToTargetWithoutPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(SOURCE_ONLY) final CamundaClient client) {
    // given: source and target deployed, an instance running on the source
    final long sourceKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(SOURCE_ID)
                    .startEvent()
                    .userTask("sourceTask")
                    .endEvent()
                    .done(),
                "source.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long targetKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(TARGET_ID)
                    .startEvent()
                    .userTask("targetTask")
                    .endEvent()
                    .done(),
                "target.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .processDefinitionKey(sourceKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("process instance is exported")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(SOURCE_ID));

    // when: the user, authorized on the source only, migrates to the target
    final var problem =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    client
                        .newMigrateProcessInstanceCommand(processInstanceKey)
                        .migrationPlan(
                            MigrationPlan.newBuilder()
                                .withTargetProcessDefinitionKey(targetKey)
                                .addMappingInstruction("sourceTask", "targetTask")
                                .build())
                        .send()
                        .join())
            .actual();

    // then: the call is forbidden and the instance stays on the source
    assertThat(problem.code()).isEqualTo(403);
    assertThat(
            adminClient
                .newProcessInstanceGetRequest(processInstanceKey)
                .send()
                .join()
                .getProcessDefinitionId())
        .isEqualTo(SOURCE_ID);
  }

  @Test
  void shouldMigrateWhenPermittedOnSourceAndTarget(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(SOURCE_AND_TARGET) final CamundaClient client) {
    // given
    final long sourceKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(SOURCE_ID)
                    .startEvent()
                    .userTask("sourceTask")
                    .endEvent()
                    .done(),
                "source.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long targetKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(TARGET_ID)
                    .startEvent()
                    .userTask("targetTask")
                    .endEvent()
                    .done(),
                "target.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .processDefinitionKey(sourceKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("process instance is exported")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(SOURCE_ID));

    // when: the user, authorized on source and target, migrates
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetKey)
                .addMappingInstruction("sourceTask", "targetTask")
                .build())
        .send()
        .join();

    // then: the instance ends up on the target
    Awaitility.await("process instance is migrated to the target")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(TARGET_ID));
  }

  @Test
  void shouldNotBatchMigrateToTargetWithoutPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(SOURCE_ONLY) final CamundaClient client) {
    // given
    final long sourceKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(SOURCE_ID)
                    .startEvent()
                    .userTask("sourceTask")
                    .endEvent()
                    .done(),
                "source.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long targetKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(TARGET_ID)
                    .startEvent()
                    .userTask("targetTask")
                    .endEvent()
                    .done(),
                "target.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .processDefinitionKey(sourceKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("process instance is exported")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(SOURCE_ID));

    // when: the user, authorized on the source only, starts a batch migration to the target
    final String batchKey =
        client
            .newCreateBatchOperationCommand()
            .migrateProcessInstance()
            .migrationPlan(
                MigrationPlan.newBuilder()
                    .withTargetProcessDefinitionKey(targetKey)
                    .addMappingInstruction("sourceTask", "targetTask")
                    .build())
            .filter(new ProcessInstanceFilterImpl().processInstanceKey(processInstanceKey))
            .send()
            .join()
            .getBatchOperationKey();
    Awaitility.await("batch operation has finished")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(client.newBatchOperationGetRequest(batchKey).send().join().getStatus())
                    .isIn(
                        Set.of(
                            BatchOperationState.COMPLETED,
                            BatchOperationState.PARTIALLY_COMPLETED,
                            BatchOperationState.FAILED)));

    // then: the instance was not migrated
    assertThat(
            adminClient
                .newProcessInstanceGetRequest(processInstanceKey)
                .send()
                .join()
                .getProcessDefinitionId())
        .isEqualTo(SOURCE_ID);
  }

  @Test
  void shouldBatchMigrateWhenPermittedOnSourceAndTarget(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(SOURCE_AND_TARGET) final CamundaClient client) {
    // given
    final long sourceKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(SOURCE_ID)
                    .startEvent()
                    .userTask("sourceTask")
                    .endEvent()
                    .done(),
                "source.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long targetKey =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(TARGET_ID)
                    .startEvent()
                    .userTask("targetTask")
                    .endEvent()
                    .done(),
                "target.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final long processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .processDefinitionKey(sourceKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("process instance is exported")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(SOURCE_ID));

    // when: the user, authorized on source and target, starts a batch migration
    client
        .newCreateBatchOperationCommand()
        .migrateProcessInstance()
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetKey)
                .addMappingInstruction("sourceTask", "targetTask")
                .build())
        .filter(new ProcessInstanceFilterImpl().processInstanceKey(processInstanceKey))
        .send()
        .join();

    // then: the instance ends up on the target
    Awaitility.await("process instance is migrated to the target")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getProcessDefinitionId())
                    .isEqualTo(TARGET_ID));
  }
}
