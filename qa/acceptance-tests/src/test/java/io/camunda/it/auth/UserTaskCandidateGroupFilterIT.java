/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for Tasklist V2 candidate group filter issue after 8.8 migration.
 *
 * <p>This test reproduces the scenario where:
 *
 * <ul>
 *   <li>A group has different groupId and groupName (as happens after 8.7→8.8 migration)
 *   <li>A BPMN process references the group by its name in candidateGroups
 *   <li>A user who is a member of that group tries to search/filter tasks
 *   <li>The filter should work whether the BPMN uses the group name or group ID
 * </ul>
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserTaskCandidateGroupFilterIT {
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID_WITH_GROUP_NAME = "processWithGroupName";
  private static final String PROCESS_ID_WITH_GROUP_ID = "processWithGroupId";
  private static final String ADMIN = "admin";
  private static final String USER_IN_MIGRATED_GROUP = "userInMigratedGroup";

  // Simulating 8.7→8.8 migration: groupId is slugified, name is the original
  private static final String GROUP_ID = "front_support_retail_ch"; // slugified ID
  private static final String GROUP_NAME = "Front Support Retail CH"; // original name

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              Permissions.withWildcard(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE),
              Permissions.withWildcard(PROCESS_DEFINITION, READ_PROCESS_DEFINITION),
              Permissions.withWildcard(USER_TASK, READ)));

  @UserDefinition
  private static final TestUser USER_IN_MIGRATED_GROUP_USER =
      new TestUser(
          USER_IN_MIGRATED_GROUP,
          "password",
          List.of(
              // User has permission to read user tasks by candidateGroups property
              Permissions.withPropertyName(USER_TASK, READ, "candidateGroups")));

  @GroupDefinition
  private static final TestGroup MIGRATED_GROUP =
      TestGroup.withoutPermissions(
          GROUP_ID, // The ID (slugified in 8.8)
          GROUP_NAME, // The display name (original from 8.7)
          List.of(new Membership(USER_IN_MIGRATED_GROUP, EntityType.USER)));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Deploy BPMN process that uses the group NAME in candidateGroups (as was done in 8.7)
    deployResource(adminClient, "process/process_with_group_name.bpmn");
    // Deploy BPMN process that uses the group ID in candidateGroups (as might be done in 8.8+)
    deployResource(adminClient, "process/process_with_group_id.bpmn");

    // Start instances of both processes
    startProcessInstance(adminClient, PROCESS_ID_WITH_GROUP_NAME);
    startProcessInstance(adminClient, PROCESS_ID_WITH_GROUP_ID);

    waitForProcessAndTasksBeingExported(adminClient);
  }

  @Test
  void shouldFindTaskWhenBpmnUsesGroupNameAndUserFiltersWithGroupId(
      @Authenticated(USER_IN_MIGRATED_GROUP) final CamundaClient camundaClient) {
    // given - BPMN uses group NAME "Front Support Retail CH"
    // when - user searches (their authenticated groups contain group ID "front_support_retail_ch")
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - should find the task because the system resolves group ID to name
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .contains(PROCESS_ID_WITH_GROUP_NAME);
  }

  @Test
  void shouldFindTaskWhenBpmnUsesGroupIdAndUserFiltersWithGroupId(
      @Authenticated(USER_IN_MIGRATED_GROUP) final CamundaClient camundaClient) {
    // given - BPMN uses group ID "front_support_retail_ch"
    // when - user searches (their authenticated groups contain group ID "front_support_retail_ch")
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - should find the task (exact match on group ID)
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .contains(PROCESS_ID_WITH_GROUP_ID);
  }

  @Test
  void shouldFindBothTasksWhenUserIsInGroup(
      @Authenticated(USER_IN_MIGRATED_GROUP) final CamundaClient camundaClient) {
    // when - user searches without explicit filter
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - should find both tasks (one using group name, one using group ID)
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .containsExactlyInAnyOrder(PROCESS_ID_WITH_GROUP_NAME, PROCESS_ID_WITH_GROUP_ID);
  }

  @Test
  void shouldFindTaskWhenExplicitlyFilteringByGroupId(
      @Authenticated(USER_IN_MIGRATED_GROUP) final CamundaClient camundaClient) {
    // when - user explicitly filters by group ID (what the UI does in V2)
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.candidateGroup(GROUP_ID))
            .send()
            .join();

    // then - should find tasks that use either the group ID or group name in BPMN
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .containsExactlyInAnyOrder(PROCESS_ID_WITH_GROUP_NAME, PROCESS_ID_WITH_GROUP_ID);
  }

  @Test
  void shouldFindTaskWhenExplicitlyFilteringByGroupName(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when - user explicitly filters by group NAME (what might have worked in 8.7)
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.candidateGroup(GROUP_NAME))
            .send()
            .join();

    // then - should find the task that uses the group name
    assertThat(result.items())
        .singleElement()
        .extracting(UserTask::getBpmnProcessId)
        .isEqualTo(PROCESS_ID_WITH_GROUP_NAME);
  }

  private static void deployResource(final CamundaClient camundaClient, final String resourceName) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private static void waitForProcessAndTasksBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newUserTaskSearchRequest().send().join().items()).hasSize(2);
            });
  }
}
