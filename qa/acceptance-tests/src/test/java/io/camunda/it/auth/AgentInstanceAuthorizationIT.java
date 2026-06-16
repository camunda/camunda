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
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.OffsetDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class AgentInstanceAuthorizationIT {

  private static final String AGENT_ELEMENT_ID = "agentAuthElement";
  private static final String PROCESS_ID_1 = "agentAuthProcess1";
  private static final String PROCESS_ID_2 = "agentAuthProcess2";
  private static final String PROCESS_ID_3 = "agentAuthProcess3";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final String USER3 = "user3";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of("*"))));

  // user1 may read agent instances of PROCESS_ID_1 only
  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(
          USER1,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_1))));

  // user2 may read agent instances of PROCESS_ID_2 only
  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_2))));

  // user3 may update agent instances of PROCESS_ID_3 only
  @UserDefinition
  private static final TestUser USER3_USER =
      new TestUser(
          USER3,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of(PROCESS_ID_3))));

  private static long agentInstanceKey1;
  private static long agentInstanceKey2;
  private static long agentInstanceKey3;
  private static long elementInstanceKey1;
  private static long elementInstanceKey3;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var result1 = createAgentInstance(adminClient, PROCESS_ID_1);
    agentInstanceKey1 = result1.agentInstanceKey();
    elementInstanceKey1 = result1.elementInstanceKey();

    agentInstanceKey2 = createAgentInstance(adminClient, PROCESS_ID_2).agentInstanceKey();
    final var result3 = createAgentInstance(adminClient, PROCESS_ID_3);

    agentInstanceKey3 = result3.agentInstanceKey();
    elementInstanceKey3 = result3.elementInstanceKey();
    waitForAgentInstanceToBeIndexed(adminClient, agentInstanceKey1);
    waitForAgentInstanceToBeIndexed(adminClient, agentInstanceKey2);
    waitForAgentInstanceToBeIndexed(adminClient, agentInstanceKey3);
  }

  // ── search ────────────────────────────────────────────────────────────────

  @Test
  void searchShouldReturnOnlyAuthorizedAgentInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceSearchRequest().execute();

    // then
    assertThat(result.items())
        .singleElement()
        .satisfies(
            instance -> assertThat(instance.getAgentInstanceKey()).isEqualTo(agentInstanceKey1));
  }

  @Test
  void searchShouldNotReturnUnauthorizedAgentInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when — user1 filters explicitly on the process they cannot read
    final var result =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID_2))
            .execute();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void searchShouldReturnAllAgentInstancesForAdmin(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceSearchRequest().execute();

    // then — admin sees all three instances
    assertThat(result.items())
        .extracting(AgentInstance::getAgentInstanceKey)
        .containsExactlyInAnyOrder(agentInstanceKey1, agentInstanceKey2, agentInstanceKey3);
  }

  // ── getByKey ──────────────────────────────────────────────────────────────

  @Test
  void getByKeyShouldReturnAuthorizedAgentInstance(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceGetRequest(agentInstanceKey2).execute();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getAgentInstanceKey()).isEqualTo(agentInstanceKey2);
    assertThat(result.getProcessDefinitionId()).isEqualTo(PROCESS_ID_2);
  }

  @Test
  void getByKeyShouldReturn403ForUnauthorizedAgentInstance(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final ThrowingCallable executeGet =
        () -> camundaClient.newAgentInstanceGetRequest(agentInstanceKey2).execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION'");
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createShouldReturn403WhenUnauthorized(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 has READ_PROCESS_INSTANCE on PROCESS_ID_1 but not UPDATE_PROCESS_INSTANCE
    final ThrowingCallable execute =
        () ->
            camundaClient
                .newCreateAgentInstanceCommand()
                .elementInstanceKey(elementInstanceKey1)
                .model("gpt-4o")
                .provider("openai")
                .systemPrompt("You are a helpful assistant.")
                .execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(execute).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains(
            "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  void createShouldReturnExistingKeyWhenAlreadyExistsForAuthorizedUser(
      @Authenticated(USER3) final CamundaClient camundaClient) {
    // given — user3 has UPDATE_PROCESS_INSTANCE on PROCESS_ID_3, and an agent instance was
    // already created for elementInstanceKey3 by admin in setUp

    // when
    final var response =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey3)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .execute();

    // then — idempotent CREATE returns the existing key
    assertThat(response.getAgentInstanceKey()).isEqualTo(agentInstanceKey3);
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void updateShouldReturn403WhenUnauthorized(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 has READ_PROCESS_INSTANCE on PROCESS_ID_1 but not UPDATE_PROCESS_INSTANCE
    final ThrowingCallable execute =
        () ->
            camundaClient
                .newUpdateAgentInstanceCommand(agentInstanceKey1)
                .elementInstanceKey(elementInstanceKey1)
                .execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(execute).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail()).contains("'UPDATE_PROCESS_INSTANCE'");
  }

  @Test
  void updateShouldSucceedWhenUserHasUpdatePermission(
      @Authenticated(USER3) final CamundaClient camundaClient) {
    // given — user3 has UPDATE_PROCESS_INSTANCE on PROCESS_ID_3

    // when / then — no exception means the command was accepted
    assertThatNoException()
        .isThrownBy(
            () ->
                camundaClient
                    .newUpdateAgentInstanceCommand(agentInstanceKey3)
                    .elementInstanceKey(elementInstanceKey3)
                    .execute());
  }

  // ── createHistoryItem ─────────────────────────────────────────────────────

  @Test
  void createHistoryItemShouldReturn403WhenUnauthorized(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 has READ_PROCESS_INSTANCE on PROCESS_ID_1 but not UPDATE_PROCESS_INSTANCE
    final ThrowingCallable execute =
        () ->
            camundaClient
                .newCreateAgentHistoryItemCommand(agentInstanceKey1)
                .elementInstanceKey(elementInstanceKey1)
                .jobKey(elementInstanceKey1)
                .role(AgentHistoryRole.USER)
                .content(List.of(AgentHistoryContent.text("hello")))
                .producedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                .execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(execute).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail()).contains("'UPDATE_PROCESS_INSTANCE'");
  }

  @Test
  void createHistoryItemShouldPassAuthorizationForAuthorizedUser(
      @Authenticated(USER3) final CamundaClient camundaClient) {
    // given — user3 has UPDATE_PROCESS_INSTANCE on PROCESS_ID_3; there is no active job, so the
    // engine rejects with 404 after authorization, not 403
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateAgentHistoryItemCommand(agentInstanceKey3)
                    .elementInstanceKey(elementInstanceKey3)
                    .jobKey(elementInstanceKey3)
                    .role(AgentHistoryRole.USER)
                    .content(List.of(AgentHistoryContent.text("hello")))
                    .producedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                    .execute())
        .isInstanceOf(ProblemException.class)
        .satisfies(ex -> assertThat(((ProblemException) ex).code()).isNotEqualTo(403));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static AgentInstanceCreationResult createAgentInstance(
      final CamundaClient adminClient, final String processId) {
    final var processModel =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .endEvent()
            .done();

    deployProcessAndWaitForIt(adminClient, processModel, processId + ".bpmn");

    final var pi = startProcessInstance(adminClient, processId);
    final long processInstanceKey = pi.getProcessInstanceKey();

    waitForElementInstances(
        adminClient, f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey), 1);

    final var elementInstanceKey =
        adminClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    final var agentInstanceKey =
        adminClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .execute()
            .getAgentInstanceKey();

    return new AgentInstanceCreationResult(agentInstanceKey, elementInstanceKey);
  }

  private record AgentInstanceCreationResult(long agentInstanceKey, long elementInstanceKey) {}
}
