/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.task;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UserTaskRestrictionsIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withAdditionalProfile(Profile.TASKLIST)
          .withProperty("camunda.tasklist.identity.userAccessRestrictionsEnabled", true);

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final String USER3 = "user3";
  private static final String USER4 = "user4";
  private static final String USER5 = "user5";
  private static final String GROUP1 = "group1";
  private static final String GROUP2 = "group2";
  private static final String GROUP3_ID = "group3";
  private static final String GROUP3_NAME = "Group 3";

  private static final String PROCESS_ID_1 = "processWithCandidateUsers";
  private static final BpmnModelInstance PROCESS_1 =
      Bpmn.createExecutableProcess(PROCESS_ID_1)
          .startEvent()
          .parallelGateway("startParallel")
          .userTask("user1Task")
          .zeebeUserTask()
          .zeebeCandidateUsers(USER1)
          .parallelGateway("endParallel")
          .moveToNode("startParallel")
          .userTask("user2Task")
          .zeebeUserTask()
          .zeebeCandidateUsers(USER2)
          .done();

  private static final String PROCESS_ID_2 = "processWithCandidateGroups";
  private static final BpmnModelInstance PROCESS_2 =
      Bpmn.createExecutableProcess(PROCESS_ID_2)
          .startEvent()
          .parallelGateway("startParallel")
          .userTask("group1Task")
          .zeebeUserTask()
          .zeebeCandidateGroups(GROUP1)
          .endEvent()
          .moveToNode("startParallel")
          .userTask("group2Task")
          .zeebeUserTask()
          .zeebeCandidateGroups(GROUP2)
          .moveToNode("startParallel")
          .userTask("group3Task")
          .zeebeUserTask()
          .zeebeCandidateGroups(GROUP3_NAME)
          .done();

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(
          USER1,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser USER3_USER = new TestUser(USER3, "password", List.of());

  @UserDefinition
  private static final TestUser USER4_USER = new TestUser(USER4, "password", List.of());

  @UserDefinition
  private static final TestUser USER5_USER = new TestUser(USER5, "password", List.of());

  @GroupDefinition
  private static final TestGroup GROUP1_GROUP =
      new TestGroup(
          GROUP1,
          GROUP1,
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))),
          List.of(new Membership(USER3, EntityType.USER)));

  @GroupDefinition
  private static final TestGroup GROUP2_GROUP =
      new TestGroup(
          GROUP2,
          GROUP2,
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))),
          List.of(new Membership(USER4, EntityType.USER)));

  @GroupDefinition
  private static final TestGroup GROUP3_GROUP =
      new TestGroup(
          GROUP3_ID,
          GROUP3_NAME,
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))),
          List.of(new Membership(USER5, EntityType.USER)));

  private static long processInstanceKeyCandidateUsers;
  private static long processInstanceKeyCandidateGroups;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, PROCESS_1, PROCESS_ID_1 + ".bpmn");
    deployResource(adminClient, PROCESS_2, PROCESS_ID_2 + ".bpmn");
    final var startEvent1 = startProcessInstance(adminClient, PROCESS_ID_1);
    final var startEvent2 = startProcessInstance(adminClient, PROCESS_ID_2);
    processInstanceKeyCandidateUsers = startEvent1.getProcessInstanceKey();
    processInstanceKeyCandidateGroups = startEvent2.getProcessInstanceKey();

    awaitUserTaskBeingAvailable(adminClient, processInstanceKeyCandidateUsers, 2);
    awaitUserTaskBeingAvailable(adminClient, processInstanceKeyCandidateGroups, 3);
  }

  @Test
  public void searchShouldReturnCandidateUserTasks() throws JsonProcessingException {
    // given
    try (final var tasklistUser1RestClient =
            STANDALONE_CAMUNDA
                .newTasklistClient()
                .withAuthentication(USER1_USER.username(), USER1_USER.password());
        final var tasklistUser2RestClient =
            STANDALONE_CAMUNDA
                .newTasklistClient()
                .withAuthentication(USER2_USER.username(), USER2_USER.password()); ) {

      // when
      // searching for tasks available to user1 and user2 (no process instance key provided)
      final HttpResponse<String> user1Response = tasklistUser1RestClient.searchTasks(null);
      final HttpResponse<String> user2Response = tasklistUser2RestClient.searchTasks(null);

      // then
      assertThat(user1Response).isNotNull();
      assertThat(user2Response).isNotNull();
      assertThat(user1Response.statusCode()).isEqualTo(200);
      assertThat(user2Response.statusCode()).isEqualTo(200);

      final TaskSearchResponse[] tasksUser1 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user1Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser1).hasSize(1);
      assertThat(tasksUser1[0].getCandidateUsers()).containsExactly(USER1);
      final var tasksUser2 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user2Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser2).hasSize(1);
      assertThat(tasksUser2[0].getCandidateUsers()).containsExactly(USER2);
    }
  }

  @Test
  public void searchShouldReturnCandidateGroupTasks() throws JsonProcessingException {
    // given
    try (final var tasklistUser3RestClient =
            STANDALONE_CAMUNDA
                .newTasklistClient()
                .withAuthentication(USER3_USER.username(), USER3_USER.password());
        final var tasklistUser4RestClient =
            STANDALONE_CAMUNDA
                .newTasklistClient()
                .withAuthentication(USER4_USER.username(), USER4_USER.password());
        final var tasklistUser5RestClient =
            STANDALONE_CAMUNDA
                .newTasklistClient()
                .withAuthentication(USER5_USER.username(), USER5_USER.password()); ) {

      // when
      // searching for tasks available to user1 and user2 (no process instance key provided)
      final HttpResponse<String> user3Response = tasklistUser3RestClient.searchTasks(null);
      final HttpResponse<String> user4Response = tasklistUser4RestClient.searchTasks(null);
      final HttpResponse<String> user5Response = tasklistUser5RestClient.searchTasks(null);

      // then
      assertThat(user3Response).isNotNull();
      assertThat(user4Response).isNotNull();
      assertThat(user5Response).isNotNull();
      assertThat(user3Response.statusCode()).isEqualTo(200);
      assertThat(user4Response.statusCode()).isEqualTo(200);
      assertThat(user5Response.statusCode()).isEqualTo(200);

      final TaskSearchResponse[] tasksUser3 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user3Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser3).hasSize(1);
      assertThat(tasksUser3[0].getCandidateGroups()).containsExactly(GROUP1);
      final var tasksUser4 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user4Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser4).hasSize(1);
      assertThat(tasksUser4[0].getCandidateGroups()).containsExactly(GROUP2);
      final var tasksUser5 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user5Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser5).hasSize(1);
      assertThat(tasksUser5[0].getCandidateGroups()).containsExactly(GROUP3_NAME);
    }
  }

  public static void awaitUserTaskBeingAvailable(
      final CamundaClient camundaClient, final long processInstanceKey, final int count) {
    Awaitility.await("should create user tasks")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(count);
            });
  }
}
