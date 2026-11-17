/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.startProcessInstanceForTenant;
import static io.camunda.it.util.TestHelper.waitForVariablesBeingExported;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class VariableTenancyIT {

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
  private static final String PROCESS_ID = "bpmProcessVariable";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  @UserDefinition
  private static final TestUser USER2_USER = new TestUser(USER2, "password", List.of());

  @TenantDefinition
  private static final TestTenant A_TENANT =
      new TestTenant(TENANT_A).setName(TENANT_A).addUsers(ADMIN, USER1);

  @TenantDefinition
  private static final TestTenant B_TENANT =
      new TestTenant(TENANT_B).setName(TENANT_B).addUsers(ADMIN);

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResourceForTenant(adminClient, "process/bpm_variable_test.bpmn", TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);

    deployResourceForTenant(adminClient, "process/bpm_variable_test.bpmn", TENANT_B);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_B);
    waitForVariablesBeingExported(adminClient, 10);
  }

  @Test
  public void shouldReturnAllVariablesWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when / then
    assertVariablesForCommand(
        () -> camundaClient.newVariableSearchRequest().send().join(), 10, TENANT_A, TENANT_B);
  }

  @Test
  public void shouldReturnOnlyTenantAVariables(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when / then
    assertVariablesForCommand(
        () -> camundaClient.newVariableSearchRequest().send().join(), 5, TENANT_A);
  }

  @Test
  public void shouldNotReturnAnyVariables(@Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    assertVariablesForCommand(() -> camundaClient.newVariableSearchRequest().send().join(), 0);
  }

  @Test
  void getByKeyShouldReturnTenantOwnedVariable(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var variableKey = getVariableInstanceKey(adminClient, TENANT_A);
    // when
    final var result = camundaClient.newVariableGetRequest(variableKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getVariableKey()).isEqualTo(variableKey);
    assertThat(result.getTenantId()).isEqualTo(TENANT_A);
  }

  @Test
  void getByKeyShouldThrowNotFoundException(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var variableKey = getVariableInstanceKey(adminClient, TENANT_A);

    // when
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newVariableGetRequest(variableKey).send().join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Variable with key '%s' not found".formatted(variableKey));
  }

  private void assertVariablesForCommand(
      final Supplier<SearchResponse<Variable>> command,
      final int expectedSize,
      final String... expectedTenants) {
    final var result = command.get();
    assertThat(result.items()).hasSize(expectedSize);
    assertThat(result.items().stream().map(Variable::getTenantId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(expectedTenants);
  }

  private long getVariableInstanceKey(final CamundaClient camundaClient, final String tenantId) {
    return camundaClient
        .newVariableSearchRequest()
        .filter(f -> f.tenantId(tenantId))
        .send()
        .join()
        .items()
        .getFirst()
        .getVariableKey();
  }
}
