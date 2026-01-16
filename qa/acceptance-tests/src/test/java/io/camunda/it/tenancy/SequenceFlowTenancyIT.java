/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@Execution(ExecutionMode.SAME_THREAD)
public class SequenceFlowTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final String TENANT_A = "tenantA";
  private static final String PROCESS_ID = "service_tasks_v1";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  @UserDefinition
  private static final TestUser USER2_USER = new TestUser(USER2, "password", List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, USER1, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    final var processInstance1 = startProcessInstance(adminClient, PROCESS_ID, TENANT_A);

    waitForSequenceFlowsExported(adminClient, processInstance1.getProcessInstanceKey());
  }

  @Test
  void shouldReturnTenantOwnedSequenceFlows(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID, TENANT_A);
    // when
    final var result =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();
    // then
    assertThat(result).hasSize(1);
    assertThat(
            result.stream()
                .map(ProcessInstanceSequenceFlow::getTenantId)
                .collect(Collectors.toSet()))
        .containsExactly(TENANT_A);
  }

  @Test
  void shouldReturnNoSequenceFlows(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID, TENANT_A);
    // when
    final var result =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();
    // then
    assertThat(result).hasSize(0);
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void deployResource(
      final CamundaClient camundaClient, final String resourceName, final String tenant) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenant)
        .send()
        .join();
  }

  private static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void waitForSequenceFlowsExported(
      final CamundaClient camundaClient, final long processInstanceKey) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newProcessInstanceSearchRequest()
                          .filter(f -> f.processInstanceKey(processInstanceKey))
                          .send()
                          .join()
                          .items())
                  .hasSize(1);
              assertThat(
                      camundaClient
                          .newProcessInstanceSequenceFlowsRequest(processInstanceKey)
                          .send()
                          .join())
                  .hasSize(1);
            });
  }

  private long getProcessInstanceKey(
      final CamundaClient camundaClient, final String processId, final String tenantId) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId).tenantId(tenantId))
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessInstanceKey();
  }
}
