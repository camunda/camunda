/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

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
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Multi-physical-tenant authorization ITs that share a SINGLE 3-PT broker ({@code default}, {@code
 * tenanta}, {@code tenantb}), with authorizations and basic auth enabled, multi-tenancy disabled,
 * and a per-PT admin user seeded into each PT. Sharing one broker avoids the topology-await
 * contention seen when each IT class booted its own heavy 3-PT RDBMS broker serially.
 *
 * <p>Each test isolates itself via unique owner/resource ids (UUID-suffixed), so the methods do not
 * interfere with each other on the shared broker.
 *
 * <p>RDBMS-H2 only — Elasticsearch/OpenSearch variants are skipped because per-PT secondary-storage
 * schema init (#51996) and the per-PT writer (#51736) are not yet available, so non-default PTs
 * have no ES/OS indices to authorize against.
 */
@ZeebeIntegration
@Disabled
final class MultiPhysicalTenantAuthorizationIT extends MultiPhysicalTenantAuthorizationTestBase {

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER = configureBroker();

  private static CamundaClient defaultAdmin;
  private static CamundaClient tenantAAdmin;
  private static CamundaClient tenantBAdmin;

  @BeforeAll
  static void startBroker() {
    BROKER.start();
    defaultAdmin = defaultAdminClient(BROKER);
    tenantAAdmin = adminClient(BROKER, TENANT_A);
    tenantBAdmin = adminClient(BROKER, TENANT_B);
    TENANTS.awaitAdminReady(defaultAdmin);
    TENANTS.awaitAdminReady(tenantAAdmin);
    TENANTS.awaitAdminReady(tenantBAdmin);
  }

  @AfterAll
  static void closeClients() {
    closeQuietly(defaultAdmin);
    closeQuietly(tenantAAdmin);
    closeQuietly(tenantBAdmin);
  }

  /**
   * IT-1 — control-plane authorization isolation. AC: a permission granted only in tenantb
   * authorizes a control-plane operation (deploy) via the tenantb path, but the same operation is
   * denied (403) via the tenanta path; granting the same in tenanta flips it to allowed.
   */
  @Test
  void shouldAuthorizeControlPlaneInGrantedTenantAndDenyInUngrantedTenant() {
    // given — the same identity mirrored into both PTs, with deploy granted ONLY in tenantb
    final String username = "ctrl-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(tenantBAdmin, username);
    createRestrictedUserNamed(tenantAAdmin, username);
    grantDeployPermission(tenantBAdmin, username);

    // when / then — the grant authorizes deploy via tenantb (positive)
    try (final CamundaClient restrictedInB = restrictedClient(BROKER, TENANT_B, username)) {
      awaitDeployAllowed(restrictedInB, "granted user can deploy via tenantb");
    }

    // and — the same identity (same ownerId), ungranted in tenanta, is forbidden (403) via tenanta;
    // a cross-PT leak by ownerId would flip this denial to allowed and fail the test
    try (final CamundaClient restrictedInA = restrictedClient(BROKER, TENANT_A, username)) {
      assertThatThrownBy(() -> deploy(restrictedInA))
          .as(
              "same identity ungranted in tenanta is denied — a grant in tenantb must not leak"
                  + " (same ownerId, so a cross-PT leak would flip this to allowed)")
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("status: 403")
          .hasMessageContaining("FORBIDDEN");

      // positive control — granting the same permission in tenanta flips it to allowed
      grantDeployPermission(tenantAAdmin, username);
      awaitDeployAllowed(restrictedInA, "granting in tenanta flips the denial to allowed");
    }
  }

  /**
   * IT-5 — no cross-PT or default-tenant leakage. AC: grants applied in default or tenanta do NOT
   * authorize in tenantb; an ungranted tenantb user stays denied (403) despite those grants, and
   * granting in tenantb itself flips it to allowed (positive control).
   */
  @Test
  void shouldNotAuthorizeInTenantBFromGrantsInDefaultOrTenantA() {
    // given — the same identity mirrored into default, tenanta, and tenantb; deploy granted only in
    // default and tenanta (NOT tenantb), so a cross-PT leak of that grant would flip the tenantb
    // denial to allowed (same ownerId) and fail the test
    final String username = "leak-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(defaultAdmin, username);
    createRestrictedUserNamed(tenantAAdmin, username);
    createRestrictedUserNamed(tenantBAdmin, username);
    grantDeployPermission(defaultAdmin, username);
    grantDeployPermission(tenantAAdmin, username);

    try (final CamundaClient restrictedInB = restrictedClient(BROKER, TENANT_B, username)) {
      // then — the same identity stays denied via tenantb across the propagation window;
      // a cross-PT leak from default/tenanta would flip this to allowed (same ownerId)
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

      // positive control — granting in tenantb itself flips the tenantb user to allowed
      grantDeployPermission(tenantBAdmin, username);
      awaitDeployAllowed(restrictedInB, "granting in tenantb itself authorizes the operation");
    }
  }

  /**
   * IT-2 — data-plane request filtering and cross-PT grant non-leakage. AC: a user granted READ on
   * specific process definitions in tenantb sees only those via the tenantb path; the search result
   * reflects the in-context PT's grants, not another PT's.
   *
   * <p>To make the negative assertion a genuine control (not vacuously true), the SAME reader
   * username is mirrored into default and tenanta and granted READ on the {@code ungranted}
   * definition's id there. If a cross-PT grant leaked into tenantb's authorization view, the reader
   * would then see {@code ungranted} via tenantb and the test would fail — so the {@code
   * doesNotContain(ungranted)} assertion specifically proves cross-PT grant non-leakage.
   */
  @Test
  void shouldFilterDataPlaneSearchToInContextTenantGrants() {
    // given — three process definitions deployed in tenantb, READ granted on only two of them
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

    // leak trap — the same reader, mirrored into default and tenanta, IS granted READ on the
    // 'ungranted' id there; a cross-PT leak would surface that id in the tenantb view below
    createRestrictedUserNamed(defaultAdmin, reader);
    createRestrictedUserNamed(tenantAAdmin, reader);
    grant(defaultAdmin, reader, USER, READ_PROCESS_DEFINITION, PROCESS_DEFINITION, ungranted);
    grant(tenantAAdmin, reader, USER, READ_PROCESS_DEFINITION, PROCESS_DEFINITION, ungranted);

    // when / then — the reader sees exactly the two granted definitions via tenantb (filtered)
    try (final CamundaClient readerClient = restrictedClient(BROKER, TENANT_B, reader)) {
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
   * IT-3 — membership-derived authorization across PTs. AC: a permission granted to a GROUP, a ROLE
   * and a MAPPING_RULE (not the user directly) in tenantb authorizes a member user via tenantb but
   * is denied (403) via tenanta.
   *
   * <p>GROUP and ROLE are fully exercised end-to-end: a member user inherits the grant and deploy
   * succeeds in tenantb. To prove the grant specifically does not leak (rather than merely that the
   * identity is PT-scoped), the same username is created independently in tenanta with NO
   * grant/membership; via tenanta the member authenticates but is unauthorized, so the denial is a
   * true 403, not a 401. MAPPING_RULE binds OIDC token claims to an identity, so a BASIC-auth user
   * cannot inherit a mapping-rule grant on this harness; for that kind we instead assert the
   * mapping-rule authorization record is itself PT-isolated (visible via tenantb, absent via
   * tenanta), which is the membership-authorization signal observable under basic auth.
   */
  @Test
  void shouldDeriveAuthorizationFromGroupRoleAndMappingRuleMembership() {
    // GROUP membership — grant deploy to a group in tenantb, add a member user
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String groupId = "grp-" + suffix;
    tenantBAdmin.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
    final String groupMember = createRestrictedUser(tenantBAdmin, "grp-member");
    tenantBAdmin.newAssignUserToGroupCommand().username(groupMember).groupId(groupId).send().join();
    grant(tenantBAdmin, groupId, GROUP, CREATE, RESOURCE, "*");

    // mirror the SAME username into tenanta independently, with NO grant/membership there, so the
    // cross-tenant denial is a true 403 (identity exists, unauthorized) not a 401 (unknown
    // identity)
    createRestrictedUserNamed(tenantAAdmin, groupMember);

    try (final CamundaClient memberClient = restrictedClient(BROKER, TENANT_B, groupMember)) {
      awaitDeployAllowed(memberClient, "group member inherits deploy grant via tenantb");
    }
    try (final CamundaClient memberInA = restrictedClient(BROKER, TENANT_A, groupMember)) {
      assertDeployForbidden(memberInA, "group grant in tenantb must not authorize via tenanta");
    }

    // ROLE membership — grant deploy to a role in tenantb, assign a member user
    final String roleId = "role-" + suffix;
    tenantBAdmin.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
    final String roleMember = createRestrictedUser(tenantBAdmin, "role-member");
    tenantBAdmin.newAssignRoleToUserCommand().roleId(roleId).username(roleMember).send().join();
    grant(tenantBAdmin, roleId, ROLE, CREATE, RESOURCE, "*");
    // mirror the same username into tenanta (ungranted) so the cross-tenant denial is a true 403
    createRestrictedUserNamed(tenantAAdmin, roleMember);

    try (final CamundaClient memberClient = restrictedClient(BROKER, TENANT_B, roleMember)) {
      awaitDeployAllowed(memberClient, "role member inherits deploy grant via tenantb");
    }
    try (final CamundaClient memberInA = restrictedClient(BROKER, TENANT_A, roleMember)) {
      assertDeployForbidden(memberInA, "role grant in tenantb must not authorize via tenanta");
    }

    // MAPPING_RULE membership — assert the mapping-rule authorization record is PT-isolated
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
    // assert tenanta stays empty over a short window, so a late-arriving leak fails the test
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
   * IT-4 — engine batch operation (off the request thread). AC: a batch operation created via the
   * tenantb path operates only on tenantb's authorized process instances; instances in
   * default/tenanta are untouched. This exercises the async/off-request authorization path: item
   * materialization runs on the engine using the creator's PT-scoped authorization, so the batch
   * must resolve only tenantb instances.
   */
  @Test
  void shouldScopeBatchOperationToCreatingTenantInstances() {
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String processId = "batch-" + suffix;

    // given — the same waiting process deployed and instantiated in default, tenanta and tenantb
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

    // when — a cancel batch operation is created via the tenantb path over all active instances
    final var batch =
        tenantBAdmin
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(f -> f.processDefinitionId(processId))
            .send()
            .join();

    // then — the batch resolves only tenantb's instances (off-request authorization is PT-scoped)
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

    // and — default and tenanta instances remain active (untouched by tenantb's batch)
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

  // --- helpers -------------------------------------------------------------

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

  private static void closeQuietly(final CamundaClient client) {
    if (client != null) {
      client.close();
    }
  }
}
