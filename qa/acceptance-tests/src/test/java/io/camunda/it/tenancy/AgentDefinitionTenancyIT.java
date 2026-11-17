/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.deployProcessForTenantAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForAgentDefinitionsToBeIndexed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AgentDefinitionTenancyIT {

  private static final String AGENT_ELEMENT_ID = "agentDefinitionTenancyElement";
  private static final String PROCESS_ID = "agentDefinitionTenancyProcess";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  /** user1 is assigned to TENANT_A only. */
  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  /** user2 is not assigned to any tenant. */
  @UserDefinition
  private static final TestUser USER2_USER = new TestUser(USER2, "password", List.of());

  private static long agentDefinitionKeyA;
  private static long agentDefinitionKeyB;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    assignUserToTenant(adminClient, USER1, TENANT_A);

    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType("agent-definition-tenancy-job")
            .zeebeAiAgentSubProcessDefinition()
            .endEvent()
            .done();

    // Deploy the same process in both tenants; agent definitions are created at deploy time.
    deployProcessForTenant(adminClient, processModel, TENANT_A);
    deployProcessForTenant(adminClient, processModel, TENANT_B);

    waitForAgentDefinitionsToBeIndexed(adminClient, f -> f.tenantId(TENANT_A), 1);
    waitForAgentDefinitionsToBeIndexed(adminClient, f -> f.tenantId(TENANT_B), 1);

    agentDefinitionKeyA = fetchAgentDefinitionKey(adminClient, TENANT_A);
    agentDefinitionKeyB = fetchAgentDefinitionKey(adminClient, TENANT_B);
  }

  // ── search ────────────────────────────────────────────────────────────────

  @Test
  void searchShouldReturnAllAgentDefinitionsForAdminWithBothTenants(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then
    assertThat(result.items())
        .as("admin can see agent definitions from every tenant they're assigned to")
        .hasSize(2);
    assertThat(result.items().stream().map(ad -> ad.getTenantId()).toList())
        .as("the two returned agent definitions should each belong to a different tenant")
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  void searchShouldReturnOnlyTenantAAgentDefinitionsForUser1(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then
    assertThat(result.items())
        .as("user1 is only assigned to TENANT_A, so search must not leak TENANT_B's definition")
        .hasSize(1);
    assertThat(result.items().getFirst().getTenantId())
        .as("the single visible agent definition should belong to TENANT_A")
        .isEqualTo(TENANT_A);
    assertThat(result.items().getFirst().getAgentDefinitionKey())
        .as("the single visible agent definition should be TENANT_A's own")
        .isEqualTo(agentDefinitionKeyA);
  }

  @Test
  void searchShouldReturnNoAgentDefinitionsForUserWithNoTenantAccess(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then
    assertThat(result.items())
        .as("user2 has no tenant assignment, so search must return nothing")
        .isEmpty();
  }

  // ── getByKey ──────────────────────────────────────────────────────────────

  @Test
  void getByKeyShouldReturnAgentDefinitionWithinAccessibleTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentDefinitionGetRequest(agentDefinitionKeyA).execute();

    // then
    assertThat(result).as("user1 should be able to fetch a definition within TENANT_A").isNotNull();
    assertThat(result.getAgentDefinitionKey())
        .as("the fetched agent definition should be the requested one")
        .isEqualTo(agentDefinitionKeyA);
    assertThat(result.getTenantId())
        .as("the fetched agent definition should belong to TENANT_A")
        .isEqualTo(TENANT_A);
  }

  @Test
  void getByKeyShouldReturn404ForAgentDefinitionOutsideAccessibleTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when — user1 has no access to TENANT_B
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () -> camundaClient.newAgentDefinitionGetRequest(agentDefinitionKeyB).execute())
            .actual();

    // then — tenant boundary surfaced as 404, not 403
    assertThat(exception.getMessage())
        .as("a definition outside the caller's tenant should surface as 404, not 403")
        .startsWith("Failed with code 404");
    assertThat(exception.details()).as("problem detail should be present").isNotNull();
    assertThat(exception.details().getTitle()).as("problem title").isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).as("problem status").isEqualTo(404);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static long fetchAgentDefinitionKey(final CamundaClient client, final String tenantId) {
    return client
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.tenantId(tenantId))
        .execute()
        .items()
        .getFirst()
        .getAgentDefinitionKey();
  }

  private static void createTenant(final CamundaClient client, final String tenantId) {
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient client, final String username, final String tenantId) {
    client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
  }

  private static void deployProcessForTenant(
      final CamundaClient client, final BpmnModelInstance model, final String tenantId) {
    final String filename = PROCESS_ID + "-" + tenantId + ".bpmn";
    deployProcessForTenantAndWaitForIt(client, model, filename, tenantId);
  }
}
