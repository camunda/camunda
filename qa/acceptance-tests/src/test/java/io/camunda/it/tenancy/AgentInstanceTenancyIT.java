/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.deployProcessForTenantAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AgentInstanceTenancyIT {

  private static final String AGENT_ELEMENT_ID = "agentTenancyElement";
  private static final String PROCESS_ID = "agentTenancyProcess";
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

  private static long agentInstanceKeyA;
  private static long agentInstanceKeyB;
  private static long elementInstanceKeyB;
  private static long jobKeyB;

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
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .endEvent()
            .done();

    // Deploy the same process in both tenants
    deployProcessForTenant(adminClient, processModel, TENANT_A);
    deployProcessForTenant(adminClient, processModel, TENANT_B);

    agentInstanceKeyA = createAgentInstance(adminClient, TENANT_A);
    final var resultB = createAgentInstanceWithResult(adminClient, TENANT_B);
    agentInstanceKeyB = resultB.agentInstanceKey();
    elementInstanceKeyB = resultB.elementInstanceKey();
    jobKeyB = resultB.jobKey();

    waitForAgentInstanceToBeIndexed(adminClient, agentInstanceKeyA);
    waitForAgentInstanceToBeIndexed(adminClient, agentInstanceKeyB);
  }

  // ── search ────────────────────────────────────────────────────────────────

  @Test
  void searchShouldReturnAllAgentInstancesForAdminWithBothTenants(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceSearchRequest().execute();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(ai -> ai.getTenantId()).toList())
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  void searchShouldReturnOnlyTenantAAgentInstancesForUser1(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceSearchRequest().execute();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(TENANT_A);
    assertThat(result.items().getFirst().getAgentInstanceKey()).isEqualTo(agentInstanceKeyA);
  }

  @Test
  void searchShouldReturnNoAgentInstancesForUserWithNoTenantAccess(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceSearchRequest().execute();

    // then
    assertThat(result.items()).isEmpty();
  }

  // ── getByKey ──────────────────────────────────────────────────────────────

  @Test
  void getByKeyShouldReturnAgentInstanceWithinAccessibleTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newAgentInstanceGetRequest(agentInstanceKeyA).execute();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getAgentInstanceKey()).isEqualTo(agentInstanceKeyA);
    assertThat(result.getTenantId()).isEqualTo(TENANT_A);
  }

  @Test
  void getByKeyShouldReturn404ForAgentInstanceOutsideAccessibleTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when — user1 has no access to TENANT_B
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newAgentInstanceGetRequest(agentInstanceKeyB).execute())
            .actual();

    // then — tenant boundary surfaced as 404, not 403
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Agent Instance with key '%d' not found".formatted(agentInstanceKeyB));
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createShouldReturn404ForCrossTenantRequest(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 belongs to TENANT_A only; elementInstanceKeyB is in TENANT_B
    // with withAuthenticatedAccess() the engine surfaces cross-tenant violations as 404,
    // consistent with how getByKey hides cross-tenant records
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newCreateAgentInstanceCommand()
                        .elementInstanceKey(elementInstanceKeyB)
                        .model("gpt-4o")
                        .provider("openai")
                        .systemPrompt("You are a helpful assistant.")
                        .execute())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void updateShouldReturn404ForCrossTenantRequest(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 belongs to TENANT_A only; agentInstanceKeyB is in TENANT_B
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newUpdateAgentInstanceCommand(agentInstanceKeyB)
                        .elementInstanceKey(elementInstanceKeyB)
                        .execute())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  // ── createHistoryItem ─────────────────────────────────────────────────────

  @Test
  void createHistoryItemShouldReturn404ForCrossTenantRequest(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given — user1 belongs to TENANT_A only; agentInstanceKeyB is in TENANT_B
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newCreateAgentHistoryItemCommand(agentInstanceKeyB)
                        .elementInstanceKey(elementInstanceKeyB)
                        .jobKey(jobKeyB)
                        .role(AgentHistoryRole.USER)
                        .content(List.of(AgentHistoryContent.text("hello")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                        .execute())
            .actual();

    // then — tenant boundary surfaced as 404, not 403
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

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

  private static long createAgentInstance(final CamundaClient client, final String tenantId) {
    return createAgentInstanceWithResult(client, tenantId).agentInstanceKey();
  }

  private static AgentInstanceCreationResult createAgentInstanceWithResult(
      final CamundaClient client, final String tenantId) {
    // Start process instance in the given tenant
    final var pi =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .tenantId(tenantId)
            .execute();
    final long processInstanceKey = pi.getProcessInstanceKey();

    waitForElementInstances(
        client, f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey), 1);

    final var elementInstanceKey =
        client
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    final var agentInstanceKey =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .execute()
            .getAgentInstanceKey();

    final long jobKey =
        waitForJobs(client, List.of(processInstanceKey)).stream()
            .filter(j -> JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX.equals(j.getType()))
            .findFirst()
            .map(Job::getJobKey)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "No agent job found for process instance " + processInstanceKey));

    return new AgentInstanceCreationResult(agentInstanceKey, elementInstanceKey, jobKey);
  }

  private record AgentInstanceCreationResult(
      long agentInstanceKey, long elementInstanceKey, long jobKey) {}
}
