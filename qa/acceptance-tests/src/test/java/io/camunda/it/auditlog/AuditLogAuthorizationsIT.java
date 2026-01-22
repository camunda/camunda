/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.AUDIT_LOG;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A_ID;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_B_ID;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_C_ID;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
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

  private static final String USER_UNAUTHORIZED_NAME = "UnauthorizedUser";
  private static final String USER_A_USERNAME = "userA";
  private static final String USER_AA_USERNAME = "userAA";
  private static final String USER_B_USERNAME = "userB";
  private static final String USER_BB_USERNAME = "userBB";
  private static final String USER_C_USERNAME = "userC";
  private static final String USER_CC_USERNAME = "userCC";
  private static final String USER_D_USERNAME = "userD";
  private static final String USER_E_USERNAME = "userE";
  private static final String USER_F_USERNAME = "userF";
  private static final String PASSWORD = "password";
  private static final List WILDCARD = List.of(AuthorizationScope.WILDCARD.getResourceId());

  @UserDefinition
  private static final TestUser USER_UNAUTHORIZED =
      new TestUser(USER_UNAUTHORIZED_NAME, PASSWORD, List.of());

  // With AUDIT_LOG#READ permissions
  @UserDefinition
  private static final TestUser USER_A =
      new TestUser(USER_A_USERNAME, PASSWORD, List.of(new Permissions(AUDIT_LOG, READ, WILDCARD)));

  @UserDefinition
  private static final TestUser USER_AA =
      new TestUser(
          USER_AA_USERNAME,
          PASSWORD,
          List.of(new Permissions(AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.ADMIN.name()))));

  // With PROCESS_DEFINITION#READ_PROCESS_INSTANCE permission
  @UserDefinition
  private static final TestUser USER_B =
      new TestUser(
          USER_B_USERNAME,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, WILDCARD)));

  @UserDefinition
  private static final TestUser USER_BB =
      new TestUser(
          USER_BB_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_A_ID))));

  @UserDefinition
  private static final TestUser USER_C =
      new TestUser(
          USER_C_USERNAME,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, WILDCARD)));

  @UserDefinition
  private static final TestUser USER_CC =
      new TestUser(
          USER_CC_USERNAME,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_C_ID))));

  @UserDefinition
  private static final TestUser USER_D =
      new TestUser(
          USER_D_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_A_ID)),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_B_ID))));

  // User with two different AUDIT_LOG categories
  @UserDefinition
  private static final TestUser USER_E =
      new TestUser(
          USER_E_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(
                  AUDIT_LOG,
                  READ,
                  List.of(
                      AuditLogCategoryEnum.USER_TASKS.name(),
                      AuditLogCategoryEnum.DEPLOYED_RESOURCES.name()))));

  // User with ADMIN read and PROCESS_DEFINITION+READ_USER_TASK for PROCESS_B
  @UserDefinition
  private static final TestUser USER_F =
      new TestUser(
          USER_F_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(AUDIT_LOG, READ, List.of(AuditLogCategoryEnum.ADMIN.name())),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_B_ID))));

  // At test execution time, we already have 100+ audit log entries.
  // Custom page limit ensures all entries are retrieved for test assertions.
  private static final Consumer<SearchRequestPage> PAGE_CONFIG = p -> p.limit(200);

  private static CamundaClient adminClient;

  @BeforeAll
  static void setUp() {
    final var utils = new AuditLogUtils(adminClient);
    utils.generateDefaults();
    utils.assignUserToTenant(USER_UNAUTHORIZED_NAME, TENANT_A);
    utils.assignUserToTenant(USER_A_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_AA_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_B_USERNAME, TENANT_B);
    utils.assignUserToTenant(USER_BB_USERNAME, TENANT_B);
    utils.assignUserToTenant(USER_C_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_CC_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_C_USERNAME, TENANT_B);
    utils.assignUserToTenant(USER_CC_USERNAME, TENANT_B);
    utils.assignUserToTenant(USER_D_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_E_USERNAME, TENANT_A);
    utils.assignUserToTenant(USER_F_USERNAME, TENANT_A);
    utils.await();
  }

  @Test
  void shouldNotAuthorizeWithoutPermissionsSearch(
      @Authenticated(USER_UNAUTHORIZED_NAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isEmpty();
  }

  /**
   * Verifies that the authorization filter blocks all access when no relevant permissions are
   * configured.
   */
  @Test
  void shouldDenyAllAccessWhenNoRelevantPermissions(
      @Authenticated(USER_UNAUTHORIZED_NAME) final CamundaClient unauthorizedClient,
      @Authenticated(USER_A_USERNAME) final CamundaClient authorizedClient) {
    // given - verify that audit logs exist in the system
    final var authorizedLogs = authorizedClient.newAuditLogSearchRequest().send().join();
    assertThat(authorizedLogs.items())
        .as("Audit logs should exist in the system for the authorized user")
        .isNotEmpty();

    // when - search with a user that has no audit log related permissions
    final var unauthorizedLogs = unauthorizedClient.newAuditLogSearchRequest().send().join();

    // then - the deny-all filter should block all access
    assertThat(unauthorizedLogs.items())
        .as("User without permissions should get zero results due to denyAll authorization filter")
        .isEmpty();
  }

  @Test
  void shouldNotAuthorizeWithoutPermissionsGetByKey(
      @Authenticated(USER_UNAUTHORIZED_NAME) final CamundaClient unauthorizedClient,
      @Authenticated(USER_A_USERNAME) final CamundaClient authorizedClient) {
    // when
    final var logs = authorizedClient.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then - unauthorized client should throw exception when trying to get by key
    assertThat(logs.items()).isNotEmpty();
    assertThatThrownBy(
            () ->
                unauthorizedClient
                    .newAuditLogGetRequest(logs.items().getFirst().getAuditLogKey())
                    .send()
                    .join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldAuthorizeByTenant(@Authenticated(USER_A_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(
                    log -> Objects.isNull(log.getTenantId()) || TENANT_A.equals(log.getTenantId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByCategoryWildcard(
      @Authenticated(USER_A_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.ADMIN.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.DEPLOYED_RESOURCES.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByCategory(@Authenticated(USER_AA_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(log -> AuditLogCategoryEnum.ADMIN.equals(log.getCategory())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByProcessIdWildcard(
      @Authenticated(USER_B_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(
                    log ->
                        AuditLogCategoryEnum.DEPLOYED_RESOURCES.equals(log.getCategory())
                            || AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> PROCESS_A_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> PROCESS_C_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByProcessId(@Authenticated(USER_BB_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(log -> PROCESS_A_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByUserTaskProcessIdWildcard(
      @Authenticated(USER_C_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(log -> AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> PROCESS_B_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> PROCESS_C_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByUserTaskProcessId(
      @Authenticated(USER_CC_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(log -> AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .allMatch(log -> PROCESS_C_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldNotAuthorizeNonUserTaskAuditLogsGetByKey(
      @Authenticated(USER_CC_USERNAME) final CamundaClient client) {
    // given (workaround as filter for processDefinitionId is not implemented yet)
    final var processDefinitionKey =
        client
            .newAuditLogSearchRequest()
            .send()
            .join()
            .items()
            .getFirst()
            .getProcessDefinitionKey();

    final var nonUserTaskLogs =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                f ->
                    f.category(c -> c.neq(AuditLogCategoryEnum.USER_TASKS))
                        .processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    final var unauthorizedLog = nonUserTaskLogs.items().getFirst();
    assertThatThrownBy(
            () -> client.newAuditLogGetRequest(unauthorizedLog.getAuditLogKey()).send().join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldAuthorizeWithCompositePermissions(
      @Authenticated(USER_D_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    assertThat(
            logs.items().stream()
                .allMatch(
                    log ->
                        PROCESS_A_ID.equals(log.getProcessDefinitionId())
                            || PROCESS_B_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .filter(log -> PROCESS_B_ID.equals(log.getProcessDefinitionId()))
                .allMatch(log -> AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByTwoDifferentAuditLogCategories(
      @Authenticated(USER_E_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    // Verify only USER_TASKS and DEPLOYED_RESOURCES categories are returned
    assertThat(
            logs.items().stream()
                .allMatch(
                    log ->
                        AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())
                            || AuditLogCategoryEnum.DEPLOYED_RESOURCES.equals(log.getCategory())))
        .isTrue();
    // Verify both categories are present
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())))
        .isTrue();
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.DEPLOYED_RESOURCES.equals(log.getCategory())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  @Test
  void shouldAuthorizeByAdminCategoryAndProcessDefinitionReadUserTask(
      @Authenticated(USER_F_USERNAME) final CamundaClient client) {
    // when
    final var logs = client.newAuditLogSearchRequest().page(PAGE_CONFIG).execute();

    // then
    assertThat(logs.items()).isNotEmpty();
    // Verify all logs are either ADMIN category or USER_TASKS with PROCESS_B
    assertThat(
            logs.items().stream()
                .allMatch(
                    log ->
                        AuditLogCategoryEnum.ADMIN.equals(log.getCategory())
                            || (AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())
                                && PROCESS_B_ID.equals(log.getProcessDefinitionId()))))
        .isTrue();
    // Verify user can see ADMIN category logs
    assertThat(
            logs.items().stream()
                .anyMatch(log -> AuditLogCategoryEnum.ADMIN.equals(log.getCategory())))
        .isTrue();
    // Verify user can see USER_TASKS logs for PROCESS_B
    assertThat(
            logs.items().stream()
                .anyMatch(
                    log ->
                        AuditLogCategoryEnum.USER_TASKS.equals(log.getCategory())
                            && PROCESS_B_ID.equals(log.getProcessDefinitionId())))
        .isTrue();
    assertGetByKeyAccess(client, logs.items().getFirst());
  }

  private void assertGetByKeyAccess(final CamundaClient client, final AuditLogResult log) {
    final var fetchedLog = client.newAuditLogGetRequest(log.getAuditLogKey()).send().join();

    // then
    assertThat(fetchedLog).isNotNull();
    assertThat(fetchedLog.getAuditLogKey()).isEqualTo(log.getAuditLogKey());
  }
}
