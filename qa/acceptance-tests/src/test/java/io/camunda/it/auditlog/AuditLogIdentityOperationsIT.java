/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogIdentityOperationsIT {

  public static AuditLogUtils utils;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String MAPPING_RULE_CLAIM_VALUE = "test-claim-value";
  private static final String GROUP_A_ID = "AGroupName";
  private static final String ROLE_A_ID = "ARoleName";

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_A =
      new TestMappingRule(
          Strings.newRandomValidIdentityId(),
          TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_NAME,
          MAPPING_RULE_CLAIM_VALUE);

  @GroupDefinition
  private static final TestGroup GROUP_A =
      TestGroup.withoutPermissions(
          GROUP_A_ID, GROUP_A_ID, List.of(new Membership(DEFAULT_USERNAME, EntityType.USER)));

  @RoleDefinition
  private static final TestRole ROLE_A =
      TestRole.withoutPermissions(
          ROLE_A_ID, ROLE_A_ID, List.of(new Membership(DEFAULT_USERNAME, EntityType.USER)));

  private static CamundaClient adminClient;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient).init();
  }

  @Test
  void shouldTrackUserCreationForAdminUser() {
    // when
    final var auditLogUserCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.USER)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then - verify that the admin/demo user creation was tracked
    assertThat(auditLogUserCreateItems.items()).hasSizeGreaterThanOrEqualTo(1);

    final var adminUserCreateLog =
        auditLogUserCreateItems.items().stream()
            .filter(al -> DEFAULT_USERNAME.equals(al.getEntityKey()))
            .findFirst();

    assertThat(adminUserCreateLog).isPresent();
    final var auditLogCreate = adminUserCreateLog.get();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.USER);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldTrackMappingRuleCreation() {
    // when
    final var auditLogMappingRuleCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.MAPPING_RULE)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then - verify that the mapping rule creation was tracked
    assertThat(auditLogMappingRuleCreateItems.items()).hasSizeGreaterThanOrEqualTo(1);

    final var mappingRuleCreateLog =
        auditLogMappingRuleCreateItems.items().stream()
            .filter(al -> MAPPING_RULE_A.id().equals(al.getEntityKey()))
            .findFirst();

    assertThat(mappingRuleCreateLog).isPresent();
    final var auditLogCreate = mappingRuleCreateLog.get();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.MAPPING_RULE);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(MAPPING_RULE_A.id());
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldTrackAuthorizationOperationsCorrectly() {
    final var authorizationKey =
        createAuthorization(
            DEFAULT_USERNAME,
            ResourceType.PROCESS_DEFINITION,
            PermissionType.READ_PROCESS_INSTANCE);

    // when
    final var auditLogAuthorizationItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.entityType(AuditLogEntityTypeEnum.AUTHORIZATION))
            .send()
            .join();
    final var auditLogAuthorizationCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.AUTHORIZATION)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    assertThat(auditLogAuthorizationItems.items()).hasSizeGreaterThanOrEqualTo(1);
    final var auditLogEntities =
        auditLogAuthorizationItems.items().stream()
            .map(AuditLogResult::getEntityType)
            .collect(Collectors.toSet());
    assertThat(auditLogEntities).contains(AuditLogEntityTypeEnum.AUTHORIZATION);

    assertThat(auditLogAuthorizationCreateItems.items()).hasSizeGreaterThanOrEqualTo(1);

    // Find the specific authorization we created
    final var authorizationCreateLog =
        auditLogAuthorizationCreateItems.items().stream()
            .filter(al -> String.valueOf(authorizationKey).equals(al.getEntityKey()))
            .findFirst();

    assertThat(authorizationCreateLog).isPresent();
    final var auditLogCreate = authorizationCreateLog.get();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.AUTHORIZATION);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(String.valueOf(authorizationKey));
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldTrackGroupOperationsCorrectly() {
    // when
    final var auditLogGroupItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.entityType(AuditLogEntityTypeEnum.GROUP))
            .send()
            .join();
    final var auditLogGroupCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.GROUP)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();
    final var auditLogGroupAssignItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.GROUP)
                        .operationType(AuditLogOperationTypeEnum.ASSIGN))
            .send()
            .join();

    // then
    assertThat(auditLogGroupItems.items()).hasSize(2);
    final var auditLogEntities =
        auditLogGroupItems.items().stream()
            .map(AuditLogResult::getEntityType)
            .collect(Collectors.toSet());
    assertThat(auditLogEntities).containsExactlyInAnyOrder(AuditLogEntityTypeEnum.GROUP);
    final var auditLogOperations =
        auditLogGroupItems.items().stream()
            .map(AuditLogResult::getOperationType)
            .collect(Collectors.toSet());
    assertThat(auditLogOperations)
        .containsExactlyInAnyOrder(
            AuditLogOperationTypeEnum.CREATE, AuditLogOperationTypeEnum.ASSIGN);

    assertThat(auditLogGroupCreateItems.items()).hasSize(1);
    final var auditLogCreate = auditLogGroupCreateItems.items().getFirst();
    assertThat(auditLogCreate).isNotNull();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.GROUP);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(GROUP_A_ID);
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);

    assertThat(auditLogGroupAssignItems.items()).hasSize(1);
    final var auditLogAssign = auditLogGroupAssignItems.items().getFirst();
    assertThat(auditLogAssign).isNotNull();
    assertThat(auditLogAssign.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.GROUP);
    assertThat(auditLogAssign.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.ASSIGN);
    assertThat(auditLogAssign.getEntityKey()).isEqualTo(GROUP_A_ID);
    assertThat(auditLogAssign.getTenantId()).isNull();
    assertThat(auditLogAssign.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldTrackRoleOperationsCorrectly() {
    // when
    final var auditLogRoleItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.entityType(AuditLogEntityTypeEnum.ROLE))
            .send()
            .join();
    final var auditLogRoleCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.ROLE)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();
    final var auditLogRoleAssignItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.ROLE)
                        .operationType(AuditLogOperationTypeEnum.ASSIGN))
            .send()
            .join();

    // then
    final var auditLogRoleAItems =
        auditLogRoleItems.items().stream()
            .filter(alr -> ROLE_A_ID.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogRoleAItems).hasSize(2);
    final var auditLogOperations =
        auditLogRoleAItems.stream()
            .map(AuditLogResult::getOperationType)
            .collect(Collectors.toSet());
    assertThat(auditLogOperations)
        .containsExactlyInAnyOrder(
            AuditLogOperationTypeEnum.CREATE, AuditLogOperationTypeEnum.ASSIGN);

    final var auditLogRoleACreateItems =
        auditLogRoleCreateItems.items().stream()
            .filter(alr -> ROLE_A_ID.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogRoleACreateItems).hasSize(1);
    final var auditLogCreate = auditLogRoleACreateItems.getFirst();
    assertThat(auditLogCreate).isNotNull();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.ROLE);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(ROLE_A_ID);
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);

    final var auditLogRoleAAssignItems =
        auditLogRoleAssignItems.items().stream()
            .filter(alr -> ROLE_A_ID.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogRoleAAssignItems).hasSize(1);
    final var auditLogAssign = auditLogRoleAAssignItems.getFirst();
    assertThat(auditLogAssign).isNotNull();
    assertThat(auditLogAssign.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.ROLE);
    assertThat(auditLogAssign.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.ASSIGN);
    assertThat(auditLogAssign.getEntityKey()).isEqualTo(ROLE_A_ID);
    assertThat(auditLogAssign.getTenantId()).isNull();
    assertThat(auditLogAssign.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldTrackTenantOperationsCorrectly() {
    // when
    final var auditLogTenantItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(fn -> fn.entityType(AuditLogEntityTypeEnum.TENANT))
            .send()
            .join();
    final var auditLogTenantCreateItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.TENANT)
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();
    final var auditLogTenantAssignItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityType(AuditLogEntityTypeEnum.TENANT)
                        .operationType(AuditLogOperationTypeEnum.ASSIGN))
            .send()
            .join();

    // then
    final var auditLogTenantAItems =
        auditLogTenantItems.items().stream()
            .filter(alr -> TENANT_A.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogTenantAItems).hasSize(2);
    final var auditLogEntities =
        auditLogTenantAItems.stream()
            .map(AuditLogResult::getEntityType)
            .collect(Collectors.toSet());
    assertThat(auditLogEntities).containsExactlyInAnyOrder(AuditLogEntityTypeEnum.TENANT);
    final var auditLogOperations =
        auditLogTenantAItems.stream()
            .map(AuditLogResult::getOperationType)
            .collect(Collectors.toSet());
    assertThat(auditLogOperations)
        .containsExactlyInAnyOrder(
            AuditLogOperationTypeEnum.CREATE, AuditLogOperationTypeEnum.ASSIGN);

    // Verify TENANT_A creation
    final var auditLogTenantACreateItems =
        auditLogTenantCreateItems.items().stream()
            .filter(alr -> TENANT_A.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogTenantACreateItems).hasSize(1);
    final var auditLogCreate = auditLogTenantACreateItems.getFirst();
    assertThat(auditLogCreate.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.TENANT);
    assertThat(auditLogCreate.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLogCreate.getEntityKey()).isEqualTo(TENANT_A);
    assertThat(auditLogCreate.getTenantId()).isNull();
    assertThat(auditLogCreate.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);

    // Verify TENANT_A user assignment
    final var auditLogTenantAAssignItems =
        auditLogTenantAssignItems.items().stream()
            .filter(alr -> TENANT_A.equals(alr.getEntityKey()))
            .collect(Collectors.toList());
    assertThat(auditLogTenantAAssignItems).hasSize(1);
    final var auditLogAssign = auditLogTenantAAssignItems.getFirst();
    assertThat(auditLogAssign.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.TENANT);
    assertThat(auditLogAssign.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.ASSIGN);
    assertThat(auditLogAssign.getEntityKey()).isEqualTo(TENANT_A);
    assertThat(auditLogAssign.getTenantId()).isNull();
    assertThat(auditLogAssign.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  private static String createAuthorization(
      final String username,
      final ResourceType authorizationResourceType,
      final PermissionType permission) {
    final var authorization =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(username)
            .ownerType(io.camunda.client.api.search.enums.OwnerType.USER)
            .resourceId("*")
            .resourceType(authorizationResourceType)
            .permissionTypes(permission)
            .send()
            .join();

    final var authorizationKey = String.valueOf(authorization.getAuthorizationKey());
    Awaitility.await("Audit log entry is created for the authorization")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var logs =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.entityType(AuditLogEntityTypeEnum.AUTHORIZATION)
                                  .operationType(AuditLogOperationTypeEnum.CREATE))
                      .send()
                      .join();
              assertThat(logs.items())
                  .anyMatch(l -> l.getEntityKey().equals(authorizationKey))
                  .isNotEmpty();
            });

    return authorizationKey;
  }
}
