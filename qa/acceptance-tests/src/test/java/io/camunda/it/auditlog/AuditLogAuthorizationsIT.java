/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.AUDIT_LOG;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_ID_A;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_ID_B;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_ID_DEPLOYED_RESOURCES;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_B;
import static io.camunda.it.auditlog.AuditLogUtils.USER_TASKS_PROCESS_ID;
import static io.camunda.it.auditlog.AuditLogUtils.assignUserToTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/*
 * Test is disabled on RDBMS until https://github.com/camunda/camunda/issues/43323 is implemented.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogAuthorizationsIT {
  protected static final boolean USE_REST_API = true;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String USER_A_USERNAME = "userA";
  private static final String USER_B_USERNAME = "userB";
  private static final String USER_C_USERNAME = "userC";
  private static final String USER_D_USERNAME = "userD";
  private static final String USER_E_USERNAME = "userE";
  private static final String USER_F_USERNAME = "userF";
  private static final String PASSWORD = "password";

  /*
   * User A has AUDIT_LOG READ permission for PROCESS_ID_A only. User A is only assigned to
   * TENANT_A. They should only see audit logs related to that process definition and belonging to
   * TENANT_A.
   */
  @UserDefinition
  private static final TestUser USER_A =
      new TestUser(
          USER_A_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of(PROCESS_ID_A)),
              new Permissions(
                  AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.DEPLOYED_RESOURCES.name()))));

  /*
   * User B has AUDIT_LOG READ permission for PROCESS_ID_B only. User B is only assigned to
   * TENANT_B. They should only see audit logs related to that process definition and belonging to
   * TENANT_B.
   */
  @UserDefinition
  private static final TestUser USER_B =
      new TestUser(
          USER_B_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of(PROCESS_ID_B)),
              new Permissions(
                  AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.DEPLOYED_RESOURCES.name()))));

  /*
   * User C has READ permission for USER_TASKS audit log category only. They should only see
   * audit logs related to user tasks.
   */
  @UserDefinition
  private static final TestUser USER_C =
      new TestUser(
          USER_C_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.USER_TASKS.name()))));

  /*
   * User D has READ_PROCESS_INSTANCE permission for PROCESS_ID only. They should only see
   * audit logs related to that process definition.
   */
  @UserDefinition
  private static final TestUser USER_D =
      new TestUser(
          USER_D_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_INSTANCE,
                  List.of(PROCESS_ID_DEPLOYED_RESOURCES))));

  /*
   * User E has READ_USER_TASK permission for USER_TASKS_PROCESS_ID only. They should only see
   * audit logs related to the user tasks of that process definition.
   */
  @UserDefinition
  private static final TestUser USER_E =
      new TestUser(
          USER_E_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(USER_TASKS_PROCESS_ID))));

  /*
   * User F has AUDIT_LOG READ permission for ADMIN category and READ_PROCESS_INSTANCE permission
   * for PROCESS_ID_DEPLOYED_RESOURCES and READ_USER_TASK permission for USER_TASKS_PROCESS_ID.
   * They should see audit logs related to ADMIN category as well as audit logs related to those
   * process definitions.
   */
  @UserDefinition
  private static final TestUser USER_F =
      new TestUser(
          USER_F_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.ADMIN.name())),
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_INSTANCE,
                  List.of(PROCESS_ID_DEPLOYED_RESOURCES)),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(USER_TASKS_PROCESS_ID))));

  private static CamundaClient adminClient;

  @BeforeAll
  static void setUp() {
    AuditLogUtils.generateData(adminClient, USE_REST_API);

    assignUserToTenant(adminClient, USER_A_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER_B_USERNAME, TENANT_B);
    assignUserToTenant(adminClient, USER_C_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER_D_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER_E_USERNAME, TENANT_B);
    assignUserToTenant(adminClient, USER_F_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER_F_USERNAME, TENANT_B);
  }

  @Test
  void shouldIsolateAuditLogsByTenant(
      @Authenticated(USER_A_USERNAME) final CamundaClient tenantAUserClient,
      @Authenticated(USER_B_USERNAME) final CamundaClient tenantBUserClient) {
    // when
    final var userAAuditLogs = tenantAUserClient.newAuditLogSearchRequest().send().join();
    final var userBAuditLogs = tenantBUserClient.newAuditLogSearchRequest().send().join();

    // then
    // User A should only see audit logs from TENANT_A and PROCESS_ID_A
    assertThat(userAAuditLogs.items()).isNotEmpty();
    assertThat(userAAuditLogs.items().stream().allMatch(log -> TENANT_A.equals(log.getTenantId())))
        .isTrue();
    assertThat(
            userAAuditLogs.items().stream()
                .anyMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            userAAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
        .isTrue();

    // User B should only see audit logs from TENANT_B and PROCESS_ID_B
    assertThat(userBAuditLogs.items()).isNotEmpty();
    assertThat(userBAuditLogs.items().stream().allMatch(log -> TENANT_B.equals(log.getTenantId())))
        .isTrue();
    assertThat(
            userBAuditLogs.items().stream()
                .anyMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            userBAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();
  }

  @Test
  void shouldNotSeeDeployedResourceAuditLogsWithOnlyUserTasksCategoryPermission(
      @Authenticated(USER_C_USERNAME) final CamundaClient userTaskUserClient) {
    // when
    final var userCAuditLogs =
        userTaskUserClient
            .newAuditLogSearchRequest()
            .filter(f -> f.category(AuditLogCategoryEnum.DEPLOYED_RESOURCES))
            .send()
            .join();

    // then
    assertThat(userCAuditLogs.items()).isEmpty();

    // Admin should see the DEPLOYED_RESOURCES category audit log
    final var adminAuditLogs =
        adminClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.category(AuditLogCategoryEnum.DEPLOYED_RESOURCES))
            .send()
            .join();
    assertThat(adminAuditLogs.items()).isNotEmpty();
    assertThat(adminAuditLogs.items().get(0).getCategory())
        .isEqualTo(AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldOnlySeeAllowedAuditLogsForMultipleAuditLogPermissions(
      @Authenticated(USER_F_USERNAME) final CamundaClient multiPermissionUserClient) {
    // when
    final var userFAuditLogs = multiPermissionUserClient.newAuditLogSearchRequest().send().join();

    // then
    // User F should see audit logs from ADMIN category, DEPLOYED_RESOURCES process instances,
    // and USER_TASKS from USER_TASKS_PROCESS_ID
    assertThat(userFAuditLogs.items()).isNotEmpty();

    // Verify User F can see ADMIN category audit logs
    assertThat(
            userFAuditLogs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.ADMIN.equals(log.getCategory())))
        .isTrue();

    // Verify User F can see DEPLOYED_RESOURCES process instance audit logs
    assertThat(
            userFAuditLogs.items().stream()
                .anyMatch(
                    log ->
                        PROCESS_ID_DEPLOYED_RESOURCES.equals(log.getProcessDefinitionId())
                            && AuditLogCategoryEnum.DEPLOYED_RESOURCES.equals(log.getCategory())))
        .isTrue();

    // Verify User F can see USER_TASKS audit logs from USER_TASKS_PROCESS_ID
    assertThat(
            userFAuditLogs.items().stream()
                .anyMatch(
                    log ->
                        USER_TASKS_PROCESS_ID.equals(log.getProcessDefinitionId())
                            && AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();

    // Verify User F cannot see audit logs from PROCESS_ID_A
    assertThat(
            userFAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify User F cannot see audit logs from PROCESS_ID_B
    assertThat(
            userFAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify that admin can see all these audit logs
    final var adminAuditLogs = adminClient.newAuditLogSearchRequest().send().join();
    assertThat(adminAuditLogs.items()).hasSizeGreaterThan(userFAuditLogs.items().size());

    // Verify admin can see all the categories and process definitions
    assertThat(
            adminAuditLogs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.ADMIN.equals(log.getCategory())))
        .isTrue();
    assertThat(
            adminAuditLogs.items().stream()
                .anyMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            adminAuditLogs.items().stream()
                .anyMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
        .isTrue();
  }

  @Test
  void shouldOnlySeeAuditLogsForAuthorizedProcessDefinition(
      @Authenticated(USER_D_USERNAME) final CamundaClient processDefinitionOnlyClient) {
    // when
    final var userDAuditLogs =
        processDefinitionOnlyClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    // User D should only see audit logs from PROCESS_ID
    assertThat(userDAuditLogs.items().size()).isOne();
    assertThat(
            userDAuditLogs.items().stream()
                .allMatch(
                    log -> PROCESS_ID_DEPLOYED_RESOURCES.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify they cannot see audit logs from PROCESS_ID_A
    assertThat(
            userDAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify that the audit log entry for PROCESS_ID exists for admin
    final var adminAuditLogs =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.category(AuditLogCategoryEnum.DEPLOYED_RESOURCES)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // Admin should see both audit logs
    assertThat(adminAuditLogs.items().size()).isGreaterThanOrEqualTo(2);
    assertThat(
            adminAuditLogs.items().stream()
                .map(log -> log.getProcessDefinitionId())
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(
            PROCESS_ID_DEPLOYED_RESOURCES, PROCESS_ID_A, PROCESS_ID_B, USER_TASKS_PROCESS_ID);
  }

  @Test
  void shouldOnlySeeAuditLogsForAuthorizedUserTaskProcessDefinition(
      @Authenticated(USER_E_USERNAME) final CamundaClient userTaskPDOnlyClient) {
    // when
    final var userEAuditLogs = userTaskPDOnlyClient.newAuditLogSearchRequest().send().join();

    // then
    // User E should only see audit logs from PROCESS_ID_C
    assertThat(userEAuditLogs.items()).hasSize(1);
    assertThat(
            userEAuditLogs.items().stream()
                .allMatch(log -> USER_TASKS_PROCESS_ID.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify they cannot see audit logs from PROCESS_ID
    assertThat(
            userEAuditLogs.items().stream()
                .noneMatch(
                    log -> PROCESS_ID_DEPLOYED_RESOURCES.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify they cannot see audit logs from PROCESS_ID_A
    assertThat(
            userEAuditLogs.items().stream()
                .noneMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
        .isTrue();

    // Verify that the audit log entry for PROCESS_ID_C exists for admin
    final var adminAuditLogs = adminClient.newAuditLogSearchRequest().send().join();

    // Admin should see the PROCESS_ID_C audit log
    assertThat(adminAuditLogs.items()).hasSizeGreaterThan(1);
    assertThat(
            adminAuditLogs.items().stream()
                .anyMatch(log -> USER_TASKS_PROCESS_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
  }
}
