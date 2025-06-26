/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.net.http.HttpResponse;
import java.util.List;
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
          .withProperty("camunda.tasklist.identity.userAccessRestrictionsEnabled", true);

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";

  private static final String PROCESS_ID_3 = "processWithCandidateUsers";
  private static final BpmnModelInstance PROCESS_3 =
      Bpmn.createExecutableProcess(PROCESS_ID_3)
          .startEvent()
          .parallelGateway("startParallel")
          .userTask("user1Task")
          .zeebeCandidateUsers(USER1)
          .parallelGateway("endParallel")
          .moveToNode("startParallel")
          .userTask("user2Task")
          .zeebeCandidateUsers(USER2)
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
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_3))));

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_3))));

  private static long processInstanceKey;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, PROCESS_3, PROCESS_ID_3 + ".bpmn");
    final var startEvent = startProcessInstance(adminClient, PROCESS_ID_3);
    processInstanceKey = startEvent.getProcessInstanceKey();

    assertThat(
            RecordingExporter.processRecords(ProcessIntent.CREATED)
                .withBpmnProcessId(PROCESS_ID_3)
                .limit(1L)
                .count())
        .isOne();
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(PROCESS_ID_3)
                .limit(1L)
                .count())
        .isOne();
    //    assertThat(
    //            RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
    //                .withProcessInstanceKey(processInstanceKey)
    //                .limit(2L)
    //                .count())
    //        .isEqualTo(2);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2L)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void searchShouldReturnCandidateUserTasks(
      @Authenticated(USER1) final CamundaClient user1Client,
      @Authenticated(USER2) final CamundaClient user2Client)
      throws JsonProcessingException {
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
      final HttpResponse<String> user1Response =
          tasklistUser1RestClient.searchTasks(processInstanceKey);
      final HttpResponse<String> user2Response =
          tasklistUser2RestClient.searchTasks(processInstanceKey);

      // then
      assertThat(user1Response).isNotNull();
      assertThat(user2Response).isNotNull();
      assertThat(user1Response.statusCode()).isEqualTo(200);
      assertThat(user2Response.statusCode()).isEqualTo(200);

      final var tasksUser1 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user1Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser1).hasSize(1);
      final var tasksUser2 =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(
              user2Response.body(), TaskSearchResponse[].class);
      assertThat(tasksUser2).hasSize(1);
    }
  }
}
