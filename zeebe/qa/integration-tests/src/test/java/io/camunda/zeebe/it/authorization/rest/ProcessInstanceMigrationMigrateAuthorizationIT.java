/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization.rest;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class ProcessInstanceMigrationMigrateAuthorizationIT {
  public static final String JOB_TYPE = "jobType";
  public static final String SOURCE_TASK = "sourceTask";
  public static final String TARGET_TASK = "targetTask";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String PROCESS_ID = "processId";
  private static final String TARGET_PROCESS_ID = "targetProcessId";
  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient defaultUserClient;
  private static long targetProcDefKey;

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
    final var deploymentEvent =
        defaultUserClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(SOURCE_TASK, t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done(),
                "process.xml")
            .addProcessModel(
                Bpmn.createExecutableProcess(TARGET_PROCESS_ID)
                    .startEvent()
                    .serviceTask(TARGET_TASK, t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done(),
                "targetProcess.xml")
            .send()
            .join();
    targetProcDefKey =
        deploymentEvent.getProcesses().stream()
            .filter(process -> process.getBpmnProcessId().equals(TARGET_PROCESS_ID))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();
  }

  @Test
  void shouldBeAuthorizedToMigrateProcessInstanceWithDefaultUser() {
    // given
    final var processInstanceKey =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when migrate to a non-existing process as authorization checks should fail first
    // then
    final var response =
        defaultUserClient
            .newMigrateProcessInstanceCommand(processInstanceKey)
            .migrationPlan(targetProcDefKey)
            .addMappingInstruction(SOURCE_TASK, TARGET_TASK)
            .send()
            .join();

    // The Rest API returns a null future for an empty response
    // We can verify for null, as if we'd be unauthenticated we'd get an exception
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToMigrateProcessInstanceWithUser() {
    // given
    final var processInstanceKey =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_PROCESS_INSTANCE,
            List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username, password)) {
      // when migrate to a non-existing process as authorization checks should fail first
      // then
      final var response =
          client
              .newMigrateProcessInstanceCommand(processInstanceKey)
              .migrationPlan(targetProcDefKey)
              .addMappingInstruction(SOURCE_TASK, TARGET_TASK)
              .send()
              .join();

      // The Rest API returns a null future for an empty response
      // We can verify for null, as if we'd be unauthenticated we'd get an exception
      assertThat(response).isNull();
    }
  }

  @Test
  void shouldBeUnauthorizedToMigrateProcessInstanceIfNoPermissions() {
    // given
    final var processInstanceKey =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when migrate to a non-existing process as authorization checks should fail first
      // then
      final var response =
          client
              .newMigrateProcessInstanceCommand(processInstanceKey)
              .migrationPlan(targetProcDefKey)
              .addMappingInstruction(SOURCE_TASK, TARGET_TASK)
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION' with BPMN process id '%s'",
              PROCESS_ID);
    }
  }
}
