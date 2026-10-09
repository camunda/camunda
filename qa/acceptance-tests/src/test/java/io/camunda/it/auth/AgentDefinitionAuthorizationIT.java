/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForAgentDefinitionsToBeIndexed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class AgentDefinitionAuthorizationIT {

  private static final String AGENT_ELEMENT_ID = "agentDefinitionAuthElement";
  private static final String PROCESS_ID_1 = "agentDefinitionAuthProcess1";
  private static final String PROCESS_ID_2 = "agentDefinitionAuthProcess2";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";

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
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));

  // user1 may read agent definitions of PROCESS_ID_1 only
  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(
          USER1,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of(PROCESS_ID_1))));

  // user2 may read agent definitions of PROCESS_ID_2 only
  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of(PROCESS_ID_2))));

  private static long agentDefinitionKey1;
  private static long agentDefinitionKey2;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var processDefinitionKey1 = deployAgentProcess(adminClient, PROCESS_ID_1);
    final var processDefinitionKey2 = deployAgentProcess(adminClient, PROCESS_ID_2);

    waitForAgentDefinitionsToBeIndexed(
        adminClient, f -> f.processDefinitionKey(processDefinitionKey1), 1);
    waitForAgentDefinitionsToBeIndexed(
        adminClient, f -> f.processDefinitionKey(processDefinitionKey2), 1);

    agentDefinitionKey1 = fetchAgentDefinitionKey(adminClient, processDefinitionKey1);
    agentDefinitionKey2 = fetchAgentDefinitionKey(adminClient, processDefinitionKey2);
  }

  // ── search ────────────────────────────────────────────────────────────────

  @Test
  void searchShouldReturnOnlyAuthorizedAgentDefinitions(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then
    assertThat(result.items())
        .as("user1 is only authorized to read PROCESS_ID_1, so search must not leak PROCESS_ID_2")
        .singleElement()
        .satisfies(
            definition ->
                assertThat(definition.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey1));
  }

  @Test
  void searchShouldNotReturnUnauthorizedAgentDefinitions(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when — user1 filters explicitly on the process they cannot read
    final var result =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID_2))
            .execute();

    // then
    assertThat(result.items())
        .as("filtering on an unauthorized process should yield no results, not an error")
        .isEmpty();
  }

  @Test
  void searchShouldReturnAllAgentDefinitionsForAdmin(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then — admin sees both agent definitions
    assertThat(result.items())
        .as("admin has read access to every process, so both agent definitions should be visible")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .containsExactlyInAnyOrder(agentDefinitionKey1, agentDefinitionKey2);
  }

  // ── getByKey ──────────────────────────────────────────────────────────────

  @Test
  void getByKeyShouldReturnAuthorizedAgentDefinition(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionGetRequest(agentDefinitionKey2).execute();

    // then
    assertThat(result).as("user2 should be able to fetch a definition of PROCESS_ID_2").isNotNull();
    assertThat(result.getAgentDefinitionKey())
        .as("the fetched agent definition should be the requested one")
        .isEqualTo(agentDefinitionKey2);
    assertThat(result.getProcessDefinitionId())
        .as("the fetched agent definition should belong to PROCESS_ID_2")
        .isEqualTo(PROCESS_ID_2);
  }

  @Test
  void getByKeyShouldReturn403ForUnauthorizedAgentDefinition(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final ThrowingCallable executeGet =
        () -> camundaClient.newAgentDefinitionGetRequest(agentDefinitionKey2).execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code())
        .as(
            "user1 lacks READ_PROCESS_DEFINITION on PROCESS_ID_2, so the request should be forbidden")
        .isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .as("problem detail should name the missing permission and resource type")
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource"
                + " 'PROCESS_DEFINITION'");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static long deployAgentProcess(final CamundaClient adminClient, final String processId) {
    final var processModel =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType("agent-definition-auth-job-" + processId)
            .zeebeAiAgentSubProcessDefinition()
            .endEvent()
            .done();

    return deployProcessAndWaitForIt(adminClient, processModel, processId + ".bpmn")
        .getProcessDefinitionKey();
  }

  private static long fetchAgentDefinitionKey(
      final CamundaClient client, final long processDefinitionKey) {
    return client
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.processDefinitionKey(processDefinitionKey))
        .execute()
        .items()
        .getFirst()
        .getAgentDefinitionKey();
  }
}
