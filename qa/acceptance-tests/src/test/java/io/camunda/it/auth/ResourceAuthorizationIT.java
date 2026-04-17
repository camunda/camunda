/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ResourceAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withMultiTenancyEnabled();

  private static final String DEFAULT_PASSWORD = "password";
  private static final String RESOURCE_ID = "RPA_auditlog_test";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";
  private static final AtomicLong RESOURCE_KEY_TENANT_A = new AtomicLong();
  private static final AtomicLong RESOURCE_KEY_TENANT_B = new AtomicLong();
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(RESOURCE, READ, List.of("*")),
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          DEFAULT_PASSWORD,
          List.of(new Permissions(RESOURCE, READ, List.of(RESOURCE_ID))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED, DEFAULT_PASSWORD, List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    assignUserToTenant(adminClient, RESTRICTED, TENANT_A);
    assignUserToTenant(adminClient, UNAUTHORIZED, TENANT_A);

    final var deploymentTenantA =
        adminClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .tenantId(TENANT_A)
            .send()
            .join();
    RESOURCE_KEY_TENANT_A.set(deploymentTenantA.getResource().getFirst().getResourceKey());

    final var deploymentTenantB =
        adminClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .tenantId(TENANT_B)
            .send()
            .join();
    RESOURCE_KEY_TENANT_B.set(deploymentTenantB.getResource().getFirst().getResourceKey());

    Awaitility.await("resources should be available in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var resourceTenantA =
                  adminClient.newResourceGetRequest(RESOURCE_KEY_TENANT_A.get()).send().join();
              assertThat(resourceTenantA.getResourceId()).isEqualTo(RESOURCE_ID);
              final var resourceTenantB =
                  adminClient.newResourceGetRequest(RESOURCE_KEY_TENANT_B.get()).send().join();
              assertThat(resourceTenantB.getResourceId()).isEqualTo(RESOURCE_ID);
            });
  }

  @Test
  void shouldGetResourceWhenAuthorizedByResourceId(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var resource =
        userClient.newResourceGetRequest(RESOURCE_KEY_TENANT_A.get()).send().join();

    // then
    assertThat(resource).isNotNull();
    assertThat(resource.getResourceId()).isEqualTo(RESOURCE_ID);
  }

  @Test
  void shouldReturnForbiddenForGetResourceWhenUnauthorizedByResourceId(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () -> userClient.newResourceGetRequest(RESOURCE_KEY_TENANT_A.get()).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'RESOURCE'");
  }

  @Test
  void shouldGetResourceContentWhenAuthorizedByResourceId(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var content =
        userClient.newResourceContentGetRequest(RESOURCE_KEY_TENANT_A.get()).send().join();

    // then
    assertThat(content).isNotNull().isNotEmpty();
  }

  @Test
  void shouldReturnNotFoundForGetResourceWhenNotAssignedToTenant(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given - restricted user is only a member of TENANT_A, not TENANT_B

    // when
    final ThrowingCallable executeGet =
        () -> userClient.newResourceGetRequest(RESOURCE_KEY_TENANT_B.get()).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(404);
  }

  @Test
  void shouldReturnNotFoundForGetResourceContentWhenNotAssignedToTenant(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given - restricted user is only a member of TENANT_A, not TENANT_B

    // when
    final ThrowingCallable executeGet =
        () -> userClient.newResourceContentGetRequest(RESOURCE_KEY_TENANT_B.get()).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(404);
  }

  @Test
  void shouldReturnForbiddenForGetResourceContentWhenUnauthorizedByResourceId(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () -> userClient.newResourceContentGetRequest(RESOURCE_KEY_TENANT_A.get()).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'RESOURCE'");
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }
}
