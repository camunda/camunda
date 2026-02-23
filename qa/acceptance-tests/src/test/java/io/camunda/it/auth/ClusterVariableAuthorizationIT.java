/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.CLUSTER_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ClusterVariableAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String CREATE_USER = "createUser";
  private static final String READ_USER = "readUser";
  private static final String UPDATE_USER = "updateUser";
  private static final String DELETE_USER = "deleteUser";
  private static final String NO_PERMISSION_USER = "noPermissionUser";
  private static final String TEST_TENANT_ID = "test-tenant";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(CLUSTER_VARIABLE, CREATE, List.of("*")),
              new Permissions(CLUSTER_VARIABLE, READ, List.of("*")),
              new Permissions(CLUSTER_VARIABLE, UPDATE, List.of("*")),
              new Permissions(CLUSTER_VARIABLE, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser CREATE_USER_DEF =
      new TestUser(
          CREATE_USER,
          "password",
          List.of(new Permissions(CLUSTER_VARIABLE, CREATE, List.of("*"))));

  @UserDefinition
  private static final TestUser READ_USER_DEF =
      new TestUser(
          READ_USER, "password", List.of(new Permissions(CLUSTER_VARIABLE, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser DELETE_USER_DEF =
      new TestUser(
          DELETE_USER,
          "password",
          List.of(new Permissions(CLUSTER_VARIABLE, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser UPDATE_USER_DEF =
      new TestUser(
          UPDATE_USER,
          "password",
          List.of(new Permissions(CLUSTER_VARIABLE, UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser NO_PERMISSION_USER_DEF =
      new TestUser(NO_PERMISSION_USER, "password", List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Create globally scoped cluster variables
    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create("testVar1", "testValue1")
        .send()
        .join();

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create("testVar2", "testValue2")
        .send()
        .join();

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create("testVar3", "testValue3")
        .send()
        .join();

    // Create tenant-scoped cluster variables
    adminClient
        .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
        .create("tenantVar1", "tenantValue1")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
        .create("tenantVar2", "tenantValue2")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
        .create("tenantVar3", "tenantValue3")
        .send()
        .join();

    waitForClusterVariablesToBeAvailable(adminClient, 6);
  }

  // ============= READ PERMISSION TESTS =============

  @Test
  void searchShouldSucceedWithReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final var result = userClient.newClusterVariableSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(6);
  }

  @Test
  void searchShouldReturnEmptyWithoutReadPermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final var result = userClient.newClusterVariableSearchRequest().send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void getGloballyScopedVariableShouldSucceedWithReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final var result =
        userClient.newGloballyScopedClusterVariableGetRequest().withName("testVar1").send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("testVar1");
  }

  @Test
  void getGloballyScopedVariableShouldFailWithoutReadPermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () ->
            userClient
                .newGloballyScopedClusterVariableGetRequest()
                .withName("testVar1")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void getGloballyScopedVariableShouldFailWithoutReadPermissionWhenOnlyHaveCreatePermission(
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () ->
            userClient
                .newGloballyScopedClusterVariableGetRequest()
                .withName("testVar1")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void getTenantScopedVariableShouldSucceedWithReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final var result =
        userClient
            .newTenantScopedClusterVariableGetRequest(TEST_TENANT_ID)
            .withName("tenantVar1")
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("tenantVar1");
  }

  @Test
  void getTenantScopedVariableShouldFailWithoutReadPermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () ->
            userClient
                .newTenantScopedClusterVariableGetRequest(TEST_TENANT_ID)
                .withName("tenantVar1")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  // ============= CREATE PERMISSION TESTS =============

  @Test
  void createGloballyScopedVariableShouldSucceedWithCreatePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // given
    final var variableName = "newGlobalVar" + UUID.randomUUID();

    try {
      // when
      final var result =
          userClient
              .newGloballyScopedClusterVariableCreateRequest()
              .create(variableName, "newValue")
              .send()
              .join();

      // then
      assertThat(result).isNotNull();
      assertThat(result.getName()).isNotEmpty();
    } finally {
      // cleanup
      adminClient
          .newGloballyScopedClusterVariableDeleteRequest()
          .delete(variableName)
          .send()
          .join();
      waitForClusterVariableToBeDeleted(adminClient, variableName, null);
    }
  }

  @Test
  void createGloballyScopedVariableShouldFailWithoutCreatePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newGloballyScopedClusterVariableCreateRequest()
                .create("forbiddenVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void createGloballyScopedVariableShouldFailWithoutCreatePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newGloballyScopedClusterVariableCreateRequest()
                .create("forbiddenVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void createGloballyScopedVariableShouldFailWithoutCreatePermissionWhenOnlyHaveDeletePermission(
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newGloballyScopedClusterVariableCreateRequest()
                .create("forbiddenVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void createTenantScopedVariableShouldSucceedWithCreatePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // given
    final var variableName = "newTenantVar" + UUID.randomUUID();

    try {
      // when
      final var result =
          userClient
              .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
              .create(variableName, "newValue")
              .send()
              .join();

      // then
      assertThat(result).isNotNull();
      assertThat(result.getName()).isNotEmpty();
    } finally {
      // cleanup
      adminClient
          .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
          .delete(variableName)
          .send()
          .join();
      waitForClusterVariableToBeDeleted(adminClient, variableName, TEST_TENANT_ID);
    }
  }

  @Test
  void createTenantScopedVariableShouldFailWithoutCreatePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
                .create("forbiddenTenantVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void createTenantScopedVariableShouldFailWithoutCreatePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
                .create("forbiddenTenantVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void createTenantScopedVariableShouldFailWithoutCreatePermissionWhenOnlyHaveDeletePermission(
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeCreate =
        () ->
            userClient
                .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
                .create("forbiddenTenantVar", "forbiddenValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeCreate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  // ============= DELETE PERMISSION TESTS =============

  @Test
  void deleteGloballyScopedVariableShouldSucceedWithDeletePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // given
    final var variableName = UUID.randomUUID().toString();
    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "valueToDelete")
        .send()
        .join();

    // when
    userClient.newGloballyScopedClusterVariableDeleteRequest().delete(variableName).send().join();

    // then - verify the variable was deleted
    waitForClusterVariableToBeDeleted(adminClient, variableName, null);
    final var result =
        adminClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(variableName))
            .send()
            .join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void deleteGloballyScopedVariableShouldFailWithoutDeletePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newGloballyScopedClusterVariableDeleteRequest()
                .delete("testVar1")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void deleteGloballyScopedVariableShouldFailWithoutDeletePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newGloballyScopedClusterVariableDeleteRequest()
                .delete("testVar2")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void deleteGloballyScopedVariableShouldFailWithoutDeletePermissionWhenOnlyHaveCreatePermission(
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newGloballyScopedClusterVariableDeleteRequest()
                .delete("testVar3")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void deleteTenantScopedVariableShouldSucceedWithDeletePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // given
    final var variableName = UUID.randomUUID().toString();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
        .create(variableName, "valueToDelete")
        .send()
        .join();

    // when
    userClient
        .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
        .delete(variableName)
        .send()
        .join();

    waitForClusterVariableToBeDeleted(adminClient, variableName, TEST_TENANT_ID);
    // then - verify the variable was deleted
    final var result =
        adminClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(variableName))
            .send()
            .join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void deleteTenantScopedVariableShouldFailWithoutDeletePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {

    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
                .delete("tenantVar1")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void deleteTenantScopedVariableShouldFailWithoutDeletePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {

    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
                .delete("tenantVar2")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void deleteTenantScopedVariableShouldFailWithoutDeletePermissionWhenOnlyHaveCreatePermission(
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeDelete =
        () ->
            userClient
                .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
                .delete("tenantVar3")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  // ============= UPDATE PERMISSION TESTS =============

  @Test
  void updateGloballyScopedVariableShouldSucceedWithUpdatePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UPDATE_USER) final CamundaClient userClient) {
    // given
    final var variableName = "updateGlobalVar" + UUID.randomUUID();
    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "initialValue")
        .send()
        .join();

    try {
      // when
      final var result =
          userClient
              .newGloballyScopedClusterVariableUpdateRequest()
              .update(variableName, "updatedValue")
              .send()
              .join();

      // then
      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo(variableName);
    } finally {
      // cleanup
      adminClient
          .newGloballyScopedClusterVariableDeleteRequest()
          .delete(variableName)
          .send()
          .join();
      waitForClusterVariableToBeDeleted(adminClient, variableName, null);
    }
  }

  @Test
  void updateGloballyScopedVariableShouldFailWithoutUpdatePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newGloballyScopedClusterVariableUpdateRequest()
                .update("testVar1", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateGloballyScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newGloballyScopedClusterVariableUpdateRequest()
                .update("testVar1", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateGloballyScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveCreatePermission(
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newGloballyScopedClusterVariableUpdateRequest()
                .update("testVar2", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateGloballyScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveDeletePermission(
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newGloballyScopedClusterVariableUpdateRequest()
                .update("testVar3", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateTenantScopedVariableShouldSucceedWithUpdatePermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UPDATE_USER) final CamundaClient userClient) {
    // given
    final var variableName = "updateTenantVar" + UUID.randomUUID();
    adminClient
        .newTenantScopedClusterVariableCreateRequest(TEST_TENANT_ID)
        .create(variableName, "initialValue")
        .send()
        .join();

    try {
      // when
      final var result =
          userClient
              .newTenantScopedClusterVariableUpdateRequest(TEST_TENANT_ID)
              .update(variableName, "updatedValue")
              .send()
              .join();

      // then
      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo(variableName);
    } finally {
      // cleanup
      adminClient
          .newTenantScopedClusterVariableDeleteRequest(TEST_TENANT_ID)
          .delete(variableName)
          .send()
          .join();
      waitForClusterVariableToBeDeleted(adminClient, variableName, TEST_TENANT_ID);
    }
  }

  @Test
  void updateTenantScopedVariableShouldFailWithoutUpdatePermission(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newTenantScopedClusterVariableUpdateRequest(TEST_TENANT_ID)
                .update("tenantVar1", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateTenantScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveReadPermission(
      @Authenticated(READ_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newTenantScopedClusterVariableUpdateRequest(TEST_TENANT_ID)
                .update("tenantVar1", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateTenantScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveCreatePermission(
      @Authenticated(CREATE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newTenantScopedClusterVariableUpdateRequest(TEST_TENANT_ID)
                .update("tenantVar2", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  @Test
  void updateTenantScopedVariableShouldFailWithoutUpdatePermissionWhenOnlyHaveDeletePermission(
      @Authenticated(DELETE_USER) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeUpdate =
        () ->
            userClient
                .newTenantScopedClusterVariableUpdateRequest(TEST_TENANT_ID)
                .update("tenantVar3", "newValue")
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeUpdate).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  private static void waitForClusterVariablesToBeAvailable(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should have cluster variables available")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = camundaClient.newClusterVariableSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
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
