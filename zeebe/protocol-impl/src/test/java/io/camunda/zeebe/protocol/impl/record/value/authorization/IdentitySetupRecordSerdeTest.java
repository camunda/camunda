/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link IdentitySetupRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced. Its nested records that are migrated are compared with their V1 copies;
 * the ones that are not migrated are shared by both.
 */
final class IdentitySetupRecordSerdeTest {

  private static final String ROLE_ID = "a role id that is longer than forty bytes of utf8 text";
  private static final String TENANT_ID = "a tenant id that is longer than forty bytes of utf8";
  private static final String MAPPING_RULE_ID = "a mapping rule id";
  private static final String OWNER_ID = "an owner id";
  private static final EnumSet<PermissionType> PERMISSIONS =
      EnumSet.of(PermissionType.CREATE, PermissionType.READ);

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new IdentitySetupRecordV1();
    final var current = new IdentitySetupRecord();
    populate(v1, current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WhenOnlyTheDefaultTenantIsSet() {
    // given
    final var v1 = new IdentitySetupRecordV1();
    final var current = new IdentitySetupRecord();
    v1.setDefaultTenant(tenant());
    current.setDefaultTenant(tenant());

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new IdentitySetupRecordV1();
    populate(v1, new IdentitySetupRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new IdentitySetupRecord();
    populate(new IdentitySetupRecordV1(), current);
    current.addRole(new RoleRecord().setRoleId("another role"));

    // when
    current.wrap(bytes, 0, length);

    // then the lists are replaced, not appended to
    assertThat(current.getRoles()).hasSize(1);
    assertThat(current.getRoles().iterator().next().getRoleId()).isEqualTo(ROLE_ID);
    assertThat(current.getRoleMembers()).hasSize(1);
    assertThat(current.getUsers()).hasSize(1);
    assertThat(current.getTenants()).hasSize(1);
    assertThat(current.getTenantMembers()).hasSize(1);
    assertThat(current.getMappingRules()).hasSize(1);
    assertThat(current.getAuthorizations()).hasSize(1);
    assertThat(current.getGroups()).hasSize(1);
    assertThat(current.getGroupMembers()).hasSize(1);
    assertThat(current.getDefaultTenant().getTenantId()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new IdentitySetupRecord();
    populate(new IdentitySetupRecordV1(), current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new IdentitySetupRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRoles()).hasSize(1);
    assertThat(v1.getRoles().iterator().next().getRoleId()).isEqualTo(ROLE_ID);
    assertThat(v1.getAuthorizations()).hasSize(1);
    assertThat(v1.getDefaultTenant().getTenantId()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new IdentitySetupRecord();
    populate(new IdentitySetupRecordV1(), source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has ten entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new IdentitySetupRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.addUser(new UserRecord().setUserKey(9L).setUsername("another user"));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new IdentitySetupRecordV1();
    final var current = new IdentitySetupRecord();
    populate(v1, current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  /** Fills the same content into the V1 and the current record. */
  private static void populate(final IdentitySetupRecordV1 v1, final IdentitySetupRecord current) {
    v1.addRole(roleV1());
    current.addRole(role());
    v1.addRoleMember(roleV1());
    current.addRoleMember(role());
    v1.addUser(user());
    current.addUser(user());
    v1.setDefaultTenant(tenant());
    current.setDefaultTenant(tenant());
    v1.addTenant(tenant());
    current.addTenant(tenant());
    v1.addTenantMember(tenant());
    current.addTenantMember(tenant());
    v1.addMappingRule(mappingRuleV1());
    current.addMappingRule(mappingRule());
    v1.addAuthorization(authorizationV1());
    current.addAuthorization(authorization());
    v1.addGroup(group());
    current.addGroup(group());
    v1.addGroupMember(group());
    current.addGroupMember(group());
  }

  private static RoleRecordV1 roleV1() {
    return new RoleRecordV1().setRoleKey(42L).setRoleId(ROLE_ID).setName("role");
  }

  private static RoleRecord role() {
    return new RoleRecord().setRoleKey(42L).setRoleId(ROLE_ID).setName("role");
  }

  private static MappingRuleRecordV1 mappingRuleV1() {
    return new MappingRuleRecordV1()
        .setMappingRuleKey(42L)
        .setMappingRuleId(MAPPING_RULE_ID)
        .setClaimName("claim")
        .setClaimValue("value")
        .setName("rule");
  }

  private static MappingRuleRecord mappingRule() {
    return new MappingRuleRecord()
        .setMappingRuleKey(42L)
        .setMappingRuleId(MAPPING_RULE_ID)
        .setClaimName("claim")
        .setClaimValue("value")
        .setName("rule");
  }

  private static AuthorizationRecordV1 authorizationV1() {
    return new AuthorizationRecordV1()
        .setAuthorizationKey(42L)
        .setOwnerId(OWNER_ID)
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId("resource")
        .setResourcePropertyName("")
        .setResourceType(AuthorizationResourceType.ROLE)
        .setPermissionTypes(PERMISSIONS);
  }

  private static AuthorizationRecord authorization() {
    return new AuthorizationRecord()
        .setAuthorizationKey(42L)
        .setOwnerId(OWNER_ID)
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId("resource")
        .setResourcePropertyName("")
        .setResourceType(AuthorizationResourceType.ROLE)
        .setPermissionTypes(PERMISSIONS);
  }

  private static UserRecord user() {
    return new UserRecord()
        .setUserKey(7L)
        .setUsername("username")
        .setName("name")
        .setEmail("email@example.com")
        .setPassword("password");
  }

  private static TenantRecord tenant() {
    return new TenantRecord()
        .setTenantKey(3L)
        .setTenantId(TENANT_ID)
        .setName("tenant")
        .setDescription("description")
        .setEntityId("entity")
        .setEntityType(EntityType.USER);
  }

  private static GroupRecord group() {
    return new GroupRecord()
        .setGroupKey(5L)
        .setGroupId("group")
        .setName("group")
        .setDescription("description")
        .setEntityId("entity")
        .setEntityType(EntityType.GROUP);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
