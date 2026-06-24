/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;

/**
 * Shared setup for the multi-physical-tenant authorization ITs.
 *
 * <p>Boots a single broker that serves three physical tenants ({@code default}, {@code tenanta},
 * {@code tenantb}), each on its own RDBMS-H2 schema, with authorizations and basic auth enabled and
 * a per-PT admin user seeded into each PT's own {@code security.initialization}. The control-plane
 * shape exercised by the ITs is: an admin (full permissions, scoped to its PT) creates a restricted
 * user at runtime and selectively grants it a permission; the restricted user then attempts a
 * permission-gated operation and we observe allowed (2xx) vs forbidden (403).
 *
 * <p>RDBMS-H2 only — Elasticsearch/OpenSearch variants are skipped because per-PT secondary-storage
 * schema init (#51996) and the per-PT writer (#51736) are not yet available, so non-default PTs
 * have no ES/OS indices to authorize against.
 */
abstract class MultiPhysicalTenantAuthorizationTestBase {

  protected static final String TENANT_A = "tenanta";
  protected static final String TENANT_B = "tenantb";
  protected static final String PT_ADMIN_PASSWORD = "ptadmin";
  protected static final String RESTRICTED_PASSWORD = "restricted";
  protected static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  protected static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant(TENANT_A, Storage.rdbmsH2(TENANT_A))
          .withTenant(TENANT_B, Storage.rdbmsH2(TENANT_B))
          .build();

  /**
   * Configures a broker for the multi-PT authorization scenario: storage + per-PT admin role (via
   * the helper's {@code configure}), basic auth, authorizations enabled, and a seeded per-PT admin
   * user for {@code tenanta} and {@code tenantb}.
   */
  protected static TestStandaloneBroker configureBroker() {
    final TestStandaloneBroker broker =
        TENANTS.configure(
            new TestStandaloneBroker()
                .withAuthorizationsEnabled()
                .withAuthenticationMethod(AuthenticationMethod.BASIC));
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_A, PT_ADMIN_PASSWORD);
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_B, PT_ADMIN_PASSWORD);
    return broker;
  }

  protected static CamundaClient adminClient(
      final TestStandaloneBroker broker, final String tenantId) {
    return TENANTS.newBasicAuthAdminClientBuilder(broker, tenantId, PT_ADMIN_PASSWORD).build();
  }

  /**
   * Admin client for the {@code default} PT, which authenticates with the broker's built-in default
   * user (not a {@code <tenant>-admin} user).
   */
  protected static CamundaClient defaultAdminClient(final TestStandaloneBroker broker) {
    return TENANTS
        .newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(InitializationConfiguration.DEFAULT_USER_USERNAME)
                .password(InitializationConfiguration.DEFAULT_USER_PASSWORD)
                .build())
        .build();
  }

  protected static CamundaClient restrictedClient(
      final TestStandaloneBroker broker, final String tenantId, final String username) {
    return TENANTS
        .newBasicAuthAdminClientBuilder(broker, tenantId, PT_ADMIN_PASSWORD)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password(RESTRICTED_PASSWORD)
                .build())
        .build();
  }

  /** Creates a restricted (no-permissions) user in the given PT via the PT's admin client. */
  protected static String createRestrictedUser(final CamundaClient admin, final String prefix) {
    return createRestrictedUserNamed(
        admin, prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
  }

  /**
   * Creates a restricted (no-permissions) user with an exact username in the given PT. Useful for
   * mirroring the same identity into multiple PTs (each PT has its own schema), e.g. to prove a
   * cross-PT denial is a true 403 (identity exists, no grant) rather than a 401 (unknown identity).
   */
  protected static String createRestrictedUserNamed(
      final CamundaClient admin, final String username) {
    // immediately after startup the per-PT admin user may not yet be initialized in its RDBMS
    // schema, so the admin's own request can transiently 401 — wait until it can authenticate
    Awaitility.await("per-PT admin can authenticate")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(admin.newUsersSearchRequest().send().join().items()).isNotNull());
    admin
        .newCreateUserCommand()
        .username(username)
        .password(RESTRICTED_PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
    // The user must be queryable via this PT before it can authenticate / be granted against.
    Awaitility.await("restricted user '" + username + "' exists in its PT")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        admin
                            .newUsersSearchRequest()
                            .filter(f -> f.username(username))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
    return username;
  }

  /** Grants {@code CREATE RESOURCE} (deploy) to a user via the given PT's admin client. */
  protected static void grantDeployPermission(final CamundaClient admin, final String username) {
    grant(admin, username, USER, CREATE, RESOURCE, "*");
  }

  /** Grants a permission to an arbitrary owner (user, group, role, mapping rule) in a PT. */
  protected static void grant(
      final CamundaClient admin,
      final String ownerId,
      final OwnerType ownerType,
      final PermissionType permission,
      final ResourceType resourceType,
      final String... resourceIds) {
    for (final String resourceId : resourceIds) {
      admin
          .newCreateAuthorizationCommand()
          .ownerId(ownerId)
          .ownerType(ownerType)
          .resourceId(resourceId)
          .resourceType(resourceType)
          .permissionTypes(permission)
          .send()
          .join();
    }
  }

  protected static BpmnModelInstance simpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }
}
