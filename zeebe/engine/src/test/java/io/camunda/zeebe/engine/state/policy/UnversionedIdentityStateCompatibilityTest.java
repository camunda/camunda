/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.AuthorizationKeys;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.Permissions;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.authorization.PersistedRole;
import io.camunda.zeebe.engine.state.group.PersistedGroup;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.tenant.PersistedTenant;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Existing clusters hold identity data in the unversioned layout. This test writes rows exactly as
 * that layout stores them, with the key types and values from before policy versioning, and checks
 * that the versioned states read them as version 0 and keep writing that layout at version 0.
 */
@ExtendWith(ProcessingStateExtension.class)
final class UnversionedIdentityStateCompatibilityTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  @Test
  void shouldReadUnversionedRole() {
    // given
    final var roles = unversionedColumnFamily(ZbColumnFamilies.ROLES, new DbString());
    final var role = new PersistedRole();
    role.from(new RoleRecord().setRoleKey(1L).setRoleId("role-1").setName("Role 1"));
    roles.upsert(string("role-1"), unversionedValue(role));

    // when
    final var read = processingState.getRoleState().getRole("role-1");

    // then
    assertThat(read).hasValueSatisfying(r -> assertThat(r.getName()).isEqualTo("Role 1"));
    assertThat(read.get().isDeleted()).isFalse();
  }

  @Test
  void shouldKeepUnversionedLayoutWhenWritingAtVersionZero() {
    // given
    final var roles = unversionedColumnFamily(ZbColumnFamilies.ROLES, new DbString());
    final var role = new PersistedRole();
    role.from(new RoleRecord().setRoleKey(1L).setRoleId("role-1").setName("Role 1"));
    roles.upsert(string("role-1"), unversionedValue(role));

    // when
    processingState
        .getRoleState()
        .update(new RoleRecord().setRoleKey(1L).setRoleId("role-1").setName("Renamed"));

    // then
    assertThat(roles.count()).describedAs("no second row for the same role").isEqualTo(1);
    final var stored = new PersistedRole();
    final var bytes = roles.get(string("role-1")).getDirectBuffer();
    stored.wrap(bytes, 0, bytes.capacity());
    assertThat(stored.getName()).isEqualTo("Renamed");
  }

  @Test
  void shouldShadowUnversionedRoleWithVersionedUpdate() {
    // given
    final var roles = unversionedColumnFamily(ZbColumnFamilies.ROLES, new DbString());
    final var role = new PersistedRole();
    role.from(new RoleRecord().setRoleKey(1L).setRoleId("role-1").setName("Role 1"));
    roles.upsert(string("role-1"), unversionedValue(role));

    // when
    processingState
        .getRoleState()
        .update(
            new RoleRecord()
                .setRoleKey(1L)
                .setRoleId("role-1")
                .setName("Version 1")
                .setPolicyVersion(1));

    // then
    final var roleState = processingState.getRoleState();
    assertThat(roleState.getRole("role-1", 0).orElseThrow().getName()).isEqualTo("Role 1");
    assertThat(roleState.getRole("role-1", 1).orElseThrow().getName()).isEqualTo("Version 1");
  }

  @Test
  void shouldReadUnversionedTenant() {
    // given
    final var tenants = unversionedColumnFamily(ZbColumnFamilies.TENANTS, new DbString());
    final var tenant =
        new PersistedTenant().setTenantKey(2L).setTenantId("tenant-1").setName("Tenant 1");
    tenants.upsert(string("tenant-1"), unversionedValue(tenant));

    // when
    final var read = processingState.getTenantState().getTenantById("tenant-1");

    // then
    assertThat(read).hasValueSatisfying(t -> assertThat(t.getName()).isEqualTo("Tenant 1"));
  }

  @Test
  void shouldReadUnversionedGroup() {
    // given
    final var groups = unversionedColumnFamily(ZbColumnFamilies.GROUPS, new DbString());
    final var group =
        new PersistedGroup()
            .setGroupKey(3L)
            .setGroupId("group-1")
            .setName("Group 1")
            .setDescription("");
    groups.upsert(string("group-1"), unversionedValue(group));

    // when
    final var read = processingState.getGroupState().get("group-1");

    // then
    assertThat(read).hasValueSatisfying(g -> assertThat(g.getName()).isEqualTo("Group 1"));
    assertThat(processingState.getGroupState().findByIdOrName("Group 1"))
        .extracting(PersistedGroup::getGroupId)
        .containsExactly("group-1");
  }

  @Test
  void shouldReadUnversionedMappingRule() {
    // given
    final var claim = new DbCompositeKey<>(new DbString(), new DbString());
    final var mappingRules =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPING_RULES, transactionContext, claim, new DbBytes());
    final var claimById =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID,
            transactionContext,
            new DbString(),
            new DbForeignKey<>(claim, ZbColumnFamilies.MAPPING_RULES));
    final var mappingRule =
        new PersistedMappingRule()
            .setMappingRuleKey(4L)
            .setMappingRuleId("rule-1")
            .setName("Rule 1")
            .setClaimName("department")
            .setClaimValue("sales");
    claim.first().wrapString("department");
    claim.second().wrapString("sales");
    mappingRules.upsert(claim, unversionedValue(mappingRule));
    claimById.upsert(string("rule-1"), new DbForeignKey<>(claim, ZbColumnFamilies.MAPPING_RULES));

    // when
    final var mappingRuleState = processingState.getMappingRuleState();

    // then
    assertThat(mappingRuleState.get("rule-1"))
        .hasValueSatisfying(r -> assertThat(r.getName()).isEqualTo("Rule 1"));
    assertThat(mappingRuleState.get("department", "sales"))
        .hasValueSatisfying(r -> assertThat(r.getMappingRuleId()).isEqualTo("rule-1"));
    assertThat(mappingRuleState.getAll())
        .extracting(PersistedMappingRule::getMappingRuleId)
        .containsExactly("rule-1");
  }

  @Test
  void shouldReadUnversionedMemberships() {
    // given
    writeUnversionedMembership(EntityType.USER, "user-1", RelationType.ROLE, "role-1");
    writeUnversionedMembership(EntityType.USER, "user-2", RelationType.ROLE, "role-1");
    writeUnversionedMembership(EntityType.USER, "user-1", RelationType.GROUP, "group-1");

    // when
    final var membershipState = processingState.getMembershipState();
    final var members = new ArrayList<String>();
    membershipState.forEachMember(
        RelationType.ROLE,
        "role-1",
        (type, id) -> {
          members.add(id);
        });

    // then
    assertThat(membershipState.hasRelation(EntityType.USER, "user-1", RelationType.ROLE, "role-1"))
        .isTrue();
    assertThat(membershipState.getMemberships(EntityType.USER, "user-1", RelationType.ROLE))
        .containsExactly("role-1");
    assertThat(members).containsExactlyInAnyOrder("user-1", "user-2");
  }

  @Test
  void shouldShadowUnversionedMembershipWithVersionedRemoval() {
    // given
    writeUnversionedMembership(EntityType.USER, "user-1", RelationType.ROLE, "role-1");

    // when
    processingState
        .getMembershipState()
        .deleteRelation(EntityType.USER, "user-1", RelationType.ROLE, "role-1", 1);

    // then
    final var membershipState = processingState.getMembershipState();
    assertThat(
            membershipState.hasRelation(EntityType.USER, "user-1", RelationType.ROLE, "role-1", 0))
        .isTrue();
    assertThat(
            membershipState.hasRelation(EntityType.USER, "user-1", RelationType.ROLE, "role-1", 1))
        .isFalse();
  }

  @Test
  void shouldReadUnversionedAuthorization() {
    // given
    final var authorizations =
        unversionedColumnFamily(ZbColumnFamilies.AUTHORIZATIONS, new DbLong());
    final var authorizationKey = new DbLong();
    authorizationKey.wrapLong(5L);
    final var authorization =
        new PersistedAuthorization()
            .setAuthorizationKey(5L)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setOwnerId("user-1")
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("process-1")
            .setPermissionTypes(Set.of(PermissionType.CREATE_PROCESS_INSTANCE));
    authorizations.upsert(authorizationKey, unversionedValue(authorization));

    final var owner = new DbCompositeKey<>(new DbString(), new DbString());
    owner.first().wrapString(AuthorizationOwnerType.USER.name());
    owner.second().wrapString("user-1");
    final var keys = new AuthorizationKeys();
    keys.setAuthorizationKeys(Set.of(5L));
    zeebeDb
        .createColumnFamily(
            ZbColumnFamilies.AUTHORIZATION_KEYS_BY_OWNER,
            transactionContext,
            owner,
            new AuthorizationKeys())
        .upsert(owner, keys);

    final var ownerAndResourceType = new DbCompositeKey<>(owner, new DbString());
    ownerAndResourceType.second().wrapString(AuthorizationResourceType.PROCESS_DEFINITION.name());
    final var permissions = new Permissions();
    permissions.addAuthorizationScope(
        PermissionType.CREATE_PROCESS_INSTANCE, AuthorizationScope.of("process-1"));
    zeebeDb
        .createColumnFamily(
            ZbColumnFamilies.PERMISSIONS,
            transactionContext,
            ownerAndResourceType,
            new Permissions())
        .upsert(ownerAndResourceType, permissions);

    // when
    final var authorizationState = processingState.getAuthorizationState();

    // then
    assertThat(authorizationState.get(5L))
        .hasValueSatisfying(a -> assertThat(a.getResourceId()).isEqualTo("process-1"));
    assertThat(
            authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.USER, "user-1"))
        .containsExactly(5L);
    assertThat(
            authorizationState.getAuthorizationScopes(
                AuthorizationOwnerType.USER,
                "user-1",
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.CREATE_PROCESS_INSTANCE))
        .containsExactly(AuthorizationScope.of("process-1"));
  }

  private void writeUnversionedMembership(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    final var entity = new DbCompositeKey<>(new DbEnumValue<>(EntityType.class), new DbString());
    entity.first().setValue(entityType);
    entity.second().wrapString(entityId);
    final var relation =
        new DbCompositeKey<>(new DbEnumValue<>(RelationType.class), new DbString());
    relation.first().setValue(relationType);
    relation.second().wrapString(relationId);

    final var entityAndRelation = new DbCompositeKey<>(entity, relation);
    zeebeDb
        .createColumnFamily(
            ZbColumnFamilies.RELATIONS_BY_ENTITY,
            transactionContext,
            entityAndRelation,
            DbNil.INSTANCE)
        .upsert(entityAndRelation, DbNil.INSTANCE);
    final var relationAndEntity = new DbCompositeKey<>(relation, entity);
    zeebeDb
        .createColumnFamily(
            ZbColumnFamilies.ENTITIES_BY_RELATION,
            transactionContext,
            relationAndEntity,
            DbNil.INSTANCE)
        .upsert(relationAndEntity, DbNil.INSTANCE);
  }

  private <K extends DbKey> ColumnFamily<K, DbBytes> unversionedColumnFamily(
      final ZbColumnFamilies columnFamily, final K key) {
    return zeebeDb.createColumnFamily(columnFamily, transactionContext, key, new DbBytes());
  }

  private static DbString string(final String value) {
    final var dbString = new DbString();
    dbString.wrapString(value);
    return dbString;
  }

  /**
   * Serializes the value as the unversioned layout stored it: the msgpack map without the {@code
   * deleted} property, which policy versioning added.
   */
  private static DbBytes unversionedValue(final UnpackedObject value) {
    final var source = new ExpandableArrayBuffer();
    final int length = value.write(source, 0);
    final var reader = new MsgPackReader().wrap(source, 0, length);
    final int size = reader.readMapHeader();
    final List<int[]> keptEntries = new ArrayList<>();
    boolean removed = false;
    for (int i = 0; i < size; i++) {
      final int start = reader.getOffset();
      final int keyLength = reader.readStringLength();
      final var name =
          new String(
              Arrays.copyOfRange(
                  source.byteArray(), reader.getOffset(), reader.getOffset() + keyLength),
              StandardCharsets.UTF_8);
      reader.skipBytes(keyLength);
      reader.skipValue();
      if (name.equals("deleted")) {
        removed = true;
      } else {
        keptEntries.add(new int[] {start, reader.getOffset()});
      }
    }
    assertThat(removed).describedAs("the value has a deleted property to remove").isTrue();

    final var target = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(target, 0);
    writer.writeMapHeader(keptEntries.size());
    for (final int[] entry : keptEntries) {
      writer.writeRaw(source, entry[0], entry[1] - entry[0]);
    }
    final var bytes = new DbBytes();
    bytes.wrapBytes(Arrays.copyOf(target.byteArray(), writer.getOffset()));
    return bytes;
  }
}
