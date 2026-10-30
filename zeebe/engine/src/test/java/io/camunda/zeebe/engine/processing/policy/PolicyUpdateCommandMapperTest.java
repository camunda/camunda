/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.NO_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.IdentityCommand;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PolicyUpdateCommandMapperTest {

  private static final long VERSION = 3L;

  private MutableProcessingState processingState;
  private PolicyUpdateCommandMapper mapper;
  private long nextKey;

  @BeforeEach
  void setUp() {
    mapper =
        new PolicyUpdateCommandMapper(
            processingState, EngineSecurityConfigurations.defaultConfig());
    nextKey = 1000L;
  }

  @Nested
  final class EntityUpserts {

    @Test
    void shouldCreateEntitiesThatDoNotExist() {
      // given
      final var chunk =
          chunk()
              .setTenants(List.<TenantRecordValue>of(tenant("tenant-" + uid())))
              .setRoles(List.<RoleRecordValue>of(role("role-" + uid())))
              .setGroups(List.<GroupRecordValue>of(group("group-" + uid())))
              .setMappingRules(
                  List.<MappingRuleRecordValue>of(mappingRule("rule-" + uid(), "claim-" + uid())));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .extracting(IdentityCommand::intent)
          .containsExactly(
              TenantIntent.CREATE, RoleIntent.CREATE, GroupIntent.CREATE, MappingRuleIntent.CREATE);
      assertThat(commands).extracting(IdentityCommand::key).containsOnly(NO_KEY);
    }

    @Test
    void shouldUpdateEntitiesThatExist() {
      // given
      final var tenantKey = createTenant("tenant-" + uid());
      final var roleKey = createRole("role-" + uid());
      final var groupKey = createGroup("group-" + uid());
      final var ruleKey = createMappingRule("rule-" + uid(), "claim-" + uid());
      final var chunk =
          chunk()
              .setTenants(List.<TenantRecordValue>of(tenant(tenantKey.id())))
              .setRoles(List.<RoleRecordValue>of(role(roleKey.id())))
              .setGroups(List.<GroupRecordValue>of(group(groupKey.id())))
              .setMappingRules(
                  List.<MappingRuleRecordValue>of(
                      mappingRule(ruleKey.id(), "other-claim-" + uid())));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .extracting(IdentityCommand::intent)
          .containsExactly(
              TenantIntent.UPDATE, RoleIntent.UPDATE, GroupIntent.UPDATE, MappingRuleIntent.UPDATE);
      assertThat(((TenantRecord) commands.get(0).value()).getTenantKey())
          .isEqualTo(tenantKey.key());
      assertThat(((RoleRecord) commands.get(1).value()).getRoleKey()).isEqualTo(roleKey.key());
      assertThat(((GroupRecord) commands.get(2).value()).getGroupKey()).isEqualTo(groupKey.key());
      assertThat(((MappingRuleRecord) commands.get(3).value()).getMappingRuleKey())
          .isEqualTo(ruleKey.key());
    }

    @Test
    void shouldCarryThePolicyVersionOfTheChunk() {
      // given
      final var chunk = chunk().setRoles(List.<RoleRecordValue>of(role("role-" + uid())));

      // when
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(((RoleRecord) commands.getFirst().value()).getPolicyVersion()).isEqualTo(VERSION);
    }

    @Test
    void shouldRejectChangingADefaultRole() {
      // given
      final var defaultRoleId = DefaultRole.ADMIN.getId();
      processingState
          .getRoleState()
          .create(new RoleRecord().setRoleKey(1L).setRoleId(defaultRoleId));
      final var chunk = chunk().setRoles(List.<RoleRecordValue>of(role(defaultRoleId)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains(defaultRoleId, "default role");
    }

    @Test
    void shouldRejectAnEntityWithoutId() {
      // given
      final var chunk = chunk().setGroups(List.<GroupRecordValue>of(group("")));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("group ID");
    }

    @Test
    void shouldRejectAMappingRuleWithTheClaimOfAnotherRule() {
      // given
      final var claimValue = "claim-" + uid();
      final var existingKey = createMappingRule("rule-" + uid(), claimValue);
      final var chunk =
          chunk()
              .setMappingRules(
                  List.<MappingRuleRecordValue>of(mappingRule("rule-" + uid(), claimValue)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors)
          .singleElement()
          .asString()
          .contains(existingKey.id(), "already has this claim");
    }
  }

  @Nested
  final class Memberships {

    @Test
    void shouldAddAMemberToATargetUpsertedInTheSameChunk() {
      // given
      final var roleId = "role-" + uid();
      final var chunk =
          chunk()
              .setRoles(List.<RoleRecordValue>of(role(roleId)))
              .setMemberships(
                  List.<PolicyMembershipEntryValue>of(
                      membership(
                          PolicyUpdateEntityType.ROLE, roleId, EntityType.CLIENT, "client")));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .extracting(IdentityCommand::intent)
          .containsExactly(RoleIntent.CREATE, RoleIntent.ADD_ENTITY);
      final var membership = (RoleRecord) commands.get(1).value();
      assertThat(membership.getRoleId()).isEqualTo(roleId);
      assertThat(membership.getEntityType()).isEqualTo(EntityType.CLIENT);
      assertThat(membership.getEntityId()).isEqualTo("client");
    }

    @Test
    void shouldSkipAMembershipThatAlreadyExists() {
      // given
      final var groupId = createGroup("group-" + uid()).id();
      processingState
          .getMembershipState()
          .insertRelation(EntityType.CLIENT, "client", RelationType.GROUP, groupId);
      final var chunk =
          chunk()
              .setMemberships(
                  List.<PolicyMembershipEntryValue>of(
                      membership(
                          PolicyUpdateEntityType.GROUP, groupId, EntityType.CLIENT, "client")));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands).isEmpty();
    }

    @Test
    void shouldRejectAMembershipOfAMissingTarget() {
      // given
      final var chunk =
          chunk()
              .setMemberships(
                  List.<PolicyMembershipEntryValue>of(
                      membership(
                          PolicyUpdateEntityType.TENANT,
                          "missing-" + uid(),
                          EntityType.CLIENT,
                          "client")));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("tenant does not exist");
    }

    @Test
    void shouldRejectAMemberTypeTheTargetDoesNotAccept() {
      // given
      final var groupId = createGroup("group-" + uid()).id();
      final var roleId = createRole("role-" + uid()).id();
      final var chunk =
          chunk()
              .setMemberships(
                  List.<PolicyMembershipEntryValue>of(
                      membership(PolicyUpdateEntityType.GROUP, groupId, EntityType.ROLE, roleId)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("cannot be a member of a group");
    }

    @Test
    void shouldRemoveAnExistingMembership() {
      // given
      final var tenantKey = createTenant("tenant-" + uid());
      processingState
          .getMembershipState()
          .insertRelation(EntityType.CLIENT, "client", RelationType.TENANT, tenantKey.id());
      final var chunk =
          chunk()
              .setMembershipDeletes(
                  List.<PolicyMembershipEntryValue>of(
                      membership(
                          PolicyUpdateEntityType.TENANT,
                          tenantKey.id(),
                          EntityType.CLIENT,
                          "client")));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .singleElement()
          .extracting(IdentityCommand::intent)
          .isEqualTo(TenantIntent.REMOVE_ENTITY);
      assertThat(((TenantRecord) commands.getFirst().value()).getTenantKey())
          .isEqualTo(tenantKey.key());
    }

    @Test
    void shouldRejectRemovingAMembershipThatDoesNotExist() {
      // given
      final var roleId = createRole("role-" + uid()).id();
      final var chunk =
          chunk()
              .setMembershipDeletes(
                  List.<PolicyMembershipEntryValue>of(
                      membership(
                          PolicyUpdateEntityType.ROLE, roleId, EntityType.CLIENT, "client")));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("is not a member");
    }
  }

  @Nested
  final class Authorizations {

    @Test
    void shouldCreateAnAuthorizationForAnOwnerUpsertedInTheSameChunk() {
      // given
      final var roleId = "role-" + uid();
      final var chunk =
          chunk()
              .setRoles(List.<RoleRecordValue>of(role(roleId)))
              .setAuthorizations(
                  List.<AuthorizationRecordValue>of(
                      authorization(
                          roleId, "process-" + uid(), PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .extracting(IdentityCommand::intent)
          .containsExactly(RoleIntent.CREATE, AuthorizationIntent.CREATE);
    }

    @Test
    void shouldReconcileAnAuthorizationWithTheSameOwnerAndResource() {
      // given
      final var roleId = createRole("role-" + uid()).id();
      final var processId = "process-" + uid();
      final var authorizationKey =
          createAuthorization(roleId, processId, PermissionType.READ_PROCESS_DEFINITION);
      final var chunk =
          chunk()
              .setAuthorizations(
                  List.<AuthorizationRecordValue>of(
                      authorization(roleId, processId, PermissionType.UPDATE_PROCESS_INSTANCE)));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .singleElement()
          .extracting(IdentityCommand::intent)
          .isEqualTo(AuthorizationIntent.UPDATE);
      final var update = (AuthorizationRecord) commands.getFirst().value();
      assertThat(update.getAuthorizationKey()).isEqualTo(authorizationKey);
      assertThat(update.getPermissionTypes())
          .containsExactly(PermissionType.UPDATE_PROCESS_INSTANCE);
    }

    @Test
    void shouldRejectAnAuthorizationOfAMissingOwner() {
      // given
      final var chunk =
          chunk()
              .setAuthorizations(
                  List.<AuthorizationRecordValue>of(
                      authorization(
                          "missing-" + uid(),
                          "process-" + uid(),
                          PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("owner does not exist");
    }

    @Test
    void shouldRejectAPermissionTheResourceTypeDoesNotSupport() {
      // given
      final var roleId = createRole("role-" + uid()).id();
      final var chunk =
          chunk()
              .setAuthorizations(
                  List.<AuthorizationRecordValue>of(
                      authorization(
                          roleId,
                          "process-" + uid(),
                          PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("supports only");
    }

    @Test
    void shouldDeleteAnExistingAuthorization() {
      // given
      final var roleId = createRole("role-" + uid()).id();
      final var processId = "process-" + uid();
      final var authorizationKey =
          createAuthorization(roleId, processId, PermissionType.READ_PROCESS_DEFINITION);
      final var chunk =
          chunk()
              .setAuthorizationDeletes(
                  List.<AuthorizationRecordValue>of(
                      authorization(roleId, processId, PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .singleElement()
          .extracting(IdentityCommand::intent)
          .isEqualTo(AuthorizationIntent.DELETE);
      assertThat(((AuthorizationRecord) commands.getFirst().value()).getAuthorizationKey())
          .isEqualTo(authorizationKey);
    }

    @Test
    void shouldRejectDeletingAnAuthorizationThatDoesNotExist() {
      // given
      final var roleId = createRole("role-" + uid()).id();
      final var chunk =
          chunk()
              .setAuthorizationDeletes(
                  List.<AuthorizationRecordValue>of(
                      authorization(
                          roleId, "process-" + uid(), PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("no such authorization exists");
    }
  }

  @Nested
  final class EntityDeletes {

    @Test
    void shouldDeleteExistingEntities() {
      // given
      final var tenantKey = createTenant("tenant-" + uid());
      final var roleKey = createRole("role-" + uid());
      final var groupKey = createGroup("group-" + uid());
      final var ruleKey = createMappingRule("rule-" + uid(), "claim-" + uid());
      final var chunk =
          chunk()
              .setDeletes(
                  List.<PolicyDeleteEntryValue>of(
                      delete(PolicyUpdateEntityType.MAPPING_RULE, ruleKey.id()),
                      delete(PolicyUpdateEntityType.GROUP, groupKey.id()),
                      delete(PolicyUpdateEntityType.ROLE, roleKey.id()),
                      delete(PolicyUpdateEntityType.TENANT, tenantKey.id())));

      // when
      final var errors = mapper.validate(chunk);
      final var commands = mapper.toCommands(chunk);

      // then
      assertThat(errors).isEmpty();
      assertThat(commands)
          .extracting(IdentityCommand::intent)
          .containsExactly(
              MappingRuleIntent.DELETE, GroupIntent.DELETE, RoleIntent.DELETE, TenantIntent.DELETE);
      assertThat(((RoleRecord) commands.get(2).value()).getRoleKey()).isEqualTo(roleKey.key());
    }

    @Test
    void shouldRejectDeletingAnEntityThatDoesNotExist() {
      // given
      final var chunk =
          chunk()
              .setDeletes(
                  List.<PolicyDeleteEntryValue>of(
                      delete(PolicyUpdateEntityType.GROUP, "missing-" + uid())));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("does not exist");
    }

    @Test
    void shouldRejectDeletingADefaultRole() {
      // given
      final var defaultRoleId = DefaultRole.ADMIN.getId();
      processingState
          .getRoleState()
          .create(new RoleRecord().setRoleKey(1L).setRoleId(defaultRoleId));
      final var chunk =
          chunk()
              .setDeletes(
                  List.<PolicyDeleteEntryValue>of(
                      delete(PolicyUpdateEntityType.ROLE, defaultRoleId)));

      // when
      final var errors = mapper.validate(chunk);

      // then
      assertThat(errors).singleElement().asString().contains("default role");
    }
  }

  @Test
  void shouldMapAllSectionsInApplyOrder() {
    // given
    final var tenantId = createTenant("tenant-" + uid()).id();
    final var groupId = createGroup("group-" + uid()).id();
    final var oldRoleId = createRole("role-" + uid()).id();
    final var processId = "process-" + uid();
    createAuthorization(oldRoleId, processId, PermissionType.READ_PROCESS_DEFINITION);
    processingState
        .getMembershipState()
        .insertRelation(EntityType.CLIENT, "client", RelationType.GROUP, groupId);
    final var newRoleId = "role-" + uid();
    final var chunk =
        chunk()
            .setDeletes(
                List.<PolicyDeleteEntryValue>of(delete(PolicyUpdateEntityType.ROLE, oldRoleId)))
            .setMembershipDeletes(
                List.<PolicyMembershipEntryValue>of(
                    membership(PolicyUpdateEntityType.GROUP, groupId, EntityType.CLIENT, "client")))
            .setAuthorizationDeletes(
                List.<AuthorizationRecordValue>of(
                    authorization(oldRoleId, processId, PermissionType.READ_PROCESS_DEFINITION)))
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(newRoleId, processId, PermissionType.READ_PROCESS_DEFINITION)))
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    membership(
                        PolicyUpdateEntityType.TENANT, tenantId, EntityType.ROLE, newRoleId)))
            .setRoles(List.<RoleRecordValue>of(role(newRoleId)));

    // when
    final var errors = mapper.validate(chunk);
    final var commands = mapper.toCommands(chunk);

    // then
    assertThat(errors).isEmpty();
    assertThat(commands)
        .extracting(IdentityCommand::intent)
        .containsExactly(
            RoleIntent.CREATE,
            TenantIntent.ADD_ENTITY,
            AuthorizationIntent.CREATE,
            AuthorizationIntent.DELETE,
            GroupIntent.REMOVE_ENTITY,
            RoleIntent.DELETE);
  }

  // ------------------------------------------------------------------------------------------

  private static PolicyUpdateRecord chunk() {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + uid())
        .setPolicyVersion(VERSION)
        .setUpdateId("update-" + uid())
        .setChunkIndex(0)
        .setChunkCount(1);
  }

  private static TenantRecord tenant(final String tenantId) {
    return new TenantRecord().setTenantId(tenantId).setName(tenantId);
  }

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId).setName(roleId);
  }

  private static GroupRecord group(final String groupId) {
    return new GroupRecord().setGroupId(groupId).setName(groupId);
  }

  private static MappingRuleRecord mappingRule(
      final String mappingRuleId, final String claimValue) {
    return new MappingRuleRecord()
        .setMappingRuleId(mappingRuleId)
        .setName(mappingRuleId)
        .setClaimName("department")
        .setClaimValue(claimValue);
  }

  private static PolicyMembershipEntry membership(
      final PolicyUpdateEntityType targetType,
      final String targetId,
      final EntityType memberType,
      final String memberId) {
    return new PolicyMembershipEntry()
        .setTargetType(targetType)
        .setTargetId(targetId)
        .setMemberType(memberType)
        .setMemberId(memberId);
  }

  private static AuthorizationRecord authorization(
      final String roleId, final String processId, final PermissionType permission) {
    return new AuthorizationRecord()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId(roleId)
        .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId(processId)
        .setPermissionTypes(Set.of(permission));
  }

  private static PolicyDeleteEntry delete(final PolicyUpdateEntityType type, final String id) {
    return new PolicyDeleteEntry().setEntityType(type).setId(id);
  }

  private Created createTenant(final String tenantId) {
    final long key = nextKey++;
    processingState
        .getTenantState()
        .createTenant(new TenantRecord().setTenantKey(key).setTenantId(tenantId).setName(tenantId));
    return new Created(key, tenantId);
  }

  private Created createRole(final String roleId) {
    final long key = nextKey++;
    processingState
        .getRoleState()
        .create(new RoleRecord().setRoleKey(key).setRoleId(roleId).setName(roleId));
    return new Created(key, roleId);
  }

  private Created createGroup(final String groupId) {
    final long key = nextKey++;
    processingState
        .getGroupState()
        .create(new GroupRecord().setGroupKey(key).setGroupId(groupId).setName(groupId));
    return new Created(key, groupId);
  }

  private Created createMappingRule(final String mappingRuleId, final String claimValue) {
    final long key = nextKey++;
    processingState
        .getMappingRuleState()
        .create(mappingRule(mappingRuleId, claimValue).setMappingRuleKey(key));
    return new Created(key, mappingRuleId);
  }

  private long createAuthorization(
      final String roleId, final String processId, final PermissionType permission) {
    final long key = nextKey++;
    processingState
        .getAuthorizationState()
        .create(key, authorization(roleId, processId, permission).setAuthorizationKey(key));
    return key;
  }

  private record Created(long key, String id) {}

  private static String uid() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
