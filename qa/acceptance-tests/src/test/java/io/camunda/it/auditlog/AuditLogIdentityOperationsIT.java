/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogIdentityOperationsIT {

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

  // ==================== USER TESTS ====================

  @Test
  void shouldTrackUserCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.USER, AuditLogOperationTypeEnum.CREATE);

    // then
    assertThat(auditLogs).hasSizeGreaterThanOrEqualTo(1);
    assertAuditLog(
        findByEntityKey(auditLogs, DEFAULT_USERNAME),
        AuditLogEntityTypeEnum.USER,
        AuditLogOperationTypeEnum.CREATE,
        DEFAULT_USERNAME);
  }

  @Test
  void shouldTrackUserUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var newUsername = "user-" + Strings.newRandomValidIdentityId();
    client.newCreateUserCommand().username(newUsername).password("password").send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.USER, AuditLogOperationTypeEnum.CREATE, newUsername);

    // when
    client.newUpdateUserCommand(newUsername).email("new-email@test.com").send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.USER, AuditLogOperationTypeEnum.UPDATE, newUsername);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, newUsername),
        AuditLogEntityTypeEnum.USER,
        AuditLogOperationTypeEnum.UPDATE,
        newUsername);
  }

  @Test
  void shouldTrackUserDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var newUsername = "user-" + Strings.newRandomValidIdentityId();
    client.newCreateUserCommand().username(newUsername).password("password").send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.USER, AuditLogOperationTypeEnum.CREATE, newUsername);

    // when
    client.newDeleteUserCommand(newUsername).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.USER, AuditLogOperationTypeEnum.DELETE, newUsername);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, newUsername),
        AuditLogEntityTypeEnum.USER,
        AuditLogOperationTypeEnum.DELETE,
        newUsername);
  }

  // ==================== TENANT TESTS ====================

  @Test
  void shouldTrackTenantCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use shorter ID to stay within 31 character limit
    final var tenantId = "tnt-" + Strings.newRandomValidIdentityId().substring(0, 16);

    // when
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.CREATE, tenantId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, tenantId),
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.CREATE,
        tenantId);
  }

  @Test
  void shouldTrackTenantUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use shorter ID to stay within 31 character limit
    final var tenantId = "tnt-" + Strings.newRandomValidIdentityId().substring(0, 16);
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.CREATE, tenantId);

    // when
    client
        .newUpdateTenantCommand(tenantId)
        .name("Updated Name")
        .description("Updated description")
        .send()
        .join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.UPDATE, tenantId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, tenantId),
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.UPDATE,
        tenantId);
  }

  @Test
  void shouldTrackTenantDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use shorter ID to stay within 31 character limit
    final var tenantId = "tnt-" + Strings.newRandomValidIdentityId().substring(0, 16);
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.CREATE, tenantId);

    // when
    client.newDeleteTenantCommand(tenantId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.DELETE, tenantId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, tenantId),
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.DELETE,
        tenantId);
  }

  @Test
  void shouldTrackTenantAssign(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use shorter ID to stay within 31 character limit
    final var tenantId = "tnt-" + Strings.newRandomValidIdentityId().substring(0, 16);
    final var username = "usr-" + Strings.newRandomValidIdentityId().substring(0, 16);
    client.newCreateUserCommand().username(username).password("password").send().join();
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();

    // when
    client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.ASSIGN, tenantId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, tenantId),
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.ASSIGN,
        tenantId);
  }

  @Test
  void shouldTrackTenantRemoveAssignment(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use shorter ID to stay within 31 character limit
    final var tenantId = "tnt-" + Strings.newRandomValidIdentityId().substring(0, 16);
    final var username = "usr-" + Strings.newRandomValidIdentityId().substring(0, 16);
    client.newCreateUserCommand().username(username).password("password").send().join();
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
    client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.ASSIGN, tenantId);

    // when
    client.newUnassignUserFromTenantCommand().username(username).tenantId(tenantId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.TENANT, AuditLogOperationTypeEnum.UNASSIGN, tenantId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, tenantId),
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.UNASSIGN,
        tenantId);
  }

  // ==================== ROLE TESTS ====================

  @Test
  void shouldTrackRoleCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.CREATE);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, ROLE_A_ID),
        AuditLogEntityTypeEnum.ROLE,
        AuditLogOperationTypeEnum.CREATE,
        ROLE_A_ID);
  }

  @Test
  void shouldTrackRoleUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    client.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.CREATE, roleId);

    // when
    client
        .newUpdateRoleCommand(roleId)
        .name("Updated Role Name")
        .description("Updated description")
        .send()
        .join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.UPDATE, roleId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, roleId),
        AuditLogEntityTypeEnum.ROLE,
        AuditLogOperationTypeEnum.UPDATE,
        roleId);
  }

  @Test
  void shouldTrackRoleDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    client.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.CREATE, roleId);

    // when
    client.newDeleteRoleCommand(roleId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.DELETE, roleId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, roleId),
        AuditLogEntityTypeEnum.ROLE,
        AuditLogOperationTypeEnum.DELETE,
        roleId);
  }

  @Test
  void shouldTrackRoleAssign(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.ASSIGN);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, ROLE_A_ID),
        AuditLogEntityTypeEnum.ROLE,
        AuditLogOperationTypeEnum.ASSIGN,
        ROLE_A_ID);
  }

  @Test
  void shouldTrackRoleRemoveAssignment(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var username = "user-" + Strings.newRandomValidIdentityId();
    client.newCreateUserCommand().username(username).password("password").send().join();
    client.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
    client.newAssignRoleToUserCommand().roleId(roleId).username(username).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.ASSIGN, roleId);

    // when
    client.newUnassignRoleFromUserCommand().roleId(roleId).username(username).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.ROLE, AuditLogOperationTypeEnum.UNASSIGN, roleId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, roleId),
        AuditLogEntityTypeEnum.ROLE,
        AuditLogOperationTypeEnum.UNASSIGN,
        roleId);
  }

  // ==================== GROUP TESTS ====================

  @Test
  void shouldTrackGroupCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.CREATE);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, GROUP_A_ID),
        AuditLogEntityTypeEnum.GROUP,
        AuditLogOperationTypeEnum.CREATE,
        GROUP_A_ID);
  }

  @Test
  void shouldTrackGroupUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var groupId = "group-" + Strings.newRandomValidIdentityId();
    client.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.CREATE, groupId);

    // when
    client
        .newUpdateGroupCommand(groupId)
        .name("Updated Group Name")
        .description("Updated description")
        .send()
        .join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.UPDATE, groupId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, groupId),
        AuditLogEntityTypeEnum.GROUP,
        AuditLogOperationTypeEnum.UPDATE,
        groupId);
  }

  @Test
  void shouldTrackGroupDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var groupId = "group-" + Strings.newRandomValidIdentityId();
    client.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.CREATE, groupId);

    // when
    client.newDeleteGroupCommand(groupId).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.DELETE, groupId);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, groupId),
        AuditLogEntityTypeEnum.GROUP,
        AuditLogOperationTypeEnum.DELETE,
        groupId);
  }

  @Test
  void shouldTrackGroupAssign(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.ASSIGN);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, GROUP_A_ID),
        AuditLogEntityTypeEnum.GROUP,
        AuditLogOperationTypeEnum.ASSIGN,
        GROUP_A_ID);
  }

  @Test
  void shouldTrackGroupRemoveAssignment(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var groupId = "group-" + Strings.newRandomValidIdentityId();
    final var username = "user-" + Strings.newRandomValidIdentityId();
    client.newCreateUserCommand().username(username).password("password").send().join();
    client.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
    client.newAssignUserToGroupCommand().username(username).groupId(groupId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.ASSIGN, groupId);

    // when
    client.newUnassignUserFromGroupCommand().username(username).groupId(groupId).send().join();
    awaitAuditLogEntry(
        client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.UNASSIGN, groupId);

    // then
    final var auditLogs =
        findAuditLogs(client, AuditLogEntityTypeEnum.GROUP, AuditLogOperationTypeEnum.UNASSIGN);
    assertAuditLog(
        findByEntityKey(auditLogs, groupId),
        AuditLogEntityTypeEnum.GROUP,
        AuditLogOperationTypeEnum.UNASSIGN,
        groupId);
  }

  // ==================== MAPPING RULE TESTS ====================

  @Test
  void shouldTrackMappingRuleCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogs =
        findAuditLogs(
            client, AuditLogEntityTypeEnum.MAPPING_RULE, AuditLogOperationTypeEnum.CREATE);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, MAPPING_RULE_A.id()),
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.CREATE,
        MAPPING_RULE_A.id());
  }

  @Test
  void shouldTrackMappingRuleUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    client
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("Test Mapping Rule")
        .claimName("test-claim")
        .claimValue("test-value-" + mappingRuleId)
        .send()
        .join();
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.CREATE,
        mappingRuleId);

    // when
    client
        .newUpdateMappingRuleCommand(mappingRuleId)
        .name("Updated Name")
        .claimName("test-claim")
        .claimValue("updated-value")
        .send()
        .join();
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.UPDATE,
        mappingRuleId);

    // then
    final var auditLogs =
        findAuditLogs(
            client, AuditLogEntityTypeEnum.MAPPING_RULE, AuditLogOperationTypeEnum.UPDATE);
    assertAuditLog(
        findByEntityKey(auditLogs, mappingRuleId),
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.UPDATE,
        mappingRuleId);
  }

  @Test
  void shouldTrackMappingRuleDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    client
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("Test Mapping Rule")
        .claimName("test-claim")
        .claimValue("test-value-" + mappingRuleId)
        .send()
        .join();
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.CREATE,
        mappingRuleId);

    // when
    client.newDeleteMappingRuleCommand(mappingRuleId).send().join();
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.DELETE,
        mappingRuleId);

    // then
    final var auditLogs =
        findAuditLogs(
            client, AuditLogEntityTypeEnum.MAPPING_RULE, AuditLogOperationTypeEnum.DELETE);
    assertAuditLog(
        findByEntityKey(auditLogs, mappingRuleId),
        AuditLogEntityTypeEnum.MAPPING_RULE,
        AuditLogOperationTypeEnum.DELETE,
        mappingRuleId);
  }

  // ==================== AUTHORIZATION TESTS ====================

  @Test
  void shouldTrackAuthorizationCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var authorizationKey =
        createAuthorization(
            client,
            DEFAULT_USERNAME,
            ResourceType.PROCESS_DEFINITION,
            PermissionType.READ_PROCESS_INSTANCE);

    // when
    final var auditLogs =
        findAuditLogs(
            client, AuditLogEntityTypeEnum.AUTHORIZATION, AuditLogOperationTypeEnum.CREATE);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, authorizationKey),
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.CREATE,
        authorizationKey);
  }

  @Test
  void shouldTrackAuthorizationUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var authorization =
        client
            .newCreateAuthorizationCommand()
            .ownerId(DEFAULT_USERNAME)
            .ownerType(io.camunda.client.api.search.enums.OwnerType.USER)
            .resourceId("*")
            .resourceType(ResourceType.DECISION_DEFINITION)
            .permissionTypes(PermissionType.READ_DECISION_DEFINITION)
            .send()
            .join();
    final var authorizationKey = String.valueOf(authorization.getAuthorizationKey());
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.CREATE,
        authorizationKey);

    // when
    client
        .newUpdateAuthorizationCommand(authorization.getAuthorizationKey())
        .ownerId(DEFAULT_USERNAME)
        .ownerType(io.camunda.client.api.search.enums.OwnerType.USER)
        .resourceId("*")
        .resourceType(ResourceType.DECISION_DEFINITION)
        .permissionTypes(
            PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE)
        .send()
        .join();
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.UPDATE,
        authorizationKey);

    // then
    final var auditLogs =
        findAuditLogs(
            client, AuditLogEntityTypeEnum.AUTHORIZATION, AuditLogOperationTypeEnum.UPDATE);
    assertAuditLog(
        findByEntityKey(auditLogs, authorizationKey),
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.UPDATE,
        authorizationKey);
  }

  @Test
  void shouldTrackAuthorizationDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var authorization =
        client
            .newCreateAuthorizationCommand()
            .ownerId(DEFAULT_USERNAME)
            .ownerType(io.camunda.client.api.search.enums.OwnerType.USER)
            .resourceId("*")
            .resourceType(ResourceType.DECISION_DEFINITION)
            .permissionTypes(PermissionType.READ_DECISION_DEFINITION)
            .send()
            .join();
    final var authorizationKey = String.valueOf(authorization.getAuthorizationKey());
    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.CREATE,
        authorizationKey);

    // when
    client.newDeleteAuthorizationCommand(authorization.getAuthorizationKey()).send().join();
    final var auditLogs =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.AUTHORIZATION,
            AuditLogOperationTypeEnum.DELETE,
            authorizationKey);

    // then
    assertAuditLog(
        findByEntityKey(auditLogs, authorizationKey),
        AuditLogEntityTypeEnum.AUTHORIZATION,
        AuditLogOperationTypeEnum.DELETE,
        authorizationKey);
  }

  // ==================== HELPER METHODS ====================

  private List<AuditLogResult> findAuditLogs(
      final CamundaClient client,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType) {
    return client
        .newAuditLogSearchRequest()
        .filter(fn -> fn.entityType(entityType).operationType(operationType))
        .send()
        .join()
        .items();
  }

  private AuditLogResult findByEntityKey(
      final List<AuditLogResult> auditLogs, final String entityKey) {
    return auditLogs.stream()
        .filter(al -> entityKey.equals(al.getEntityKey()))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Audit log entry not found for entity key: " + entityKey));
  }

  private void assertAuditLog(
      final AuditLogResult auditLog,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final String entityKey) {
    assertThat(auditLog.getEntityType()).isEqualTo(entityType);
    assertThat(auditLog.getOperationType()).isEqualTo(operationType);
    assertThat(auditLog.getEntityKey()).isEqualTo(entityKey);
    assertThat(auditLog.getTenantId()).isNull();
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.ADMIN);
  }

  private List<AuditLogResult> awaitAuditLogEntry(
      final CamundaClient client,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final String entityKey) {
    return Awaitility.await("Audit log entry is created")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .until(
            () -> {
              final var logs = findAuditLogs(client, entityType, operationType);
              return logs.stream().anyMatch(l -> l.getEntityKey().equals(entityKey)) ? logs : null;
            },
            java.util.Objects::nonNull);
  }

  private static String createAuthorization(
      final CamundaClient client,
      final String username,
      final ResourceType authorizationResourceType,
      final PermissionType permission) {
    final var authorization =
        client
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
                  client
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
