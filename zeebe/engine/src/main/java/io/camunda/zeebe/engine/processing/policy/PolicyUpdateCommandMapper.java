/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

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
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.NullMarked;

/**
 * Maps a chunk of a policy update to the identity commands that apply it.
 *
 * <p>The commands follow the apply order of the change set: upserts of tenants, roles, groups and
 * mapping rules; membership adds; authorization upserts; authorization deletes; membership removes;
 * deletes of mapping rules, groups, roles and tenants. They are processed in that order after the
 * command that wrote them.
 */
@NullMarked
public final class PolicyUpdateCommandMapper {

  /** The key of an entity that is not stored yet, and the record key of every follow-up command. */
  static final long NO_KEY = -1L;

  private final ProcessingState state;

  public PolicyUpdateCommandMapper(final ProcessingState state) {
    this.state = state;
  }

  /**
   * Maps the chunk to identity commands, in apply order.
   *
   * @return the commands, or the entries that cannot be mapped because they don't name what kind of
   *     entity they refer to
   */
  public Either<List<String>, List<IdentityCommand>> toCommands(
      final PolicyUpdateRecordValue chunk) {
    final var unmappable = findUnmappableEntries(chunk);
    if (!unmappable.isEmpty()) {
      return Either.left(unmappable);
    }
    final var commands = new ArrayList<IdentityCommand>();
    chunk.getTenants().forEach(tenant -> commands.add(tenantUpsert(tenant)));
    chunk.getRoles().forEach(role -> commands.add(roleUpsert(role)));
    chunk.getGroups().forEach(group -> commands.add(groupUpsert(group)));
    chunk.getMappingRules().forEach(rule -> commands.add(mappingRuleUpsert(rule)));
    chunk
        .getMemberships()
        .forEach(
            membership ->
                commands.add(
                    membershipCommand(
                        membership,
                        RoleIntent.ADD_ENTITY,
                        GroupIntent.ADD_ENTITY,
                        TenantIntent.ADD_ENTITY)));
    chunk
        .getAuthorizations()
        .forEach(authorization -> commands.add(authorizationUpsert(authorization)));
    chunk.getAuthorizationDeletes().forEach(delete -> commands.add(authorizationDelete(delete)));
    chunk
        .getMembershipDeletes()
        .forEach(
            membership ->
                commands.add(
                    membershipCommand(
                        membership,
                        RoleIntent.REMOVE_ENTITY,
                        GroupIntent.REMOVE_ENTITY,
                        TenantIntent.REMOVE_ENTITY)));
    chunk.getDeletes().forEach(delete -> commands.add(entityDelete(delete)));
    return Either.right(commands);
  }

  /** The entries of the chunk that name no entity type, so no command can be derived from them. */
  static List<String> findUnmappableEntries(final PolicyUpdateRecordValue chunk) {
    final var errors = new ArrayList<String>();
    for (final var membership : chunk.getMemberships()) {
      if (!isMembershipTarget(membership.getTargetType())) {
        errors.add(unmappableMembership("add", membership));
      }
    }
    for (final var membership : chunk.getMembershipDeletes()) {
      if (!isMembershipTarget(membership.getTargetType())) {
        errors.add(unmappableMembership("remove", membership));
      }
    }
    for (final var delete : chunk.getDeletes()) {
      if (delete.getEntityType() == PolicyUpdateEntityType.UNSPECIFIED) {
        errors.add(
            "Expected the delete of '%s' to name a tenant, role, group or mapping rule, but the entity type was not specified"
                .formatted(delete.getId()));
      }
    }
    return errors;
  }

  private static boolean isMembershipTarget(final PolicyUpdateEntityType targetType) {
    return targetType == PolicyUpdateEntityType.ROLE
        || targetType == PolicyUpdateEntityType.GROUP
        || targetType == PolicyUpdateEntityType.TENANT;
  }

  private static String unmappableMembership(
      final String operation, final PolicyMembershipEntryValue membership) {
    return "Expected the membership to %s '%s' to target a role, group or tenant, but the target '%s' has type %s"
        .formatted(
            operation,
            membership.getMemberId(),
            membership.getTargetId(),
            membership.getTargetType());
  }

  private IdentityCommand tenantUpsert(final TenantRecordValue tenant) {
    final var record =
        new TenantRecord()
            .setTenantId(tenant.getTenantId())
            .setName(tenant.getName())
            .setDescription(tenant.getDescription());
    final var existing = state.getTenantState().getTenantById(tenant.getTenantId());
    if (existing.isPresent()) {
      record.setTenantKey(existing.get().getTenantKey());
      return new IdentityCommand(TenantIntent.UPDATE, record);
    }
    return new IdentityCommand(TenantIntent.CREATE, record);
  }

  private IdentityCommand roleUpsert(final RoleRecordValue role) {
    final var record =
        new RoleRecord()
            .setRoleId(role.getRoleId())
            .setName(role.getName())
            .setDescription(role.getDescription());
    final var existing = state.getRoleState().getRole(role.getRoleId());
    if (existing.isPresent()) {
      record.setRoleKey(existing.get().getRoleKey());
      return new IdentityCommand(RoleIntent.UPDATE, record);
    }
    return new IdentityCommand(RoleIntent.CREATE, record);
  }

  private IdentityCommand groupUpsert(final GroupRecordValue group) {
    final var record =
        new GroupRecord()
            .setGroupId(group.getGroupId())
            .setName(group.getName())
            .setDescription(group.getDescription());
    final var existing = state.getGroupState().get(group.getGroupId());
    if (existing.isPresent()) {
      record.setGroupKey(existing.get().getGroupKey());
      return new IdentityCommand(GroupIntent.UPDATE, record);
    }
    return new IdentityCommand(GroupIntent.CREATE, record);
  }

  private IdentityCommand mappingRuleUpsert(final MappingRuleRecordValue rule) {
    final var record =
        new MappingRuleRecord()
            .setMappingRuleId(rule.getMappingRuleId())
            .setName(rule.getName())
            .setClaimName(rule.getClaimName())
            .setClaimValue(rule.getClaimValue());
    final var existing = state.getMappingRuleState().get(rule.getMappingRuleId());
    if (existing.isPresent()) {
      record.setMappingRuleKey(existing.get().getMappingRuleKey());
      return new IdentityCommand(MappingRuleIntent.UPDATE, record);
    }
    return new IdentityCommand(MappingRuleIntent.CREATE, record);
  }

  /**
   * The target may be upserted in the same chunk and then has no key yet; the membership processors
   * find the target by its ID.
   */
  private IdentityCommand membershipCommand(
      final PolicyMembershipEntryValue membership,
      final RoleIntent roleIntent,
      final GroupIntent groupIntent,
      final TenantIntent tenantIntent) {
    final var targetId = membership.getTargetId();
    final long targetKey = findEntityKey(membership.getTargetType(), targetId).orElse(NO_KEY);
    return switch (membership.getTargetType()) {
      case ROLE ->
          new IdentityCommand(
              roleIntent,
              new RoleRecord()
                  .setRoleKey(targetKey)
                  .setRoleId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId()));
      case GROUP ->
          new IdentityCommand(
              groupIntent,
              new GroupRecord()
                  .setGroupKey(targetKey)
                  .setGroupId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId()));
      case TENANT ->
          new IdentityCommand(
              tenantIntent,
              new TenantRecord()
                  .setTenantKey(targetKey)
                  .setTenantId(targetId)
                  .setEntityType(membership.getMemberType())
                  .setEntityId(membership.getMemberId()));
      default ->
          throw new IllegalStateException(
              "Expected a membership in a role, group or tenant, but got %s"
                  .formatted(membership.getTargetType()));
    };
  }

  /** Creates the authorization, or reconciles the one with the same owner and resource. */
  private IdentityCommand authorizationUpsert(final AuthorizationRecordValue authorization) {
    final var record =
        authorizationRecord(authorization)
            .setPermissionTypes(new HashSet<>(authorization.getPermissionTypes()));
    final var existingKey = findAuthorizationKey(authorization);
    if (existingKey.isPresent()) {
      record.setAuthorizationKey(existingKey.getAsLong());
      return new IdentityCommand(AuthorizationIntent.UPDATE, record);
    }
    return new IdentityCommand(AuthorizationIntent.CREATE, record);
  }

  /**
   * Deletes the authorization with the same owner and resource. If there is none, the command
   * carries no authorization key and the authorization processor rejects it.
   */
  private IdentityCommand authorizationDelete(final PolicyAuthorizationDeleteEntryValue delete) {
    final var record =
        new AuthorizationRecord()
            .setOwnerType(delete.getOwnerType())
            .setOwnerId(delete.getOwnerId())
            .setResourceType(delete.getResourceType())
            .setResourceMatcher(delete.getResourceMatcher())
            .setResourceId(delete.getResourceId())
            .setResourcePropertyName(delete.getResourcePropertyName());
    record.setAuthorizationKey(findAuthorizationKey(record).orElse(NO_KEY));
    return new IdentityCommand(AuthorizationIntent.DELETE, record);
  }

  private IdentityCommand entityDelete(final PolicyDeleteEntryValue delete) {
    final var id = delete.getId();
    final long key = findEntityKey(delete.getEntityType(), id).orElse(NO_KEY);
    return switch (delete.getEntityType()) {
      case TENANT ->
          new IdentityCommand(
              TenantIntent.DELETE, new TenantRecord().setTenantKey(key).setTenantId(id));
      case ROLE ->
          new IdentityCommand(RoleIntent.DELETE, new RoleRecord().setRoleKey(key).setRoleId(id));
      case GROUP ->
          new IdentityCommand(
              GroupIntent.DELETE, new GroupRecord().setGroupKey(key).setGroupId(id));
      case MAPPING_RULE ->
          new IdentityCommand(
              MappingRuleIntent.DELETE,
              new MappingRuleRecord().setMappingRuleKey(key).setMappingRuleId(id));
      default ->
          throw new IllegalStateException(
              "Expected a delete of a tenant, role, group or mapping rule, but got %s"
                  .formatted(delete.getEntityType()));
    };
  }

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

  /**
   * Finds the stored authorization with the same owner and resource. Each candidate is compared
   * right away, because a state read reuses the buffer of the previous one.
   *
   * <p>The engine allows several authorizations with the same owner and resource if their
   * permissions are disjoint, but in hub mode there is at most one: the identity data is truncated
   * before a cluster switches to hub mode, and Hub sends at most one authorization per owner and
   * resource, which this mapping reconciles onto. So the first match is the only one.
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

  /**
   * A command that applies one entry of the chunk. It is written without a record key, like the
   * commands of the identity APIs, because the identity processors find entities by their ID.
   */
  public record IdentityCommand(Intent intent, UnifiedRecordValue value) {}
}
