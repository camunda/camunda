/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.protocol.model.PolicyAuthorization;
import io.camunda.fanout.protocol.model.PolicyAuthorizationKey;
import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.fanout.protocol.model.PolicyGroup;
import io.camunda.fanout.protocol.model.PolicyMappingRule;
import io.camunda.fanout.protocol.model.PolicyMembership;
import io.camunda.fanout.protocol.model.PolicyRole;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSetEntries.Entry;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSetEntries.Section;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyAuthorizationDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps a Hub policy change set to the sections of a policy update record.
 *
 * <p>The record carries the whole change set; {@link PolicyChangeSetSplitter} cuts it into chunks.
 * Its deletions are in the apply order of {@code PolicyUpdateCommandMapper}: mapping rules, groups,
 * roles and then tenants, since a later section may still refer to an entity an earlier one
 * deletes.
 *
 * <p>The change set names entity, owner, resource, matcher and permission types as text, which the
 * library does not check against the broker's types. The mapper rejects a change set with a text
 * that names no such type, so no chunk of it is ever written. Whether a known type fits its place,
 * for example a mapping rule as a membership target, is checked by the engine.
 */
@NullMarked
final class PolicyChangeSetMapper {

  private PolicyChangeSetMapper() {}

  /**
   * Maps the change set to the identifiers of its policy update and its entries in apply order.
   *
   * @return the mapped change set, or why the change set cannot be applied
   */
  static Either<String, PolicyChangeSetEntries> map(
      final PolicyChangeSetV1 changeSet, final long seqNo) {
    if (changeSet.kind() != PolicyChangeSetKind.DELTA) {
      return Either.left(
          "Expected policy change set of policy '%s' to be a DELTA, but it is a %s, which is not supported yet"
              .formatted(changeSet.policyId(), changeSet.kind()));
    }

    try {
      return Either.right(new PolicyChangeSetEntries(header(changeSet, seqNo), entries(changeSet)));
    } catch (final UnknownTypeException e) {
      return Either.left(
          "Expected policy change set of policy '%s' to name only known types, but %s"
              .formatted(changeSet.policyId(), e.getMessage()));
    }
  }

  /**
   * The ID of the policy update that applies the change set. It is the same for the same change
   * set, so a change set handed over again belongs to the same update.
   */
  static String updateId(final PolicyChangeSetV1 changeSet, final long seqNo) {
    return "%s-%d-%d-%d"
        .formatted(
            changeSet.policyId(), changeSet.basePolicyVersion(), changeSet.policyVersion(), seqNo);
  }

  private static PolicyUpdateRecord header(final PolicyChangeSetV1 changeSet, final long seqNo) {
    return new PolicyUpdateRecord()
        .setPolicyId(changeSet.policyId())
        .setPolicyVersion(changeSet.policyVersion())
        .setBasePolicyVersion(changeSet.basePolicyVersion())
        .setSeqNo(seqNo)
        .setUpdateId(updateId(changeSet, seqNo));
  }

  private static List<Entry> entries(final PolicyChangeSetV1 changeSet) {
    final var upserts = changeSet.upserts();
    final var deletions = changeSet.deletions();
    final var entries = new ArrayList<Entry>();
    upserts.tenants().forEach(tenant -> entries.add(new Entry(Section.TENANTS, tenant(tenant))));
    upserts.roles().forEach(role -> entries.add(new Entry(Section.ROLES, role(role))));
    upserts.groups().forEach(group -> entries.add(new Entry(Section.GROUPS, group(group))));
    upserts
        .mappingRules()
        .forEach(rule -> entries.add(new Entry(Section.MAPPING_RULES, mappingRule(rule))));
    upserts
        .memberships()
        .forEach(membership -> entries.add(new Entry(Section.MEMBERSHIPS, membership(membership))));
    upserts
        .authorizations()
        .forEach(
            authorization ->
                entries.add(new Entry(Section.AUTHORIZATIONS, authorization(authorization))));
    deletions
        .authorizations()
        .forEach(
            key -> entries.add(new Entry(Section.AUTHORIZATION_DELETES, authorizationDelete(key))));
    deletions
        .memberships()
        .forEach(
            membership ->
                entries.add(new Entry(Section.MEMBERSHIP_DELETES, membership(membership))));
    entityDeletes(
            deletions.mappingRuleIds(),
            deletions.groupIds(),
            deletions.roleIds(),
            deletions.tenantIds())
        .forEach(delete -> entries.add(new Entry(Section.DELETES, delete)));
    return entries;
  }

  private static TenantRecordValue tenant(final PolicyTenant tenant) {
    return new TenantRecord()
        .setTenantId(tenant.tenantId())
        .setName(tenant.name())
        .setDescription(orEmpty(tenant.description()));
  }

  private static RoleRecordValue role(final PolicyRole role) {
    return new RoleRecord()
        .setRoleId(role.roleId())
        .setName(role.name())
        .setDescription(orEmpty(role.description()));
  }

  private static GroupRecordValue group(final PolicyGroup group) {
    return new GroupRecord()
        .setGroupId(group.groupId())
        .setName(group.name())
        .setDescription(orEmpty(group.description()));
  }

  private static MappingRuleRecordValue mappingRule(final PolicyMappingRule rule) {
    return new MappingRuleRecord()
        .setMappingRuleId(rule.mappingRuleId())
        .setName(rule.name())
        .setClaimName(rule.claimName())
        .setClaimValue(rule.claimValue());
  }

  private static PolicyMembershipEntryValue membership(final PolicyMembership membership) {
    return new PolicyMembershipEntry()
        .setTargetType(
            parse(PolicyUpdateEntityType.class, "membership target type", membership.targetType()))
        .setTargetId(membership.targetId())
        .setMemberType(parse(EntityType.class, "membership member type", membership.memberType()))
        .setMemberId(membership.memberId());
  }

  private static AuthorizationRecordValue authorization(final PolicyAuthorization authorization) {
    final var key = authorization.key();
    final var record =
        new AuthorizationRecord()
            .setOwnerType(parse(AuthorizationOwnerType.class, "owner type", key.ownerType()))
            .setOwnerId(key.ownerId())
            .setResourceType(
                parse(AuthorizationResourceType.class, "resource type", key.resourceType()))
            .setResourceMatcher(
                parse(AuthorizationResourceMatcher.class, "resource matcher", key.matcher()))
            .setPermissionTypes(permissions(authorization.permissions()));
    if (key.resourceId() != null) {
      record.setResourceId(key.resourceId());
    }
    if (key.resourcePropertyName() != null) {
      record.setResourcePropertyName(key.resourcePropertyName());
    }
    return record;
  }

  private static PolicyAuthorizationDeleteEntryValue authorizationDelete(
      final PolicyAuthorizationKey key) {
    final var entry =
        new PolicyAuthorizationDeleteEntry()
            .setOwnerType(parse(AuthorizationOwnerType.class, "owner type", key.ownerType()))
            .setOwnerId(key.ownerId())
            .setResourceType(
                parse(AuthorizationResourceType.class, "resource type", key.resourceType()))
            .setResourceMatcher(
                parse(AuthorizationResourceMatcher.class, "resource matcher", key.matcher()));
    if (key.resourceId() != null) {
      entry.setResourceId(key.resourceId());
    }
    if (key.resourcePropertyName() != null) {
      entry.setResourcePropertyName(key.resourcePropertyName());
    }
    return entry;
  }

  private static Set<PermissionType> permissions(final List<String> permissions) {
    final var types = EnumSet.noneOf(PermissionType.class);
    permissions.forEach(
        permission -> types.add(parse(PermissionType.class, "permission type", permission)));
    return types;
  }

  /** The deletes of mapping rules, groups, roles and tenants, in this order. */
  private static List<PolicyDeleteEntryValue> entityDeletes(
      final List<String> mappingRuleIds,
      final List<String> groupIds,
      final List<String> roleIds,
      final List<String> tenantIds) {
    final var deletes = new ArrayList<PolicyDeleteEntryValue>();
    mappingRuleIds.forEach(id -> deletes.add(delete(PolicyUpdateEntityType.MAPPING_RULE, id)));
    groupIds.forEach(id -> deletes.add(delete(PolicyUpdateEntityType.GROUP, id)));
    roleIds.forEach(id -> deletes.add(delete(PolicyUpdateEntityType.ROLE, id)));
    tenantIds.forEach(id -> deletes.add(delete(PolicyUpdateEntityType.TENANT, id)));
    return deletes;
  }

  private static PolicyDeleteEntryValue delete(final PolicyUpdateEntityType type, final String id) {
    return new PolicyDeleteEntry().setEntityType(type).setId(id);
  }

  private static String orEmpty(final @Nullable String text) {
    return Objects.requireNonNullElse(text, "");
  }

  /** The constant of the given type with the given name; never the type's {@code UNSPECIFIED}. */
  private static <E extends Enum<E>> E parse(
      final Class<E> type, final String field, final String name) {
    for (final var constant : type.getEnumConstants()) {
      if (constant.name().equals(name) && !"UNSPECIFIED".equals(name)) {
        return constant;
      }
    }
    throw new UnknownTypeException(
        "the %s '%s' is none of %s"
            .formatted(
                field,
                name,
                Arrays.stream(type.getEnumConstants())
                    .map(Enum::name)
                    .filter(constant -> !"UNSPECIFIED".equals(constant))
                    .collect(Collectors.joining(", "))));
  }

  private static final class UnknownTypeException extends RuntimeException {
    private UnknownTypeException(final String message) {
      super(message, null, false, false);
    }
  }
}
