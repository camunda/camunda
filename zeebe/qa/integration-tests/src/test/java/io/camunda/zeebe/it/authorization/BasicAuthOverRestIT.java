/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
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
final class BasicAuthOverRestIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static CamundaClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
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
  }

  @Test
  void shouldBeAuthorizedWithDefaultUser() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when then
    final var deploymentEvent =
        defaultUserClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();
    assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  }

  @Test
  void shouldBeAuthorizedWithUserThatIsGrantedPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(ResourceTypeEnum.DEPLOYMENT, PermissionTypeEnum.CREATE, List.of("*")));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var deploymentEvent =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  "process.bpmn")
              .send()
              .join();

      // then
      assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
    }
  }

  @Test
  void shouldBeUnauthorizedWithUserThatIsNotGrantedPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    // when
    try (final var client = authUtil.createClient(username, password)) {
      final var deployFuture =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  "process.bpmn")
              .send();

      // then
      assertThatThrownBy(deployFuture::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: FORBIDDEN")
          .hasMessageContaining("status: 403")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'CREATE' on resource 'DEPLOYMENT'");
    }
  }

  //  @Test
  //  void shouldCorrectlyAddAndRemovePermissionsStepByStep() {
  //    // given
  //    final var processInstanceKey = createProcessInstance(PROCESS_ID);
  //    final var processInstanceKey2 = createProcessInstance(PROCESS_ID_2);
  //    final var userTaskKey = getUserTaskKey(processInstanceKey);
  //    final var userTaskKey2 = getUserTaskKey(processInstanceKey2);
  //    final var username = UUID.randomUUID().toString();
  //    final var password = "password";
  //    final long user = authUtil.createUser(username, password);
  //
  //    try (final var client = authUtil.createClient(username, password)) {
  //      // Step 1: Add permissions for two resources
  //      authUtil
  //          .getDefaultClient()
  //          .newAddPermissionsCommand(user)
  //          .resourceType(PROCESS_DEFINITION)
  //          .permission(UPDATE_USER_TASK)
  //          .resourceIds(List.of(PROCESS_ID, PROCESS_ID_2))
  //          .send()
  //          .join();
  //
  //      // Step 2: Verify permissions work
  //      client
  //          .newUserTaskAssignCommand(userTaskKey)
  //          .assignee("assignee")
  //          .allowOverride(true)
  //          .send()
  //          .join();
  //      client
  //          .newUserTaskAssignCommand(userTaskKey2)
  //          .assignee("assignee")
  //          .allowOverride(true)
  //          .send()
  //          .join();
  //
  //      // Step 3: Remove permission for the first resource
  //      authUtil
  //          .getDefaultClient()
  //          .newRemovePermissionsCommand(user)
  //          .resourceType(PROCESS_DEFINITION)
  //          .permission(UPDATE_USER_TASK)
  //          .resourceIds(List.of(PROCESS_ID))
  //          .send()
  //          .join();
  //
  //      // Step 4: Verify permission still works for the second resource
  //      client
  //          .newUserTaskAssignCommand(userTaskKey2)
  //          .assignee("assignee")
  //          .allowOverride(true)
  //          .send()
  //          .join();
  //
  //      // Step 5: Verify unauthorized exception after first permission removed
  //      final var response =
  //          client
  //              .newUserTaskAssignCommand(userTaskKey)
  //              .assignee("assignee")
  //              .allowOverride(true)
  //              .send();
  //
  //      // then
  //      assertThatThrownBy(response::join)
  //          .isInstanceOf(ProblemException.class)
  //          .hasMessageContaining("title: FORBIDDEN")
  //          .hasMessageContaining("status: 403")
  //          .hasMessageContaining(
  //              "Command 'ASSIGN' rejected with code 'FORBIDDEN': Insufficient permissions to
  // perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource
  // identifiers are one of '[*, processId]'");
  //
  //      // Step 6: Remove permission for the remaining resource
  //      authUtil
  //          .getDefaultClient()
  //          .newRemovePermissionsCommand(user)
  //          .resourceType(PROCESS_DEFINITION)
  //          .permission(UPDATE_USER_TASK)
  //          .resourceIds(List.of(PROCESS_ID_2))
  //          .send()
  //          .join();
  //
  //      // Step 6: Verify unauthorized exception after all permissions are removed
  //      final var response2 =
  //          client
  //              .newUserTaskAssignCommand(userTaskKey)
  //              .assignee("assignee")
  //              .allowOverride(true)
  //              .send();
  //
  //      // then
  //      assertThatThrownBy(response2::join)
  //          .isInstanceOf(ProblemException.class)
  //          .hasMessageContaining("title: FORBIDDEN")
  //          .hasMessageContaining("status: 403")
  //          .hasMessageContaining(
  //              "Command 'ASSIGN' rejected with code 'FORBIDDEN': Insufficient permissions to
  // perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource
  // identifiers are one of '[*, processId]");
  //    }
  //  }
  //
  //  private long createProcessInstance(final String bpmnProcessId) {
  //    return defaultUserClient
  //        .newCreateInstanceCommand()
  //        .bpmnProcessId(bpmnProcessId)
  //        .latestVersion()
  //        .send()
  //        .join()
  //        .getProcessInstanceKey();
  //  }
  //
  //  private static long getUserTaskKey(final long processInstanceKey) {
  //    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
  //        .withProcessInstanceKey(processInstanceKey)
  //        .withElementId(USER_TASK_ID)
  //        .limit(1)
  //        .findFirst()
  //        .orElseThrow()
  //        .getKey();
  //  }
}
