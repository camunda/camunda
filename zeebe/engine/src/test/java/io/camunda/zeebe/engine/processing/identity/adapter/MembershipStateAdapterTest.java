/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import static io.camunda.zeebe.auth.Authorization.LOGIN_SOURCE;
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
import io.camunda.zeebe.engine.state.appliers.UserCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleType;
import io.camunda.zeebe.test.util.Strings;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MembershipStateAdapterTest {

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private MembershipStateAdapter adapter;
  private SimpleMeterRegistry meterRegistry;
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
    meterRegistry = new SimpleMeterRegistry();
    adapter =
        new MembershipStateAdapter(
            processingState.getMappingRuleState(),
            processingState.getMembershipState(),
            processingState.getUserState(),
            processingState.getGroupState(),
            new EngineConfiguration(),
            meterRegistry);
    mappingRuleCreatedApplier =
        new MappingRuleCreatedApplier(processingState.getMappingRuleState());
    groupCreatedApplier = new GroupCreatedApplier(processingState.getGroupState());
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
    roleCreatedApplier = new RoleCreatedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
    tenantCreatedApplier = new TenantCreatedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
  }

  @AfterEach
  void tearDown() {
    meterRegistry.close();
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
  void shouldMatchUserAttributeRuleAgainstStoredUserAttributes() {
    // given
    final var ruleId = createAttributeRule("$.department", "Finance");
    createUser("finance-user", Map.of("department", "Finance"));
    createUser("other-user", Map.of("department", "Sales"));
    createUser("plain-user", Map.of());

    // when / then
    assertThat(adapter.mappingRuleIds(userQuery("finance-user", Map.of()))).containsExactly(ruleId);
    assertThat(adapter.mappingRuleIds(userQuery("other-user", Map.of()))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("plain-user", Map.of()))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("unknown-user", Map.of()))).isEmpty();
  }

  @Test
  void shouldNotMatchUserAttributeRuleAgainstTokenClaims() {
    // given
    createAttributeRule("$.department", "Finance");
    createUser("sales-user", Map.of("department", "Sales"));
    createUser("plain-user", Map.of());

    // when / then - a token claiming the attribute must not satisfy the rule
    final var spoofed = Map.<String, Object>of("department", "Finance");
    assertThat(adapter.mappingRuleIds(userQuery("sales-user", spoofed))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("plain-user", spoofed))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("unknown-user", spoofed))).isEmpty();
  }

  @Test
  void shouldNotMatchUserAttributeRuleForClientPrincipal() {
    // given
    createAttributeRule("$.department", "Finance");
    createUser("finance-user", Map.of("department", "Finance"));

    // when
    final var result =
        adapter.mappingRuleIds(new MembershipQuery(Map.of(), "finance-user", PrincipalType.CLIENT));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldMatchTokenClaimRuleOnlyAgainstTokenClaimsNotUserAttributes() {
    // given
    final var ruleId = createTokenRule("department", "Finance");
    createUser("finance-user", Map.of("department", "Finance"));

    // when / then
    assertThat(adapter.mappingRuleIds(userQuery("finance-user", Map.of()))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("finance-user", Map.of("department", "Finance"))))
        .containsExactly(ruleId);
  }

  @Test
  void shouldMatchGroupAttributeRuleForDirectGroupMember() {
    // given
    final var ruleId = createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE);
    createGroupWithAttributes("member-user", EntityType.USER, Map.of("region", "EMEA"));

    // when
    final var result = adapter.mappingRuleIds(userQuery("member-user", Map.of()));

    // then
    assertThat(result).containsExactly(ruleId);
  }

  @Test
  void shouldNotMatchGroupAttributeRuleForNonMember() {
    // given
    createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE);
    createGroupWithAttributes("member-user", EntityType.USER, Map.of("region", "EMEA"));

    // when
    final var result = adapter.mappingRuleIds(userQuery("other-user", Map.of()));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNotMatchGroupAttributeRuleAgainstTokenClaims() {
    // given
    createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE);
    createGroupWithAttributes("member-user", EntityType.USER, Map.of("region", "APAC"));

    // when
    final var result = adapter.mappingRuleIds(userQuery("member-user", Map.of("region", "EMEA")));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNotMatchGroupAttributeRuleForClientPrincipal() {
    // given
    createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE);
    createGroupWithAttributes("member-client", EntityType.CLIENT, Map.of("region", "EMEA"));

    // when
    final var result =
        adapter.mappingRuleIds(
            new MembershipQuery(Map.of(), "member-client", PrincipalType.CLIENT));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldOnlyMatchTokenClaimRuleWithOriginForLoginOfThatSource() {
    // given
    final var anyRule = createRule("department", "Finance", MappingRuleType.TOKEN_CLAIM, "");
    final var entraRule = createRule("department", "Sales", MappingRuleType.TOKEN_CLAIM, "entra");
    final var claims = Map.<String, Object>of("department", "Sales");

    // when / then
    assertThat(adapter.mappingRuleIds(loginQuery("alice", claims, "entra")))
        .containsExactly(entraRule);
    assertThat(adapter.mappingRuleIds(loginQuery("alice", claims, "keycloak"))).isEmpty();
    assertThat(adapter.mappingRuleIds(loginQuery("alice", claims, null))).isEmpty();
    assertThat(
            adapter.mappingRuleIds(
                loginQuery("alice", Map.of("department", "Finance"), "keycloak")))
        .containsExactly(anyRule);
  }

  @Test
  void shouldNotTakeLoginSourceFromTokenClaims() {
    // given
    createRule("department", "Sales", MappingRuleType.TOKEN_CLAIM, "entra");
    final var claims = Map.<String, Object>of("department", "Sales", LOGIN_SOURCE, "entra");

    // when
    final var result = adapter.mappingRuleIds(loginQuery("alice", claims, null));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldOnlyMatchUserAttributeRuleWithOriginForUserOfThatSource() {
    // given
    final var anyRule = createRule("$.department", "Finance", MappingRuleType.USER_ATTRIBUTE, "");
    final var entraRule = createRule("$.team", "Finance", MappingRuleType.USER_ATTRIBUTE, "entra");
    final var attributes = Map.<String, Object>of("department", "Finance", "team", "Finance");
    createUser("entra-user", attributes, "entra");
    createUser("keycloak-user", attributes, "keycloak");
    createUser("unowned-user", attributes, "");

    // when / then
    assertThat(adapter.mappingRuleIds(userQuery("entra-user", Map.of())))
        .containsExactlyInAnyOrder(anyRule, entraRule);
    assertThat(adapter.mappingRuleIds(userQuery("keycloak-user", Map.of())))
        .containsExactly(anyRule);
    assertThat(adapter.mappingRuleIds(userQuery("unowned-user", Map.of())))
        .containsExactly(anyRule);
  }

  @Test
  void shouldIgnoreLoginSourceForUserAttributeRules() {
    // given
    createRule("$.department", "Finance", MappingRuleType.USER_ATTRIBUTE, "entra");
    createUser("keycloak-user", Map.of("department", "Finance"), "keycloak");

    // when
    final var result = adapter.mappingRuleIds(loginQuery("keycloak-user", Map.of(), "entra"));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldOnlyConsiderGroupsOfTheUsersSourceForGroupAttributeRules() {
    // given
    final var ruleId = createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE, "");
    createUser("alice", Map.of(), "entra");
    createGroupWithAttributes("alice", EntityType.USER, Map.of("region", "EMEA"), "keycloak");
    createUser("bob", Map.of(), "entra");
    createGroupWithAttributes("bob", EntityType.USER, Map.of("region", "EMEA"), "entra");

    // when / then
    assertThat(adapter.mappingRuleIds(userQuery("alice", Map.of()))).isEmpty();
    assertThat(adapter.mappingRuleIds(userQuery("bob", Map.of()))).containsExactly(ruleId);
  }

  @Test
  void shouldNotMatchGroupAttributeRuleWithOriginForUserOfOtherSource() {
    // given
    createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE, "entra");
    createUser("alice", Map.of(), "keycloak");
    createGroupWithAttributes("alice", EntityType.USER, Map.of("region", "EMEA"), "keycloak");

    // when
    final var result = adapter.mappingRuleIds(userQuery("alice", Map.of()));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldMatchGroupAttributeRuleWithOriginForUserOfThatSource() {
    // given
    final var ruleId = createRule("$.region", "EMEA", MappingRuleType.GROUP_ATTRIBUTE, "entra");
    createUser("alice", Map.of(), "entra");
    createGroupWithAttributes("alice", EntityType.USER, Map.of("region", "EMEA"), "entra");

    // when
    final var result = adapter.mappingRuleIds(userQuery("alice", Map.of()));

    // then
    assertThat(result).containsExactly(ruleId);
  }

  private static MembershipQuery loginQuery(
      final String username, final Map<String, Object> tokenClaims, final String loginSource) {
    final Map<String, Object> claims = new HashMap<>();
    claims.put(USER_TOKEN_CLAIMS, tokenClaims);
    if (loginSource != null) {
      claims.put(LOGIN_SOURCE, loginSource);
    }
    return new MembershipQuery(claims, username, PrincipalType.USER);
  }

  private void createGroupWithAttributes(
      final String entityId,
      final EntityType entityType,
      final Map<String, Object> attributes,
      final String source) {
    final var groupKey = random.nextLong();
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setAttributes(attributes)
            .setSource(source)
            .setEntityId(entityId)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
  }

  private String createRule(
      final String path, final String value, final MappingRuleType type, final String origin) {
    final var id = UUID.randomUUID().toString();
    mappingRuleCreatedApplier.applyState(
        random.nextLong(),
        new MappingRuleRecord()
            .setMappingRuleId(id)
            .setName(Strings.newRandomValidIdentityId())
            .setClaimName(path)
            .setClaimValue(value)
            .setType(type)
            .setOrigin(origin));
    return id;
  }

  private void createUser(
      final String username, final Map<String, Object> attributes, final String source) {
    final var userKey = random.nextLong();
    new UserCreatedApplier(processingState.getUserState())
        .applyState(
            userKey,
            new UserRecord()
                .setUserKey(userKey)
                .setUsername(username)
                .setName(username)
                .setEmail(username + "@example.com")
                .setPassword("password")
                .setAttributes(attributes)
                .setSource(source));
  }

  private void createGroupWithAttributes(
      final String entityId, final EntityType entityType, final Map<String, Object> attributes) {
    final var groupKey = random.nextLong();
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setAttributes(attributes)
            .setEntityId(entityId)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
  }

  private String createAttributeRule(final String path, final String value) {
    return createRule(path, value, MappingRuleType.USER_ATTRIBUTE);
  }

  private String createTokenRule(final String path, final String value) {
    return createRule(path, value, MappingRuleType.TOKEN_CLAIM);
  }

  private String createRule(final String path, final String value, final MappingRuleType type) {
    final var id = UUID.randomUUID().toString();
    mappingRuleCreatedApplier.applyState(
        random.nextLong(),
        new MappingRuleRecord()
            .setMappingRuleId(id)
            .setName(Strings.newRandomValidIdentityId())
            .setClaimName(path)
            .setClaimValue(value)
            .setType(type));
    return id;
  }

  private void createUser(final String username, final Map<String, Object> attributes) {
    final var userKey = random.nextLong();
    new UserCreatedApplier(processingState.getUserState())
        .applyState(
            userKey,
            new UserRecord()
                .setUserKey(userKey)
                .setUsername(username)
                .setName(username)
                .setEmail(username + "@example.com")
                .setPassword("password")
                .setAttributes(attributes));
  }

  private static MembershipQuery userQuery(
      final String username, final Map<String, Object> tokenClaims) {
    return new MembershipQuery(
        Map.of(USER_TOKEN_CLAIMS, tokenClaims), username, PrincipalType.USER);
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
  void shouldNotAddMappingRuleGroupsWhenGroupsClaimIsPresent() {
    // given — OIDC mode: groups are supplied via the token claim, and a matched mapping rule is
    // also a member of a group. The mapping-rule group must NOT widen the principal's groups. This
    // matches main's ClaimsExtractor.getGroups, which ignores mapping-rule group memberships when a
    // groups claim is present.
    final var mappingRuleId = UUID.randomUUID().toString();
    createGroupAndAssignEntity(mappingRuleId, EntityType.MAPPING_RULE);
    final var query =
        new MembershipQuery(
                Map.of(USER_GROUPS_CLAIMS, List.of("claim-group")), "userId", PrincipalType.USER)
            .withMappingRuleIds(List.of(mappingRuleId));

    // when
    final var result = adapter.groupIds(query);

    // then
    assertThat(result).containsExactly("claim-group");
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

  @Test
  void shouldRecordCacheMissThenHitOnRepeatedGroupIdsLookup() {
    // given
    final var userId = Strings.newRandomValidIdentityId();
    createGroupAndAssignEntity(userId, EntityType.USER);
    final var query = new MembershipQuery(Map.of(), userId, PrincipalType.USER);

    // when — first lookup misses and loads, second lookup hits the cache
    adapter.groupIds(query);
    adapter.groupIds(query);

    // then
    assertThat(
            meterRegistry
                .get("zeebe.authorization.membership.result")
                .tag("type", "MISS")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get("zeebe.authorization.membership.result")
                .tag("type", "HIT")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.get("zeebe.authorization.membership.load.duration.success").timer())
        .isNotNull();
  }

  @Test
  void shouldRegisterEvictionsMeter() {
    assertThat(meterRegistry.get("zeebe.authorization.membership.evictions").counter()).isNotNull();
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
