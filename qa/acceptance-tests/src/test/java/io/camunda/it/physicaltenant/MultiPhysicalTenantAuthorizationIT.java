/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static io.camunda.client.api.search.enums.OwnerType.GROUP;
import static io.camunda.client.api.search.enums.OwnerType.MAPPING_RULE;
import static io.camunda.client.api.search.enums.OwnerType.ROLE;
import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Multi-physical-tenant authorization ITs converted to the {@link MultiDbTest} + {@link
 * MultiDbPhysicalTenants} framework. Validates cross-tenant isolation on RDBMS backends with a
 * single broker hosting {@code default}, {@code tenanta}, and {@code tenantb}, each isolated via a
 * per-PT table prefix (shared DB) or a dedicated in-memory database (H2).
 *
 * <p>RDBMS only — test is disabled for ES/OS because per-PT secondary-storage schema init and
 * per-PT writer are not yet available for those backends.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "RDBMS_.*",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class MultiPhysicalTenantAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  // Injected by the extension: admin clients for tenanta and tenantb
  static MultiPhysicalTenantClients ptClients;

  // Injected by the extension: default-PT admin (broker's built-in default user)
  static CamundaClient client;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String RESTRICTED_PASSWORD = "restricted";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  /**
   * IT-1 — control-plane authorization isolation. A permission granted only in tenantb authorizes a
   * control-plane operation (deploy) via the tenantb path, but the same operation is denied (403)
   * via the tenanta path.
   */
  @Test
  void shouldAuthorizeControlPlaneInGrantedTenantAndDenyInUngrantedTenant() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);

    final String username = "ctrl-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(tenantBAdmin, username);
    createRestrictedUserNamed(tenantAAdmin, username);
    grantDeployPermission(tenantBAdmin, username);

    try (final CamundaClient restrictedInB = restrictedClient(TENANT_B, username)) {
      awaitDeployAllowed(restrictedInB, "granted user can deploy via tenantb");
    }

    try (final CamundaClient restrictedInA = restrictedClient(TENANT_A, username)) {
      assertThatThrownBy(() -> deploy(restrictedInA))
          .as(
              "same identity ungranted in tenanta is denied — a grant in tenantb must not leak"
                  + " (same ownerId, so a cross-PT leak would flip this to allowed)")
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("status: 403")
          .hasMessageContaining("FORBIDDEN");

      grantDeployPermission(tenantAAdmin, username);
      awaitDeployAllowed(restrictedInA, "granting in tenanta flips the denial to allowed");
    }
  }

  /**
   * IT-5 — no cross-PT or default-tenant leakage. Grants applied in default or tenanta do NOT
   * authorize in tenantb.
   */
  @Test
  void shouldNotAuthorizeInTenantBFromGrantsInDefaultOrTenantA() {
    final CamundaClient defaultAdmin = client;
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);

    final String username = "leak-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(defaultAdmin, username);
    createRestrictedUserNamed(tenantAAdmin, username);
    createRestrictedUserNamed(tenantBAdmin, username);
    grantDeployPermission(defaultAdmin, username);
    grantDeployPermission(tenantAAdmin, username);

    try (final CamundaClient restrictedInB = restrictedClient(TENANT_B, username)) {
      Awaitility.await(
              "same identity stays denied in tenantb despite grants in default and tenanta")
          .during(PROPAGATION_TIMEOUT.dividedBy(6))
          .atMost(PROPAGATION_TIMEOUT)
          .untilAsserted(
              () ->
                  assertThatThrownBy(() -> deploy(restrictedInB))
                      .as(
                          "grants in default/tenanta must not authorize the same identity in tenantb"
                              + " — a cross-PT leak by ownerId would flip this to allowed")
                      .isInstanceOf(ProblemException.class)
                      .hasMessageContaining("status: 403")
                      .hasMessageContaining("FORBIDDEN"));

      grantDeployPermission(tenantBAdmin, username);
      awaitDeployAllowed(restrictedInB, "granting in tenantb itself authorizes the operation");
    }
  }

  /**
   * IT-2 — data-plane request filtering and cross-PT grant non-leakage. A user granted READ on
   * specific process definitions in tenantb sees only those via the tenantb path.
   */
  @Test
  void shouldFilterDataPlaneSearchToInContextTenantGrants() {
    final CamundaClient defaultAdmin = client;
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String granted1 = "granted1-" + suffix;
    final String granted2 = "granted2-" + suffix;
    final String ungranted = "ungranted-" + suffix;
    deployNamed(tenantBAdmin, granted1);
    deployNamed(tenantBAdmin, granted2);
    deployNamed(tenantBAdmin, ungranted);

    final String reader = createRestrictedUser(tenantBAdmin, "reader-b");
    grant(
        tenantBAdmin,
        reader,
        USER,
        READ_PROCESS_DEFINITION,
        PROCESS_DEFINITION,
        granted1,
        granted2);

    // leak trap — mirror the same reader into default and tenanta with READ on the 'ungranted' id
    createRestrictedUserNamed(defaultAdmin, reader);
    createRestrictedUserNamed(tenantAAdmin, reader);
    grant(defaultAdmin, reader, USER, READ_PROCESS_DEFINITION, PROCESS_DEFINITION, ungranted);
    grant(tenantAAdmin, reader, USER, READ_PROCESS_DEFINITION, PROCESS_DEFINITION, ungranted);

    try (final CamundaClient readerClient = restrictedClient(TENANT_B, reader)) {
      Awaitility.await("reader sees only the granted process definitions via tenantb")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var ids =
                    readerClient.newProcessDefinitionSearchRequest().send().join().items().stream()
                        .map(p -> p.getProcessDefinitionId())
                        .toList();
                assertThat(ids)
                    .as("data-plane search is filtered to the reader's tenantb grants")
                    .contains(granted1, granted2)
                    .doesNotContain(ungranted);
              });
    }
  }

  /**
   * IT-3 — membership-derived authorization across PTs. A permission granted to a GROUP, a ROLE and
   * a MAPPING_RULE in tenantb authorizes a member user via tenantb but is denied (403) via tenanta.
   */
  @Test
  void shouldDeriveAuthorizationFromGroupRoleAndMappingRuleMembership() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);

    final String suffix = UUID.randomUUID().toString().substring(0, 8);

    // GROUP membership
    final String groupId = "grp-" + suffix;
    tenantBAdmin.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
    final String groupMember = createRestrictedUser(tenantBAdmin, "grp-member");
    tenantBAdmin.newAssignUserToGroupCommand().username(groupMember).groupId(groupId).send().join();
    grant(tenantBAdmin, groupId, GROUP, CREATE, RESOURCE, "*");
    createRestrictedUserNamed(tenantAAdmin, groupMember);

    try (final CamundaClient memberClient = restrictedClient(TENANT_B, groupMember)) {
      awaitDeployAllowed(memberClient, "group member inherits deploy grant via tenantb");
    }
    try (final CamundaClient memberInA = restrictedClient(TENANT_A, groupMember)) {
      assertDeployForbidden(memberInA, "group grant in tenantb must not authorize via tenanta");
    }

    // ROLE membership
    final String roleId = "role-" + suffix;
    tenantBAdmin.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
    final String roleMember = createRestrictedUser(tenantBAdmin, "role-member");
    tenantBAdmin.newAssignRoleToUserCommand().roleId(roleId).username(roleMember).send().join();
    grant(tenantBAdmin, roleId, ROLE, CREATE, RESOURCE, "*");
    createRestrictedUserNamed(tenantAAdmin, roleMember);

    try (final CamundaClient memberClient = restrictedClient(TENANT_B, roleMember)) {
      awaitDeployAllowed(memberClient, "role member inherits deploy grant via tenantb");
    }
    try (final CamundaClient memberInA = restrictedClient(TENANT_A, roleMember)) {
      assertDeployForbidden(memberInA, "role grant in tenantb must not authorize via tenanta");
    }

    // MAPPING_RULE — assert the auth record is PT-isolated (visible via tenantb, absent via
    // tenanta)
    final String mappingRuleId = "mr-" + suffix;
    tenantBAdmin
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name(mappingRuleId)
        .claimName("groups")
        .claimValue("tenantb-deployers")
        .send()
        .join();
    grant(tenantBAdmin, mappingRuleId, MAPPING_RULE, CREATE, RESOURCE, "*");

    Awaitility.await("mapping-rule authorization is visible via tenantb")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(authorizationsForOwner(tenantBAdmin, mappingRuleId))
                    .as("mapping-rule grant created in tenantb is visible via tenantb")
                    .isNotEmpty());
    Awaitility.await("mapping-rule grant stays absent via tenanta")
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(authorizationsForOwner(tenantAAdmin, mappingRuleId))
                    .as("mapping-rule grant in tenantb must NOT be visible via tenanta")
                    .isEmpty());
  }

  /**
   * IT-4 — engine batch operation (off the request thread). A batch created via the tenantb path
   * operates only on tenantb's authorized process instances.
   */
  @Test
  void shouldScopeBatchOperationToCreatingTenantInstances() {
    final CamundaClient defaultAdmin = client;
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String processId = "batch-" + suffix;

    deployWaitingProcess(defaultAdmin, processId);
    deployWaitingProcess(tenantAAdmin, processId);
    deployWaitingProcess(tenantBAdmin, processId);
    final int instancesPerTenant = 2;
    createInstances(defaultAdmin, processId, instancesPerTenant);
    createInstances(tenantAAdmin, processId, instancesPerTenant);
    createInstances(tenantBAdmin, processId, instancesPerTenant);
    awaitActiveInstances(defaultAdmin, processId, instancesPerTenant);
    awaitActiveInstances(tenantAAdmin, processId, instancesPerTenant);
    awaitActiveInstances(tenantBAdmin, processId, instancesPerTenant);

    final var batch =
        tenantBAdmin
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(f -> f.processDefinitionId(processId))
            .send()
            .join();

    Awaitility.await("batch operation materializes only tenantb instances")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final List<BatchOperationItem> items =
                  tenantBAdmin
                      .newBatchOperationItemsSearchRequest()
                      .filter(f -> f.batchOperationKey(batch.getBatchOperationKey()))
                      .send()
                      .join()
                      .items();
              assertThat(items)
                  .as("batch created via tenantb operates only on tenantb's instances")
                  .hasSize(instancesPerTenant);
            });

    Awaitility.await("tenantb instances are cancelled by the batch")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(activeInstanceCount(tenantBAdmin, processId)).isZero());
    Awaitility.await("default and tenanta instances stay untouched by tenantb's batch")
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(activeInstanceCount(defaultAdmin, processId))
                  .as("default instances must be untouched by tenantb's batch")
                  .isEqualTo(instancesPerTenant);
              assertThat(activeInstanceCount(tenantAAdmin, processId))
                  .as("tenanta instances must be untouched by tenantb's batch")
                  .isEqualTo(instancesPerTenant);
            });
  }

  // --- helpers -------------------------------------------------------------------------

  private CamundaClient restrictedClient(final String tenantId, final String username) {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final java.net.URI restAddress = java.net.URI.create(base + "/physical-tenants/" + tenantId);
    return BROKER
        .newClientBuilder()
        .physicalTenantId(tenantId)
        .preferRestOverGrpc(true)
        .restAddress(restAddress)
        .grpcAddress(BROKER.grpcAddress())
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password(RESTRICTED_PASSWORD)
                .build())
        .build();
  }

  private void awaitDeployAllowed(final CamundaClient client, final String reason) {
    Awaitility.await(reason)
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(deploy(client).getProcesses()).as(reason).isNotEmpty());
  }

  private static void assertDeployForbidden(final CamundaClient client, final String reason) {
    assertThatThrownBy(() -> deploy(client))
        .as(reason)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("status: 403")
        .hasMessageContaining("FORBIDDEN");
  }

  private static io.camunda.client.api.response.DeploymentEvent deploy(final CamundaClient client) {
    final String processId = Strings.newRandomValidBpmnId();
    return client
        .newDeployResourceCommand()
        .addProcessModel(simpleProcess(processId), processId + ".bpmn")
        .send()
        .join();
  }

  private static void deployNamed(final CamundaClient client, final String processId) {
    client
        .newDeployResourceCommand()
        .addProcessModel(simpleProcess(processId), processId + ".bpmn")
        .send()
        .join();
  }

  private static void deployWaitingProcess(final CamundaClient client, final String processId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("wait", t -> t.zeebeJobType("never-completed-" + processId))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();
  }

  private static void createInstances(
      final CamundaClient client, final String processId, final int count) {
    for (int i = 0; i < count; i++) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }
  }

  private static void awaitActiveInstances(
      final CamundaClient client, final String processId, final int expected) {
    Awaitility.await("instances for '" + processId + "' are active and searchable")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(activeInstanceCount(client, processId)).isEqualTo(expected));
  }

  private static long activeInstanceCount(final CamundaClient client, final String processId) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId).state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .items()
        .size();
  }

  private static List<?> authorizationsForOwner(final CamundaClient client, final String ownerId) {
    return client
        .newAuthorizationSearchRequest()
        .filter(f -> f.ownerId(ownerId))
        .send()
        .join()
        .items();
  }

  private static String createRestrictedUser(final CamundaClient admin, final String prefix) {
    return createRestrictedUserNamed(
        admin, prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
  }

  private static String createRestrictedUserNamed(
      final CamundaClient admin, final String username) {
    admin
        .newCreateUserCommand()
        .username(username)
        .password(RESTRICTED_PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
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

  private static void grantDeployPermission(final CamundaClient admin, final String username) {
    grant(admin, username, USER, CREATE, RESOURCE, "*");
  }

  private static void grant(
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

  private static BpmnModelInstance simpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }
}
