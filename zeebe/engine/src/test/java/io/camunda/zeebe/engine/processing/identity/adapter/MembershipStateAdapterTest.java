/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.appliers.GroupCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.MappingRuleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantEntityAddedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MembershipStateAdapterTest {

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private MembershipStateAdapter adapter;
  private MappingRuleCreatedApplier mappingRuleCreatedApplier;
  private GroupCreatedApplier groupCreatedApplier;
  private GroupEntityAddedApplier groupEntityAddedApplier;
  private RoleCreatedApplier roleCreatedApplier;
  private RoleEntityAddedApplier roleEntityAddedApplier;
  private TenantCreatedApplier tenantCreatedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
  private final Random random = new Random();

  @BeforeEach
  void setup() {
    adapter =
        new MembershipStateAdapter(
            processingState.getMappingRuleState(),
            processingState.getMembershipState(),
            new EngineConfiguration());
    mappingRuleCreatedApplier =
        new MappingRuleCreatedApplier(processingState.getMappingRuleState());
    groupCreatedApplier = new GroupCreatedApplier(processingState.getGroupState());
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
    roleCreatedApplier = new RoleCreatedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
    tenantCreatedApplier = new TenantCreatedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
  }

  @Test
  void shouldReturnMappingRuleIdsForMatchingTokenClaims() {
    // given
    final var claimName = "role";
    final var claimValue = "admin";
    final var mappingRuleId = UUID.randomUUID().toString();
    final var rule =
        new MappingRuleRecord()
            .setMappingRuleId(mappingRuleId)
            .setName(Strings.newRandomValidIdentityId())
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingRuleCreatedApplier.applyState(random.nextLong(), rule);
    final var query =
        new MembershipQuery(
            Map.of(USER_TOKEN_CLAIMS, Map.of(claimName, claimValue)), "user1", PrincipalType.USER);

    // when
    final var result = adapter.mappingRuleIds(query);

    // then
    assertThat(result).containsExactly(mappingRuleId);
  }

  @Test
  void shouldReturnEmptyMappingRuleIdsWhenTokenClaimsDoNotMatch() {
    // given
    final var rule =
        new MappingRuleRecord()
            .setMappingRuleId(UUID.randomUUID().toString())
            .setName(Strings.newRandomValidIdentityId())
            .setClaimName("role")
            .setClaimValue("admin");
    mappingRuleCreatedApplier.applyState(random.nextLong(), rule);
    final var query = new MembershipQuery(Map.of(), "user1", PrincipalType.USER);

    // when
    final var result = adapter.mappingRuleIds(query);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnGroupIdsFromTokenClaims() {
    // given — USER_GROUPS_CLAIMS present → skip DB lookup
    final var query =
        new MembershipQuery(
            Map.of(USER_GROUPS_CLAIMS, List.of("group1", "group2")), "user1", PrincipalType.USER);

    // when
    final var result = adapter.groupIds(query);

    // then
    assertThat(result).containsExactly("group1", "group2");
  }

  @Test
  void shouldFallBackToMembershipStateWhenNoGroupsClaims() {
    // given — no USER_GROUPS_CLAIMS → DB lookup
    final var userId = Strings.newRandomValidIdentityId();
    final var groupId = createGroupAndAssignEntity(userId, EntityType.USER).getGroupId();
    final var query = new MembershipQuery(Map.of(), userId, PrincipalType.USER);

    // when
    final var result = adapter.groupIds(query);

    // then
    assertThat(result).containsExactly(groupId);
  }

  @Test
  void shouldReturnGroupIdsFromMatchedMappingRules() {
    // given — mapping rule assigned to a group; no USER_GROUPS_CLAIMS
    final var mappingRuleId = UUID.randomUUID().toString();
    final var groupId =
        createGroupAndAssignEntity(mappingRuleId, EntityType.MAPPING_RULE).getGroupId();
    final var query =
        new MembershipQuery(Map.of(), "userId", PrincipalType.USER)
            .withMappingRuleIds(List.of(mappingRuleId));

    // when
    final var result = adapter.groupIds(query);

    // then
    assertThat(result).containsExactly(groupId);
  }

  @Test
  void shouldReturnRoleIdsForPrincipalAndGroups() {
    // given
    final var userId = Strings.newRandomValidIdentityId();
    final var directRoleId = createRoleAndAssignEntity(userId, EntityType.USER).getRoleId();
    final var groupId = createGroupAndAssignEntity(userId, EntityType.USER).getGroupId();
    final var groupRoleId = createRoleAndAssignEntity(groupId, EntityType.GROUP).getRoleId();
    final var query =
        new MembershipQuery(Map.of(), userId, PrincipalType.USER).withGroupIds(List.of(groupId));

    // when
    final var result = adapter.roleIds(query);

    // then
    assertThat(result).containsExactlyInAnyOrder(directRoleId, groupRoleId);
  }

  @Test
  void shouldDeduplicateRoleIds() {
    // given — two groups both assigned to the same role
    final var userId = Strings.newRandomValidIdentityId();
    final var group1Id = createGroupAndAssignEntity(userId, EntityType.USER).getGroupId();
    final var group2Id = createGroupAndAssignEntity(userId, EntityType.USER).getGroupId();
    final var sharedRoleKey = random.nextLong();
    final var sharedRoleId = Strings.newRandomValidIdentityId();
    final var sharedRole =
        new RoleRecord()
            .setRoleKey(sharedRoleKey)
            .setRoleId(sharedRoleId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString());
    roleCreatedApplier.applyState(sharedRoleKey, sharedRole);
    roleEntityAddedApplier.applyState(
        sharedRoleKey, sharedRole.setEntityId(group1Id).setEntityType(EntityType.GROUP));
    roleEntityAddedApplier.applyState(
        sharedRoleKey, sharedRole.setEntityId(group2Id).setEntityType(EntityType.GROUP));
    final var query =
        new MembershipQuery(Map.of(), userId, PrincipalType.USER)
            .withGroupIds(List.of(group1Id, group2Id));

    // when
    final var result = adapter.roleIds(query);

    // then
    assertThat(result).containsOnlyOnce(sharedRoleId);
  }

  @Test
  void shouldReturnTenantIdsViaAllInheritancePaths() {
    // given
    final var userId = Strings.newRandomValidIdentityId();
    final var directTenantId = createAndAssignTenant(userId, EntityType.USER);
    final var directRoleId = createRoleAndAssignEntity(userId, EntityType.USER).getRoleId();
    final var roleTenantId = createAndAssignTenant(directRoleId, EntityType.ROLE);
    final var groupId = createGroupAndAssignEntity(userId, EntityType.USER).getGroupId();
    final var groupTenantId = createAndAssignTenant(groupId, EntityType.GROUP);
    final var groupRoleId = createRoleAndAssignEntity(groupId, EntityType.GROUP).getRoleId();
    final var groupRoleTenantId = createAndAssignTenant(groupRoleId, EntityType.ROLE);
    final var query =
        new MembershipQuery(Map.of(), userId, PrincipalType.USER)
            .withGroupIds(List.of(groupId))
            .withRoleIds(List.of(directRoleId, groupRoleId));

    // when
    final var result = adapter.tenantIds(query);

    // then
    assertThat(result)
        .containsExactlyInAnyOrder(directTenantId, roleTenantId, groupTenantId, groupRoleTenantId);
  }

  @Test
  void shouldReturnRoleIdsFromMappingRuleDirectRoleMembership() {
    // given — mapping rule assigned directly to a role
    final var mappingRuleId = UUID.randomUUID().toString();
    final var roleId =
        createRoleAndAssignEntity(mappingRuleId, EntityType.MAPPING_RULE).getRoleId();
    final var query =
        new MembershipQuery(Map.of(), "userId", PrincipalType.USER)
            .withMappingRuleIds(List.of(mappingRuleId));

    // when
    final var result = adapter.roleIds(query);

    // then
    assertThat(result).containsExactly(roleId);
  }

  @Test
  void shouldReturnTenantIdsFromMappingRuleDirectTenantMembership() {
    // given — mapping rule assigned directly to a tenant
    final var mappingRuleId = UUID.randomUUID().toString();
    final var tenantId = createAndAssignTenant(mappingRuleId, EntityType.MAPPING_RULE);
    final var query =
        new MembershipQuery(Map.of(), "userId", PrincipalType.USER)
            .withMappingRuleIds(List.of(mappingRuleId));

    // when
    final var result = adapter.tenantIds(query);

    // then
    assertThat(result).containsExactly(tenantId);
  }

  // --- helpers ---

  private GroupRecord createGroupAndAssignEntity(
      final String entityId, final EntityType entityType) {
    final var groupKey = random.nextLong();
    final var groupId = Strings.newRandomValidIdentityId();
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(groupId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
    return group;
  }

  private RoleRecord createRoleAndAssignEntity(final String entityId, final EntityType entityType) {
    final var roleKey = random.nextLong();
    final var roleId = Strings.newRandomValidIdentityId();
    final var role =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setRoleId(roleId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    roleCreatedApplier.applyState(roleKey, role);
    roleEntityAddedApplier.applyState(roleKey, role);
    return role;
  }

  private String createAndAssignTenant(final String entityId, final EntityType entityType) {
    final var tenantKey = random.nextLong();
    final var tenant =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString());
    tenantCreatedApplier.applyState(tenantKey, tenant);
    tenant.setEntityId(entityId).setEntityType(entityType);
    tenantEntityAddedApplier.applyState(tenantKey, tenant);
    return tenant.getTenantId();
  }
}
