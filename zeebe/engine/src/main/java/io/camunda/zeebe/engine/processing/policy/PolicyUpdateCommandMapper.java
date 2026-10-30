/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.identity.AuthorizationEntityValidator;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps a chunk of a policy update to the identity commands that apply it, and validates the chunk
 * against the current identity state before that.
 *
 * <p>The commands follow the apply order of the change set: upserts of tenants, roles, groups and
 * mapping rules; membership adds; authorization upserts; authorization deletes; membership removes;
 * deletes of mapping rules, groups, roles and tenants. They are processed in that order after the
 * command that wrote them, so an entity upserted in the chunk counts as existing for the entries
 * that follow it.
 *
 * <p>Validation mirrors the checks of the identity processors, so a valid chunk does not produce
 * commands that those processors reject. Deleting an entity, membership or authorization that does
 * not exist is invalid: the policy is then out of sync with this cluster.
 */
@NullMarked
public final class PolicyUpdateCommandMapper {

  /** The key of a command that does not refer to an existing record. */
  static final long NO_KEY = -1L;

  private static final String WILDCARD_RESOURCE_ID = "*";

  private final ProcessingState state;
  private final EngineSecurityConfig securityConfig;
  private final AuthorizationEntityValidator authorizationValidator;

  public PolicyUpdateCommandMapper(
      final ProcessingState state, final EngineSecurityConfig securityConfig) {
    this.state = state;
    this.securityConfig = securityConfig;
    authorizationValidator = new AuthorizationEntityValidator(state, securityConfig);
  }

  /**
   * Validates the chunk against the current state.
   *
   * @return every problem found, or an empty list if the chunk can be applied
   */
  public List<String> validate(final PolicyUpdateRecordValue chunk) {
    final var upserted = UpsertedIds.of(chunk);
    final var errors = new ArrayList<String>();
    chunk.getTenants().forEach(tenant -> validateTenantUpsert(tenant, errors));
    chunk.getRoles().forEach(role -> validateRoleUpsert(role, errors));
    chunk.getGroups().forEach(group -> validateGroupUpsert(group, errors));
    chunk.getMappingRules().forEach(rule -> validateMappingRuleUpsert(rule, errors));
    chunk
        .getMemberships()
        .forEach(membership -> validateMembershipAdd(membership, upserted, errors));
    chunk
        .getAuthorizations()
        .forEach(authorization -> validateAuthorizationUpsert(authorization, upserted, errors));
    chunk
        .getAuthorizationDeletes()
        .forEach(authorization -> validateAuthorizationDelete(authorization, errors));
    chunk
        .getMembershipDeletes()
        .forEach(membership -> validateMembershipRemove(membership, errors));
    chunk.getDeletes().forEach(delete -> validateDelete(delete, errors));
    return errors;
  }

  /**
   * Maps the chunk to identity commands, in apply order. The chunk must be valid, see {@link
   * #validate(PolicyUpdateRecordValue)}.
   */
  public List<IdentityCommand> toCommands(final PolicyUpdateRecordValue chunk) {
    final long version = chunk.getPolicyVersion();
    final var commands = new ArrayList<IdentityCommand>();
    chunk.getTenants().forEach(tenant -> commands.add(tenantUpsert(tenant, version)));
    chunk.getRoles().forEach(role -> commands.add(roleUpsert(role, version)));
    chunk.getGroups().forEach(group -> commands.add(groupUpsert(group, version)));
    chunk.getMappingRules().forEach(rule -> commands.add(mappingRuleUpsert(rule, version)));
    chunk
        .getMemberships()
        .forEach(membership -> membershipAdd(membership, version).ifPresent(commands::add));
    chunk
        .getAuthorizations()
        .forEach(authorization -> commands.add(authorizationUpsert(authorization, version)));
    chunk
        .getAuthorizationDeletes()
        .forEach(authorization -> commands.add(authorizationDelete(authorization, version)));
    chunk
        .getMembershipDeletes()
        .forEach(membership -> commands.add(membershipRemove(membership, version)));
    chunk.getDeletes().forEach(delete -> commands.add(entityDelete(delete, version)));
    return commands;
  }

  // ------------------------------------------------------------------------------------------
  // validation
  // ------------------------------------------------------------------------------------------

  private void validateTenantUpsert(final TenantRecordValue tenant, final List<String> errors) {
    if (tenant.getTenantId().isEmpty()) {
      errors.add("Expected a tenant upsert to have a tenant ID, but it was empty");
    }
  }

  private void validateRoleUpsert(final RoleRecordValue role, final List<String> errors) {
    final var roleId = role.getRoleId();
    if (roleId.isEmpty()) {
      errors.add("Expected a role upsert to have a role ID, but it was empty");
    } else if (DefaultRole.ids().contains(roleId) && roleExistsInState(roleId)) {
      errors.add(
          "Expected to upsert role '%s', but it is a default role and cannot be changed"
              .formatted(roleId));
    }
  }

  private void validateGroupUpsert(final GroupRecordValue group, final List<String> errors) {
    if (group.getGroupId().isEmpty()) {
      errors.add("Expected a group upsert to have a group ID, but it was empty");
    }
  }

  private void validateMappingRuleUpsert(
      final MappingRuleRecordValue rule, final List<String> errors) {
    final var mappingRuleId = rule.getMappingRuleId();
    if (mappingRuleId.isEmpty()) {
      errors.add("Expected a mapping rule upsert to have a mapping rule ID, but it was empty");
      return;
    }
    state
        .getMappingRuleState()
        .get(rule.getClaimName(), rule.getClaimValue())
        .map(existing -> existing.getMappingRuleId())
        .filter(existingId -> !existingId.equals(mappingRuleId))
        .ifPresent(
            existingId ->
                errors.add(
                    "Expected to upsert mapping rule '%s' with claim '%s=%s', but mapping rule '%s' already has this claim"
                        .formatted(
                            mappingRuleId, rule.getClaimName(), rule.getClaimValue(), existingId)));
  }

  private void validateMembershipAdd(
      final PolicyMembershipEntryValue membership,
      final UpsertedIds upserted,
      final List<String> errors) {
    if (!targetExists(membership.getTargetType(), membership.getTargetId(), upserted)) {
      errors.add(
          "Expected to add %s '%s' to %s '%s', but the %s does not exist"
              .formatted(
                  describe(membership.getMemberType()),
                  membership.getMemberId(),
                  describe(membership.getTargetType()),
                  membership.getTargetId(),
                  describe(membership.getTargetType())));
    }
    if (!memberExists(
        membership.getTargetType(),
        membership.getMemberType(),
        membership.getMemberId(),
        upserted)) {
      errors.add(
          "Expected to add %s '%s' to %s '%s', but the member does not exist or cannot be a member of a %s"
              .formatted(
                  describe(membership.getMemberType()),
                  membership.getMemberId(),
                  describe(membership.getTargetType()),
                  membership.getTargetId(),
                  describe(membership.getTargetType())));
    }
  }

  private void validateMembershipRemove(
      final PolicyMembershipEntryValue membership, final List<String> errors) {
    if (!isAssigned(membership)) {
      errors.add(
          "Expected to remove %s '%s' from %s '%s', but it is not a member"
              .formatted(
                  describe(membership.getMemberType()),
                  membership.getMemberId(),
                  describe(membership.getTargetType()),
                  membership.getTargetId()));
    }
  }

  private void validateAuthorizationUpsert(
      final AuthorizationRecordValue authorization,
      final UpsertedIds upserted,
      final List<String> errors) {
    final var description = describe(authorization);
    final var record = authorizationRecord(authorization);
    final var matcherCheck = authorizationValidator.validateResourceMatcher(record, "upsert");
    if (matcherCheck.isLeft()) {
      errors.add(matcherCheck.getLeft().reason());
    }
    final var resourceType = authorization.getResourceType();
    if (!resourceType
        .getSupportedPermissionTypes()
        .containsAll(authorization.getPermissionTypes())) {
      errors.add(
          "Expected to upsert %s with permissions %s, but resource type %s supports only %s"
              .formatted(
                  description,
                  authorization.getPermissionTypes(),
                  resourceType,
                  resourceType.getSupportedPermissionTypes()));
    }
    if (!ownerExists(authorization.getOwnerType(), authorization.getOwnerId(), upserted)) {
      errors.add("Expected to upsert %s, but its owner does not exist".formatted(description));
    }
    if (!resourceExists(resourceType, authorization.getResourceId(), upserted)) {
      errors.add("Expected to upsert %s, but its resource does not exist".formatted(description));
    }
  }

  private void validateAuthorizationDelete(
      final AuthorizationRecordValue authorization, final List<String> errors) {
    if (findAuthorizationKey(authorization).isEmpty()) {
      errors.add(
          "Expected to delete %s, but no such authorization exists"
              .formatted(describe(authorization)));
    }
  }

  private void validateDelete(final PolicyDeleteEntryValue delete, final List<String> errors) {
    final var id = delete.getId();
    final var entityType = delete.getEntityType();
    if (entityType == PolicyUpdateEntityType.UNSPECIFIED) {
      errors.add(
          "Expected a delete of '%s' to name the entity type, but it was unspecified"
              .formatted(id));
      return;
    }
    if (findEntityKey(entityType, id).isEmpty()) {
      errors.add(
          "Expected to delete %s '%s', but it does not exist".formatted(describe(entityType), id));
    } else if (entityType == PolicyUpdateEntityType.ROLE && DefaultRole.ids().contains(id)) {
      errors.add(
          "Expected to delete role '%s', but it is a default role and cannot be deleted"
              .formatted(id));
    }
  }

  // ------------------------------------------------------------------------------------------
  // commands
  // ------------------------------------------------------------------------------------------

  private IdentityCommand tenantUpsert(final TenantRecordValue tenant, final long version) {
    final var record =
        new TenantRecord()
            .setTenantId(tenant.getTenantId())
            .setName(tenant.getName())
            .setDescription(tenant.getDescription())
            .setPolicyVersion(version);
    final var existing = state.getTenantState().getTenantById(tenant.getTenantId());
    if (existing.isPresent()) {
      record.setTenantKey(existing.get().getTenantKey());
      return new IdentityCommand(NO_KEY, TenantIntent.UPDATE, record);
    }
    return new IdentityCommand(NO_KEY, TenantIntent.CREATE, record);
  }

  private IdentityCommand roleUpsert(final RoleRecordValue role, final long version) {
    final var record =
        new RoleRecord()
            .setRoleId(role.getRoleId())
            .setName(role.getName())
            .setDescription(role.getDescription())
            .setPolicyVersion(version);
    final var existing = state.getRoleState().getRole(role.getRoleId());
    if (existing.isPresent()) {
      record.setRoleKey(existing.get().getRoleKey());
      return new IdentityCommand(NO_KEY, RoleIntent.UPDATE, record);
    }
    return new IdentityCommand(NO_KEY, RoleIntent.CREATE, record);
  }

  private IdentityCommand groupUpsert(final GroupRecordValue group, final long version) {
    final var record =
        new GroupRecord()
            .setGroupId(group.getGroupId())
            .setName(group.getName())
            .setDescription(group.getDescription())
            .setPolicyVersion(version);
    final var existing = state.getGroupState().get(group.getGroupId());
    if (existing.isPresent()) {
      record.setGroupKey(existing.get().getGroupKey());
      return new IdentityCommand(NO_KEY, GroupIntent.UPDATE, record);
    }
    return new IdentityCommand(NO_KEY, GroupIntent.CREATE, record);
  }

  private IdentityCommand mappingRuleUpsert(final MappingRuleRecordValue rule, final long version) {
    final var record =
        new MappingRuleRecord()
            .setMappingRuleId(rule.getMappingRuleId())
            .setName(rule.getName())
            .setClaimName(rule.getClaimName())
            .setClaimValue(rule.getClaimValue())
            .setPolicyVersion(version);
    final var existing = state.getMappingRuleState().get(rule.getMappingRuleId());
    if (existing.isPresent()) {
      record.setMappingRuleKey(existing.get().getMappingRuleKey());
      return new IdentityCommand(NO_KEY, MappingRuleIntent.UPDATE, record);
    }
    return new IdentityCommand(NO_KEY, MappingRuleIntent.CREATE, record);
  }

  /** Returns nothing if the member is already assigned, so an upsert can be applied again. */
  private Optional<IdentityCommand> membershipAdd(
      final PolicyMembershipEntryValue membership, final long version) {
    if (isAssigned(membership)) {
      return Optional.empty();
    }
    return Optional.of(
        membershipCommand(
            membership,
            RoleIntent.ADD_ENTITY,
            GroupIntent.ADD_ENTITY,
            TenantIntent.ADD_ENTITY,
            version));
  }

  private IdentityCommand membershipRemove(
      final PolicyMembershipEntryValue membership, final long version) {
    return membershipCommand(
        membership,
        RoleIntent.REMOVE_ENTITY,
        GroupIntent.REMOVE_ENTITY,
        TenantIntent.REMOVE_ENTITY,
        version);
  }

  /**
   * The target may be upserted in the same chunk and then has no key yet; the membership processors
   * find the target by its ID.
   */
  private IdentityCommand membershipCommand(
      final PolicyMembershipEntryValue membership,
      final RoleIntent roleIntent,
      final GroupIntent groupIntent,
      final TenantIntent tenantIntent,
      final long version) {
    final var targetId = membership.getTargetId();
    final long targetKey = findEntityKey(membership.getTargetType(), targetId).orElse(NO_KEY);
    return switch (membership.getTargetType()) {
      case ROLE ->
          new IdentityCommand(
              NO_KEY,
              roleIntent,
              new RoleRecord()
                  .setRoleKey(targetKey)
                  .setRoleId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId())
                  .setPolicyVersion(version));
      case GROUP ->
          new IdentityCommand(
              NO_KEY,
              groupIntent,
              new GroupRecord()
                  .setGroupKey(targetKey)
                  .setGroupId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId())
                  .setPolicyVersion(version));
      case TENANT ->
          new IdentityCommand(
              NO_KEY,
              tenantIntent,
              new TenantRecord()
                  .setTenantKey(targetKey)
                  .setTenantId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId())
                  .setPolicyVersion(version));
      default ->
          throw new IllegalArgumentException(
              "Expected a membership in a role, group or tenant, but got %s"
                  .formatted(membership.getTargetType()));
    };
  }

  /** Creates the authorization, or reconciles the one with the same owner and resource. */
  private IdentityCommand authorizationUpsert(
      final AuthorizationRecordValue authorization, final long version) {
    final var record =
        authorizationRecord(authorization)
            .setPermissionTypes(new HashSet<>(authorization.getPermissionTypes()))
            .setPolicyVersion(version);
    final var existingKey = findAuthorizationKey(authorization);
    if (existingKey.isPresent()) {
      record.setAuthorizationKey(existingKey.getAsLong());
      return new IdentityCommand(NO_KEY, AuthorizationIntent.UPDATE, record);
    }
    return new IdentityCommand(NO_KEY, AuthorizationIntent.CREATE, record);
  }

  private IdentityCommand authorizationDelete(
      final AuthorizationRecordValue authorization, final long version) {
    final var record =
        authorizationRecord(authorization)
            .setAuthorizationKey(findAuthorizationKey(authorization).orElse(NO_KEY))
            .setPolicyVersion(version);
    return new IdentityCommand(NO_KEY, AuthorizationIntent.DELETE, record);
  }

  private IdentityCommand entityDelete(final PolicyDeleteEntryValue delete, final long version) {
    final var id = delete.getId();
    final long key = findEntityKey(delete.getEntityType(), id).orElse(NO_KEY);
    return switch (delete.getEntityType()) {
      case TENANT ->
          new IdentityCommand(
              NO_KEY,
              TenantIntent.DELETE,
              new TenantRecord().setTenantKey(key).setTenantId(id).setPolicyVersion(version));
      case ROLE ->
          new IdentityCommand(
              NO_KEY,
              RoleIntent.DELETE,
              new RoleRecord().setRoleKey(key).setRoleId(id).setPolicyVersion(version));
      case GROUP ->
          new IdentityCommand(
              NO_KEY,
              GroupIntent.DELETE,
              new GroupRecord().setGroupKey(key).setGroupId(id).setPolicyVersion(version));
      case MAPPING_RULE ->
          new IdentityCommand(
              NO_KEY,
              MappingRuleIntent.DELETE,
              new MappingRuleRecord()
                  .setMappingRuleKey(key)
                  .setMappingRuleId(id)
                  .setPolicyVersion(version));
      default ->
          throw new IllegalArgumentException(
              "Expected a delete of a tenant, role, group or mapping rule, but got %s"
                  .formatted(delete.getEntityType()));
    };
  }

  // ------------------------------------------------------------------------------------------
  // state lookups
  // ------------------------------------------------------------------------------------------

  private OptionalLong findEntityKey(final PolicyUpdateEntityType type, final String id) {
    return switch (type) {
      case TENANT -> toKey(state.getTenantState().getTenantById(id).map(t -> t.getTenantKey()));
      case ROLE -> toKey(state.getRoleState().getRole(id).map(r -> r.getRoleKey()));
      case GROUP -> toKey(state.getGroupState().get(id).map(g -> g.getGroupKey()));
      case MAPPING_RULE ->
          toKey(state.getMappingRuleState().get(id).map(m -> m.getMappingRuleKey()));
      default -> OptionalLong.empty();
    };
  }

  private static OptionalLong toKey(final Optional<Long> key) {
    return key.map(OptionalLong::of).orElseGet(OptionalLong::empty);
  }

  private boolean roleExistsInState(final String roleId) {
    return state.getRoleState().getRole(roleId).isPresent();
  }

  private boolean targetExists(
      final PolicyUpdateEntityType targetType, final String targetId, final UpsertedIds upserted) {
    return switch (targetType) {
      case ROLE, GROUP, TENANT ->
          upserted.contains(targetType, targetId)
              || findEntityKey(targetType, targetId).isPresent();
      default -> false;
    };
  }

  /** Mirrors the member checks of the role, group and tenant add-entity processors. */
  private boolean memberExists(
      final PolicyUpdateEntityType targetType,
      final EntityType memberType,
      final String memberId,
      final UpsertedIds upserted) {
    final boolean allowed =
        switch (targetType) {
          case ROLE ->
              Set.of(EntityType.USER, EntityType.CLIENT, EntityType.GROUP, EntityType.MAPPING_RULE)
                  .contains(memberType);
          case GROUP ->
              Set.of(EntityType.USER, EntityType.CLIENT, EntityType.MAPPING_RULE)
                  .contains(memberType);
          case TENANT ->
              Set.of(
                      EntityType.USER,
                      EntityType.CLIENT,
                      EntityType.GROUP,
                      EntityType.MAPPING_RULE,
                      EntityType.ROLE)
                  .contains(memberType);
          default -> false;
        };
    return allowed && entityExists(memberType, memberId, upserted);
  }

  private boolean entityExists(final EntityType type, final String id, final UpsertedIds upserted) {
    return switch (type) {
      case USER -> !localUsersEnabled() || state.getUserState().getUser(id).isPresent();
      case CLIENT -> true;
      case GROUP ->
          !localGroupsEnabled()
              || upserted.contains(PolicyUpdateEntityType.GROUP, id)
              || state.getGroupState().get(id).isPresent();
      case MAPPING_RULE ->
          upserted.contains(PolicyUpdateEntityType.MAPPING_RULE, id)
              || state.getMappingRuleState().get(id).isPresent();
      case ROLE ->
          upserted.contains(PolicyUpdateEntityType.ROLE, id)
              || state.getRoleState().getRole(id).isPresent();
      default -> false;
    };
  }

  /** Mirrors the owner check of the authorization processors. */
  private boolean ownerExists(
      final AuthorizationOwnerType ownerType, final String ownerId, final UpsertedIds upserted) {
    return switch (ownerType) {
      case USER -> entityExists(EntityType.USER, ownerId, upserted);
      case GROUP -> entityExists(EntityType.GROUP, ownerId, upserted);
      case ROLE -> entityExists(EntityType.ROLE, ownerId, upserted);
      case MAPPING_RULE -> entityExists(EntityType.MAPPING_RULE, ownerId, upserted);
      default -> true;
    };
  }

  /** Mirrors the resource check of the authorization processors. */
  private boolean resourceExists(
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final UpsertedIds upserted) {
    if (WILDCARD_RESOURCE_ID.equals(resourceId)) {
      return true;
    }
    return switch (resourceType) {
      case USER -> entityExists(EntityType.USER, resourceId, upserted);
      case GROUP -> entityExists(EntityType.GROUP, resourceId, upserted);
      case ROLE -> entityExists(EntityType.ROLE, resourceId, upserted);
      case MAPPING_RULE -> entityExists(EntityType.MAPPING_RULE, resourceId, upserted);
      default -> true;
    };
  }

  private boolean isAssigned(final PolicyMembershipEntryValue membership) {
    final @Nullable RelationType relationType =
        switch (membership.getTargetType()) {
          case ROLE -> RelationType.ROLE;
          case GROUP -> RelationType.GROUP;
          case TENANT -> RelationType.TENANT;
          default -> null;
        };
    return relationType != null
        && state
            .getMembershipState()
            .hasRelation(
                membership.getMemberType(),
                membership.getMemberId(),
                relationType,
                membership.getTargetId());
  }

  /**
   * Finds the stored authorization with the same owner and resource. Each candidate is compared
   * right away, because a state read reuses the buffer of the previous one.
   */
  private OptionalLong findAuthorizationKey(final AuthorizationRecordValue authorization) {
    final var authorizationState = state.getAuthorizationState();
    for (final long key :
        authorizationState.getAuthorizationKeysForOwner(
            authorization.getOwnerType(), authorization.getOwnerId())) {
      final var stored = authorizationState.get(key);
      if (stored.isPresent()
          && stored.get().getResourceType() == authorization.getResourceType()
          && stored.get().getResourceMatcher() == authorization.getResourceMatcher()
          && stored.get().getResourceId().equals(authorization.getResourceId())
          && stored
              .get()
              .getResourcePropertyName()
              .equals(authorization.getResourcePropertyName())) {
        return OptionalLong.of(key);
      }
    }
    return OptionalLong.empty();
  }

  private boolean localUsersEnabled() {
    return securityConfig.getAuthentication().isCamundaUsersEnabled();
  }

  private boolean localGroupsEnabled() {
    return securityConfig.getAuthentication().isCamundaGroupsEnabled();
  }

  private static AuthorizationRecord authorizationRecord(
      final AuthorizationRecordValue authorization) {
    return new AuthorizationRecord()
        .setOwnerType(authorization.getOwnerType())
        .setOwnerId(authorization.getOwnerId())
        .setResourceType(authorization.getResourceType())
        .setResourceMatcher(authorization.getResourceMatcher())
        .setResourceId(authorization.getResourceId())
        .setResourcePropertyName(authorization.getResourcePropertyName());
  }

  private static String describe(final AuthorizationRecordValue authorization) {
    return "authorization of %s '%s' on %s '%s'"
        .formatted(
            describe(authorization.getOwnerType().name()),
            authorization.getOwnerId(),
            describe(authorization.getResourceType().name()),
            authorization.getResourcePropertyName().isEmpty()
                ? authorization.getResourceId()
                : authorization.getResourcePropertyName());
  }

  private static String describe(final Enum<?> type) {
    return describe(type.name());
  }

  private static String describe(final String typeName) {
    return typeName.toLowerCase().replace('_', ' ');
  }

  /** A command that applies one entry of the chunk, ready for the command writer. */
  public record IdentityCommand(long key, Intent intent, UnifiedRecordValue value) {}

  /** The IDs of the entities that the chunk upserts, which count as existing for later entries. */
  private record UpsertedIds(
      Set<String> tenants, Set<String> roles, Set<String> groups, Set<String> mappingRules) {

    static UpsertedIds of(final PolicyUpdateRecordValue chunk) {
      return new UpsertedIds(
          chunk.getTenants().stream()
              .map(TenantRecordValue::getTenantId)
              .collect(Collectors.toSet()),
          chunk.getRoles().stream().map(RoleRecordValue::getRoleId).collect(Collectors.toSet()),
          chunk.getGroups().stream().map(GroupRecordValue::getGroupId).collect(Collectors.toSet()),
          chunk.getMappingRules().stream()
              .map(MappingRuleRecordValue::getMappingRuleId)
              .collect(Collectors.toSet()));
    }

    boolean contains(final PolicyUpdateEntityType type, final String id) {
      return switch (type) {
        case TENANT -> tenants.contains(id);
        case ROLE -> roles.contains(id);
        case GROUP -> groups.contains(id);
        case MAPPING_RULE -> mappingRules.contains(id);
        default -> false;
      };
    }
  }
}
