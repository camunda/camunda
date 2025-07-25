/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
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

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ElementInstanceTenancyIT {

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
  private static final String TENANT_B = "tenantB";
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
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    assignUserToTenant(adminClient, USER1, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_A);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);
    startProcessInstance(adminClient, PROCESS_ID, TENANT_B);
    waitForElementInstancesBeingExported(adminClient);
  }

  @Test
  public void shouldReturnAllElementInstancesWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newElementInstanceSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(4);
    assertThat(
            result.items().stream().map(ElementInstance::getTenantId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  public void shouldReturnOnlyTenantAElementInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newElementInstanceSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(
            result.items().stream().map(ElementInstance::getTenantId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(TENANT_A);
  }

  @Test
  public void shouldNotReturnAnyElementInstances(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newElementInstanceSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(0);
  }

  @Test
  void getByKeyShouldReturnTenantOwnedElementInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var elementInstanceKey = getElementInstanceKey(adminClient, PROCESS_ID, TENANT_A);
    // when
    final var result = camundaClient.newElementInstanceGetRequest(elementInstanceKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(result.getTenantId()).isEqualTo(TENANT_A);
  }

  @Test
  void getByKeyShouldThrowNotFoundException(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var elementInstanceKey = getElementInstanceKey(adminClient, PROCESS_ID, TENANT_A);

    // when
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () -> camundaClient.newElementInstanceGetRequest(elementInstanceKey).send().join())
            .actual();
    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Element Instance with key '%s' not found".formatted(elementInstanceKey));
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

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void waitForElementInstancesBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newElementInstanceSearchRequest()
                          .filter(filter -> filter.processDefinitionId(PROCESS_ID))
                          .send()
                          .join()
                          .items())
                  .hasSize(4);
            });
  }

  private long getElementInstanceKey(
      final CamundaClient camundaClient, final String processId, final String tenantId) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(
            f ->
                f.processDefinitionId(processId)
                    .tenantId(tenantId)
                    .state(ElementInstanceState.ACTIVE))
        .send()
        .join()
        .items()
        .getFirst()
        .getElementInstanceKey();
  }
}
