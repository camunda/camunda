/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.policy;

import io.camunda.security.api.model.policy.PolicyAuthorization;
import io.camunda.security.api.model.policy.PolicyAuthorizationKey;
import io.camunda.security.api.model.policy.PolicyChangeSet;
import io.camunda.security.api.model.policy.PolicyDeletions;
import io.camunda.security.api.model.policy.PolicyMembership;
import io.camunda.security.api.model.policy.PolicyUpserts;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
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
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateKind;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Splits a {@link PolicyChangeSet} into the chunks of a policy update. Each chunk is a sequence of
 * whole entities that fits the chunk budget, and the chunks are returned in apply order.
 *
 * <p>Chunks never mix phases. The phases follow the apply order of the change set contract: upserts
 * of tenants, roles, groups and mapping rules; membership upserts; authorization upserts;
 * authorization deletes; membership deletes; deletes of mapping rules, groups, roles and tenants.
 * Because a chunk is applied as a whole, a chunk index is also the apply order. Within a phase the
 * entity order does not matter.
 *
 * <p>The size of an entity is its exact msgpack length plus a fixed per-entry margin, so the
 * estimate stays above the size the entity takes in the chunk. An entity larger than the budget
 * gets a chunk of its own. The budget is a conservative estimate of the applied event size; the
 * real value is measured later.
 */
public final class PolicyChangeSetSplitter {

  /** Conservative default; to be replaced by a value measured from applied event sizes. */
  public static final int DEFAULT_CHUNK_BUDGET_BYTES = 1024 * 1024;

  /** Margin per entry for msgpack array framing, so the estimate stays above the real size. */
  static final int ENTRY_OVERHEAD_BYTES = 64;

  private final int chunkBudgetBytes;

  public PolicyChangeSetSplitter(final int chunkBudgetBytes) {
    if (chunkBudgetBytes <= 0) {
      throw new IllegalArgumentException("chunkBudgetBytes must be positive: " + chunkBudgetBytes);
    }
    this.chunkBudgetBytes = chunkBudgetBytes;
  }

  public List<PolicyUpdateRecord> split(final String envelopeId, final PolicyChangeSet changeSet) {
    final var version = changeSet.policyVersion();
    final var upserts = changeSet.upserts();
    final var deletions = changeSet.deletions();

    final List<List<Entry>> phases =
        List.of(
            upsertEntityPhase(upserts, version),
            membershipUpsertPhase(upserts.memberships()),
            authorizationUpsertPhase(upserts.authorizations(), version),
            authorizationDeletePhase(deletions.authorizations()),
            membershipDeletePhase(deletions.memberships()),
            entityDeletePhase(deletions));

    final var builders = new ArrayList<ChunkBuilder>();
    for (final var phase : phases) {
      var current = new ChunkBuilder();
      for (final var entry : phase) {
        if (!current.isEmpty() && current.bytes + entry.size() > chunkBudgetBytes) {
          builders.add(current);
          current = new ChunkBuilder();
        }
        current.add(entry);
      }
      if (!current.isEmpty()) {
        builders.add(current);
      }
    }
    // A change set without entities still needs one chunk, so the version switch happens.
    if (builders.isEmpty()) {
      builders.add(new ChunkBuilder());
    }

    final var kind = byName(changeSet.kind(), PolicyUpdateKind.class);
    final var chunks = new ArrayList<PolicyUpdateRecord>();
    for (int index = 0; index < builders.size(); index++) {
      chunks.add(builders.get(index).toRecord(envelopeId, kind, version, index, builders.size()));
    }
    return chunks;
  }

  private static List<Entry> upsertEntityPhase(final PolicyUpserts upserts, final long version) {
    final var entries = new ArrayList<Entry>();
    for (final var tenant : upserts.tenants()) {
      final var value =
          new TenantRecord()
              .setTenantId(tenant.tenantId())
              .setName(nullToEmpty(tenant.name()))
              .setDescription(nullToEmpty(tenant.description()))
              .setPolicyVersion(version);
      entries.add(new Entry(sizeOf(value), builder -> builder.tenants.add(value)));
    }
    for (final var role : upserts.roles()) {
      final var value =
          new RoleRecord()
              .setRoleId(role.roleId())
              .setName(nullToEmpty(role.name()))
              .setDescription(nullToEmpty(role.description()))
              .setPolicyVersion(version);
      entries.add(new Entry(sizeOf(value), builder -> builder.roles.add(value)));
    }
    for (final var group : upserts.groups()) {
      final var value =
          new GroupRecord()
              .setGroupId(group.groupId())
              .setName(nullToEmpty(group.name()))
              .setDescription(nullToEmpty(group.description()))
              .setPolicyVersion(version);
      entries.add(new Entry(sizeOf(value), builder -> builder.groups.add(value)));
    }
    for (final var rule : upserts.mappingRules()) {
      final var value =
          new MappingRuleRecord()
              .setMappingRuleId(rule.mappingRuleId())
              .setName(nullToEmpty(rule.name()))
              .setClaimName(nullToEmpty(rule.claimName()))
              .setClaimValue(nullToEmpty(rule.claimValue()))
              .setPolicyVersion(version);
      entries.add(new Entry(sizeOf(value), builder -> builder.mappingRules.add(value)));
    }
    return entries;
  }

  private static List<Entry> membershipUpsertPhase(final List<PolicyMembership> memberships) {
    return memberships.stream()
        .map(PolicyChangeSetSplitter::membershipEntry)
        .map(value -> new Entry(sizeOf(value), builder -> builder.memberships.add(value)))
        .toList();
  }

  private static List<Entry> authorizationUpsertPhase(
      final List<PolicyAuthorization> authorizations, final long version) {
    return authorizations.stream()
        .map(
            authorization -> {
              final var value =
                  authorizationKey(authorization.key())
                      .setPermissionTypes(
                          authorization.permissions().stream()
                              .map(permission -> byName(permission, PermissionType.class))
                              .collect(Collectors.toSet()))
                      .setPolicyVersion(version);
              return new Entry(sizeOf(value), builder -> builder.authorizations.add(value));
            })
        .toList();
  }

  private static List<Entry> authorizationDeletePhase(final List<PolicyAuthorizationKey> keys) {
    return keys.stream()
        .map(PolicyChangeSetSplitter::authorizationKey)
        .map(value -> new Entry(sizeOf(value), builder -> builder.authorizationDeletes.add(value)))
        .toList();
  }

  private static List<Entry> membershipDeletePhase(final List<PolicyMembership> memberships) {
    return memberships.stream()
        .map(PolicyChangeSetSplitter::membershipEntry)
        .map(value -> new Entry(sizeOf(value), builder -> builder.membershipDeletes.add(value)))
        .toList();
  }

  private static List<Entry> entityDeletePhase(final PolicyDeletions deletions) {
    final var entries = new ArrayList<Entry>();
    deletions
        .mappingRuleIds()
        .forEach(id -> addDelete(entries, PolicyUpdateEntityType.MAPPING_RULE, id));
    deletions.groupIds().forEach(id -> addDelete(entries, PolicyUpdateEntityType.GROUP, id));
    deletions.roleIds().forEach(id -> addDelete(entries, PolicyUpdateEntityType.ROLE, id));
    deletions.tenantIds().forEach(id -> addDelete(entries, PolicyUpdateEntityType.TENANT, id));
    return entries;
  }

  private static void addDelete(
      final List<Entry> entries, final PolicyUpdateEntityType type, final String id) {
    final var value = new PolicyDeleteEntry().setEntityType(type).setId(id);
    entries.add(new Entry(sizeOf(value), builder -> builder.deletes.add(value)));
  }

  private static PolicyMembershipEntry membershipEntry(final PolicyMembership membership) {
    return new PolicyMembershipEntry()
        .setTargetType(byName(membership.targetType(), PolicyUpdateEntityType.class))
        .setTargetId(membership.targetId())
        .setMemberType(byName(membership.memberType(), EntityType.class))
        .setMemberId(membership.memberId());
  }

  private static AuthorizationRecord authorizationKey(final PolicyAuthorizationKey key) {
    return new AuthorizationRecord()
        .setOwnerType(byName(key.ownerType(), AuthorizationOwnerType.class))
        .setOwnerId(key.ownerId())
        .setResourceType(byName(key.resourceType(), AuthorizationResourceType.class))
        .setResourceMatcher(byName(key.matcher(), AuthorizationResourceMatcher.class))
        .setResourceId(key.resourceId())
        .setResourcePropertyName(key.resourcePropertyName());
  }

  /** Maps a security-library enum to the protocol enum with the same constant name. */
  private static <T extends Enum<T>> T byName(final Enum<?> source, final Class<T> target) {
    return Enum.valueOf(target, source.name());
  }

  private static int sizeOf(final BaseValue value) {
    return value.getEncodedLength() + ENTRY_OVERHEAD_BYTES;
  }

  private static String nullToEmpty(final String value) {
    return Objects.toString(value, "");
  }

  /** One entity to add to a chunk, with its estimated size and how to add it. */
  private record Entry(int size, Consumer<ChunkBuilder> adder) {}

  private static final class ChunkBuilder {
    private final List<TenantRecordValue> tenants = new ArrayList<>();
    private final List<RoleRecordValue> roles = new ArrayList<>();
    private final List<GroupRecordValue> groups = new ArrayList<>();
    private final List<MappingRuleRecordValue> mappingRules = new ArrayList<>();
    private final List<PolicyMembershipEntryValue> memberships = new ArrayList<>();
    private final List<AuthorizationRecordValue> authorizations = new ArrayList<>();
    private final List<PolicyDeleteEntryValue> deletes = new ArrayList<>();
    private final List<PolicyMembershipEntryValue> membershipDeletes = new ArrayList<>();
    private final List<AuthorizationRecordValue> authorizationDeletes = new ArrayList<>();
    private int bytes;

    boolean isEmpty() {
      return bytes == 0;
    }

    void add(final Entry entry) {
      entry.adder().accept(this);
      bytes += entry.size();
    }

    PolicyUpdateRecord toRecord(
        final String envelopeId,
        final PolicyUpdateKind kind,
        final long version,
        final int index,
        final int count) {
      return new PolicyUpdateRecord()
          .setEnvelopeId(envelopeId)
          .setKind(kind)
          .setPolicyVersion(version)
          .setChunkIndex(index)
          .setChunkCount(count)
          .setTenants(tenants)
          .setRoles(roles)
          .setGroups(groups)
          .setMappingRules(mappingRules)
          .setMemberships(memberships)
          .setAuthorizations(authorizations)
          .setDeletes(deletes)
          .setMembershipDeletes(membershipDeletes)
          .setAuthorizationDeletes(authorizationDeletes);
    }
  }
}
