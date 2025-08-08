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
import io.camunda.client.api.response.Decision;
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
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DecisionDefinitionTenancyIT {

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

    deployResource(adminClient, "decisions/decision_model.dmn", TENANT_A);
    deployResource(adminClient, "decisions/decision_model_1.dmn", TENANT_B);

    waitForDecisionDefinitionsToBeDeployed(adminClient, 2);
  }

  @Test
  public void shouldReturnAllDecisionDefinitionsWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newDecisionDefinitionSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(Decision::getTenantId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  public void shouldReturnOnlyTenantADecisionDefinition(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newDecisionDefinitionSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(Decision::getTenantId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(TENANT_A);
  }

  @Test
  public void shouldNotReturnAnyDecisionDefinition(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newDecisionDefinitionSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(0);
  }

  @Test
  void getByKeyShouldReturnTenantOwnedDecisionDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var decisionDefinition =
        adminClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.tenantId(TENANT_A))
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();

    // when
    final var result =
        camundaClient
            .newDecisionDefinitionGetRequest(decisionDefinition.getDecisionKey())
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getDecisionKey()).isEqualTo(decisionDefinition.getDecisionKey());
    assertThat(result.getTenantId()).isEqualTo(TENANT_A);
  }

  @Test
  void getByKeyShouldThrowNotFoundException(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var decisionDefinition =
        adminClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.tenantId(TENANT_A))
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();

    // when
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newDecisionDefinitionGetRequest(decisionDefinition.getDecisionKey())
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains(
            "Decision Definition with key '%s' not found"
                .formatted(decisionDefinition.getDecisionKey()));
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

  private static void waitForDecisionDefinitionsToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionDefinitionSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
