/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.USER_TASK;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
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
 * Integration test for candidate group authorization after 8.8 migration.
 *
 * <p>This test verifies that users can access tasks assigned to their candidate groups when:
 *
 * <ul>
 *   <li>A group has different groupId and groupName (as happens after 8.7→8.8 migration)
 *   <li>A BPMN process references the group by its name in candidateGroups
 *   <li>The engine should resolve group names to IDs at user task creation time
 * </ul>
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserTaskCandidateGroupAuthorizationIT {
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID_WITH_GROUP_NAME = "processWithGroupName";
  private static final String USER_IN_MIGRATED_GROUP = "userInMigratedGroup";

  // Simulating 8.7→8.8 migration: groupId is slugified, name is the original
  private static final String GROUP_ID = "front_support_retail_ch"; // slugified ID
  private static final String GROUP_NAME = "Front Support Retail CH"; // original name

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
  static void setUp(final CamundaClient adminClient) {
    // Deploy BPMN process that uses the group NAME in candidateGroups (as was done in 8.7)
    deployResource(adminClient, "process/process_with_group_name.bpmn");

    // Start instance of the process
    startProcessInstance(adminClient, PROCESS_ID_WITH_GROUP_NAME);

    waitForProcessAndTasksBeingExported(adminClient);
  }

  @Test
  void shouldFindTaskWhenBpmnUsesGroupName(
      @Authenticated(USER_IN_MIGRATED_GROUP) final CamundaClient camundaClient) {
    // given - BPMN uses group NAME "Front Support Retail CH"
    // and - user is a member of group with ID "front_support_retail_ch"

    // when - user searches (property-based authorization using their group ID)
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - should find the task because the engine resolved group name to ID at creation time
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .contains(PROCESS_ID_WITH_GROUP_NAME);
  }

  private static void waitForProcessAndTasksBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newUserTaskSearchRequest().send().join().items()).hasSize(1);
            });
  }
}
