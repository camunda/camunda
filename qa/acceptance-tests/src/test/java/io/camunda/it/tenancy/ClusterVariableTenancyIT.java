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
import io.camunda.client.api.search.response.ClusterVariable;
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
public class ClusterVariableTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String VALUE_RESULT = "\"%s\"";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";

  private static final String TENANT_A_VAR_NAME = "clusterVarTenantA";
  private static final String TENANT_A_VAR_VALUE = "tenantAValue";
  private static final String TENANT_B_VAR_NAME = "clusterVarTenantB";
  private static final String TENANT_B_VAR_VALUE = "tenantBValue";

  private static final String GLOBAL_VAR_NAME = "globalClusterVar";
  private static final String GLOBAL_VAR_VALUE = "globalValue";

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

    // Create tenant-scoped cluster variables
    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_A)
        .create(TENANT_A_VAR_NAME, TENANT_A_VAR_VALUE)
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_B)
        .create(TENANT_B_VAR_NAME, TENANT_B_VAR_VALUE)
        .send()
        .join();

    // Create global cluster variable
    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(GLOBAL_VAR_NAME, GLOBAL_VAR_VALUE)
        .send()
        .join();

    waitForClusterVariablesBeingExported(adminClient);
  }

  @Test
  public void shouldReturnAllClusterVariablesWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // given - ensure baseline variables are present
    waitForClusterVariablesBeingExported(camundaClient);

    // when
    final var result = camundaClient.newClusterVariableSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(3);
    final var tenantIds =
        result.items().stream().map(ClusterVariable::getTenantId).collect(Collectors.toSet());
    assertThat(tenantIds).containsExactlyInAnyOrder(TENANT_A, TENANT_B, null);
    assertThat(
            result.items().stream()
                .filter(v -> v.getTenantId() == null)
                .map(ClusterVariable::getName)
                .collect(Collectors.toSet()))
        .containsExactly(GLOBAL_VAR_NAME);
  }

  @Test
  public void shouldReturnOnlyTenantAClusterVariables(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given - ensure baseline variables are present
    waitForBaselineClusterVariablesForUser1(camundaClient);

    // when
    final var result = camundaClient.newClusterVariableSearchRequest().send().join();
    // then - USER1 should see TENANT_A variable and global variable (2 total)
    assertThat(result.items()).hasSize(2);
    final var names =
        result.items().stream().map(ClusterVariable::getName).collect(Collectors.toSet());
    assertThat(names).containsExactlyInAnyOrder(TENANT_A_VAR_NAME, GLOBAL_VAR_NAME);
  }

  @Test
  public void shouldNotReturnAnyTenantClusterVariables(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newClusterVariableSearchRequest().send().join();
    // then - USER2 should not see any tenant-scoped variables, but should see global variables
    assertThat(
            result.items().stream()
                .filter(v -> v.getTenantId() != null)
                .collect(Collectors.toList()))
        .hasSize(0);
  }

  @Test
  void getByNameShouldReturnTenantOwnedClusterVariable(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newTenantScopedClusterVariableGetRequest(TENANT_A)
            .withName(TENANT_A_VAR_NAME)
            .send()
            .join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(TENANT_A_VAR_NAME);
    assertThat(result.getValue()).isEqualTo(VALUE_RESULT.formatted(TENANT_A_VAR_VALUE));
    assertThat(result.getTenantId()).isEqualTo(TENANT_A);
  }

  @Test
  void getByNameShouldThrowNotFoundExceptionWhenUserDoesNotHaveAccessToTenant(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableGetRequest(TENANT_A)
                        .withName(TENANT_A_VAR_NAME)
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
            "Tenant-scoped Cluster Variable with name '%s' not found".formatted(TENANT_A_VAR_NAME));
  }

  @Test
  void shouldNotAllowUserToAccessOtherTenantClusterVariable(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when - USER1 tries to access TENANT_B variable
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableGetRequest(TENANT_B)
                        .withName(TENANT_B_VAR_NAME)
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
  }

  @Test
  void shouldCreateAndRetrieveTenantScopedClusterVariable(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final String newVarName = "newTenantScopedVar";
    final String newVarValue = "newValue";

    try {
      // when
      camundaClient
          .newTenantScopedClusterVariableCreateRequest(TENANT_A)
          .create(newVarName, newVarValue)
          .send()
          .join();

      // then
      final var result =
          camundaClient
              .newTenantScopedClusterVariableGetRequest(TENANT_A)
              .withName(newVarName)
              .send()
              .join();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo(newVarName);
      assertThat(result.getValue()).isEqualTo(VALUE_RESULT.formatted(newVarValue));
      assertThat(result.getTenantId()).isEqualTo(TENANT_A);
    } finally {
      // cleanup
      camundaClient
          .newTenantScopedClusterVariableDeleteRequest(TENANT_A)
          .delete(newVarName)
          .send()
          .join();
      waitForClusterVariableToBeDeleted(camundaClient, newVarName, TENANT_A);
    }
  }

  @Test
  void shouldDeleteTenantScopedClusterVariable(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final String varToDeleteName = "varToDelete";
    final String varToDeleteValue = "deleteMe";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_A)
        .create(varToDeleteName, varToDeleteValue)
        .send()
        .join();

    // when
    camundaClient
        .newTenantScopedClusterVariableDeleteRequest(TENANT_A)
        .delete(varToDeleteName)
        .send()
        .join();

    // then
    waitForClusterVariableToBeDeleted(camundaClient, varToDeleteName, TENANT_A);
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableGetRequest(TENANT_A)
                        .withName(varToDeleteName)
                        .send()
                        .join())
            .actual();

    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
  }

  @Test
  void shouldNotAllowUserToCreateClusterVariableOnUnassignedTenant(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final String newVarName = "unauthorizedVar";
    final String newVarValue = "shouldFail";

    // when
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableCreateRequest(TENANT_A)
                        .create(newVarName, newVarValue)
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 403");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("FORBIDDEN");
    assertThat(exception.details().getStatus()).isEqualTo(403);
  }

  @Test
  void shouldNotAllowUserToDeleteClusterVariableOnUnassignedTenant(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when - USER2 tries to delete TENANT_A variable
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableDeleteRequest(TENANT_A)
                        .delete(TENANT_A_VAR_NAME)
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  @Test
  void shouldNotAllowUserToCreateClusterVariableOnOtherTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final String newVarName = "crossTenantVar";
    final String newVarValue = "shouldFail";

    // when - USER1 tries to create variable in TENANT_B
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableCreateRequest(TENANT_B)
                        .create(newVarName, newVarValue)
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 403");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("FORBIDDEN");
    assertThat(exception.details().getStatus()).isEqualTo(403);
  }

  @Test
  void shouldNotAllowUserToDeleteClusterVariableOnOtherTenant(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when - USER1 tries to delete TENANT_B variable
    final var exception =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newTenantScopedClusterVariableDeleteRequest(TENANT_B)
                        .delete(TENANT_B_VAR_NAME)
                        .send()
                        .join())
            .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  // Global Cluster Variable Tests

  @Test
  void shouldAllowUserWithoutTenantAccessToReadGlobalVariable(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newGloballyScopedClusterVariableGetRequest()
            .withName(GLOBAL_VAR_NAME)
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(GLOBAL_VAR_NAME);
    assertThat(result.getValue()).isEqualTo(VALUE_RESULT.formatted(GLOBAL_VAR_VALUE));
    assertThat(result.getTenantId()).isNull();
  }

  @Test
  void shouldReturnOnlyGlobalVariableForUserWithoutTenantAccess(
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newClusterVariableSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getName()).isEqualTo(GLOBAL_VAR_NAME);
    assertThat(result.items().getFirst().getTenantId()).isNull();
  }

  @Test
  void shouldReturnTenantAndGlobalVariablesForUserWithTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given - ensure baseline variables are present
    waitForBaselineClusterVariablesForUser1(camundaClient);

    // when
    final var result = camundaClient.newClusterVariableSearchRequest().send().join();

    // then - USER1 should see TENANT_A variable and global variable (2 total)
    assertThat(result.items()).hasSize(2);
    final var names =
        result.items().stream().map(ClusterVariable::getName).collect(Collectors.toSet());
    assertThat(names).containsExactlyInAnyOrder(TENANT_A_VAR_NAME, GLOBAL_VAR_NAME);
  }

  @Test
  void shouldAllowUserWithTenantAccessToReadGlobalVariable(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newGloballyScopedClusterVariableGetRequest()
            .withName(GLOBAL_VAR_NAME)
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(GLOBAL_VAR_NAME);
    assertThat(result.getValue()).isEqualTo(VALUE_RESULT.formatted(GLOBAL_VAR_VALUE));
    assertThat(result.getTenantId()).isNull();
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void waitForClusterVariablesBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive cluster variables from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newClusterVariableSearchRequest().send().join().items())
                    .hasSize(3));
  }

  private static void waitForBaselineClusterVariablesForUser1(final CamundaClient camundaClient) {
    Awaitility.await("should receive baseline cluster variables for USER1")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = camundaClient.newClusterVariableSearchRequest().send().join();
              assertThat(result.items()).hasSize(2);
              final var names =
                  result.items().stream().map(ClusterVariable::getName).collect(Collectors.toSet());
              assertThat(names).containsExactlyInAnyOrder(TENANT_A_VAR_NAME, GLOBAL_VAR_NAME);
            });
  }

  private static void waitForClusterVariableToBeDeleted(
      final CamundaClient camundaClient, final String variableName, final String tenantId) {
    Awaitility.await("should have cluster variable deleted")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newClusterVariableSearchRequest()
                      .filter(
                          f -> {
                            if (tenantId != null) {
                              f.tenantId(tenantId);
                            }
                            f.name(variableName);
                          })
                      .send()
                      .join();
              assertThat(result.items()).isEmpty();
            });
  }
}
