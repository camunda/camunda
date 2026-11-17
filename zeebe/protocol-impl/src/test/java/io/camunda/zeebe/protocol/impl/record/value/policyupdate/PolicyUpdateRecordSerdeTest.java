/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
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
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class PolicyUpdateRecordSerdeTest {

  private static final String POLICY_ID = "a policy id that is longer than forty bytes of utf8";
  private static final String UPDATE_ID = "an update id";
  private static final String ERROR_MESSAGE = "an error message that is longer than forty bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord();
    final var current = new PolicyUpdateRecord();
    setAllProperties(v1);
    setAllProperties(current);

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
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord();
    final var current = new PolicyUpdateRecord();

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PolicyUpdateRecord();
    current
        .setPolicyId("dirty")
        .setPolicyVersion(7L)
        .setChunkIndex(7)
        .setErrorMessage("dirty")
        .setRoles(List.<RoleRecordValue>of(role(), role()));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPolicyId()).isEqualTo(POLICY_ID);
    assertThat(current.getPolicyVersion()).isEqualTo(42L);
    assertThat(current.getBasePolicyVersion()).isEqualTo(41L);
    assertThat(current.getSeqNo()).isEqualTo(3L);
    assertThat(current.getUpdateId()).isEqualTo(UPDATE_ID);
    assertThat(current.getChunkIndex()).isEqualTo(1);
    assertThat(current.getChunkCount()).isEqualTo(2);
    assertThat(current.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(current.getRoles()).hasSize(1);
    assertThat(current.getTenants()).hasSize(1);
    assertThat(current.getGroups()).hasSize(1);
    assertThat(current.getMappingRules()).hasSize(1);
    assertThat(current.getMemberships()).hasSize(1);
    assertThat(current.getAuthorizations()).hasSize(1);
    assertThat(current.getDeletes()).hasSize(1);
    assertThat(current.getMembershipDeletes()).hasSize(1);
    assertThat(current.getAuthorizationDeletes()).hasSize(1);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given an empty value, which is all defaults
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord()
            .write(bytes, 0);
    final var current = new PolicyUpdateRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new PolicyUpdateRecord();
    assertThat(current.getPolicyId()).isEqualTo(defaults.getPolicyId());
    assertThat(current.getPolicyVersion()).isEqualTo(defaults.getPolicyVersion());
    assertThat(current.getChunkIndex()).isEqualTo(defaults.getChunkIndex());
    assertThat(current.getErrorMessage()).isEqualTo(defaults.getErrorMessage());
    assertThat(current.getRoles()).isEmpty();
    assertThat(current.getAuthorizations()).isEmpty();
    assertThat(current.getAuthorizationDeletes()).isEmpty();
  }

  void shouldBeReadByV1() {
    // given
    final var current = new PolicyUpdateRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getPolicyId()).isEqualTo(POLICY_ID);
    assertThat(v1.getPolicyVersion()).isEqualTo(42L);
    assertThat(v1.getUpdateId()).isEqualTo(UPDATE_ID);
    assertThat(v1.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(v1.getRoles()).hasSize(1);
    assertThat(v1.getAuthorizationDeletes()).hasSize(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PolicyUpdateRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 17 entries, so its count takes two bytes: 0xde 0x00 0x11 becomes 0x12
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new PolicyUpdateRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setErrorMessage("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord();
    final var current = new PolicyUpdateRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  /**
   * Sets the same values on both: the setters take the interfaces and copy them, so the same values
   * feed the V1 and the current record.
   */
  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyUpdateRecord
          record) {
    record
        .setPolicyId(POLICY_ID)
        .setPolicyVersion(42L)
        .setBasePolicyVersion(41L)
        .setSeqNo(3L)
        .setUpdateId(UPDATE_ID)
        .setChunkIndex(1)
        .setChunkCount(2)
        .setTenants(List.<TenantRecordValue>of(tenant()))
        .setRoles(List.<RoleRecordValue>of(role()))
        .setGroups(List.<GroupRecordValue>of(group()))
        .setMappingRules(List.<MappingRuleRecordValue>of(mappingRule()))
        .setMemberships(List.<PolicyMembershipEntryValue>of(membership()))
        .setAuthorizations(List.<AuthorizationRecordValue>of(authorization()))
        .setDeletes(List.<PolicyDeleteEntryValue>of(delete()))
        .setMembershipDeletes(List.<PolicyMembershipEntryValue>of(membership()))
        .setAuthorizationDeletes(
            List.<PolicyAuthorizationDeleteEntryValue>of(authorizationDelete()))
        .setErrorMessage(ERROR_MESSAGE);
  }

  private static void setAllProperties(final PolicyUpdateRecord record) {
    record
        .setPolicyId(POLICY_ID)
        .setPolicyVersion(42L)
        .setBasePolicyVersion(41L)
        .setSeqNo(3L)
        .setUpdateId(UPDATE_ID)
        .setChunkIndex(1)
        .setChunkCount(2)
        .setTenants(List.<TenantRecordValue>of(tenant()))
        .setRoles(List.<RoleRecordValue>of(role()))
        .setGroups(List.<GroupRecordValue>of(group()))
        .setMappingRules(List.<MappingRuleRecordValue>of(mappingRule()))
        .setMemberships(List.<PolicyMembershipEntryValue>of(membership()))
        .setAuthorizations(List.<AuthorizationRecordValue>of(authorization()))
        .setDeletes(List.<PolicyDeleteEntryValue>of(delete()))
        .setMembershipDeletes(List.<PolicyMembershipEntryValue>of(membership()))
        .setAuthorizationDeletes(
            List.<PolicyAuthorizationDeleteEntryValue>of(authorizationDelete()))
        .setErrorMessage(ERROR_MESSAGE);
  }

  private static TenantRecord tenant() {
    return new TenantRecord()
        .setTenantKey(3L)
        .setTenantId("tenant")
        .setName("tenant name")
        .setDescription("description")
        .setEntityId("entity")
        .setEntityType(EntityType.USER);
  }

  private static RoleRecord role() {
    return new RoleRecord()
        .setRoleKey(4L)
        .setRoleId("role")
        .setName("role name")
        .setDescription("description")
        .setEntityId("entity")
        .setEntityType(EntityType.GROUP);
  }

  private static GroupRecord group() {
    return new GroupRecord()
        .setGroupKey(5L)
        .setGroupId("group")
        .setName("group name")
        .setDescription("description")
        .setEntityId("entity")
        .setEntityType(EntityType.GROUP);
  }

  private static MappingRuleRecord mappingRule() {
    return new MappingRuleRecord()
        .setMappingRuleKey(6L)
        .setMappingRuleId("mapping rule")
        .setClaimName("claim")
        .setClaimValue("value")
        .setName("rule");
  }

  private static AuthorizationRecord authorization() {
    return new AuthorizationRecord()
        .setAuthorizationKey(7L)
        .setOwnerId("owner")
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId("resource")
        .setResourcePropertyName("")
        .setResourceType(AuthorizationResourceType.ROLE)
        .setPermissionTypes(EnumSet.of(PermissionType.CREATE, PermissionType.READ));
  }

  private static PolicyMembershipEntry membership() {
    return new PolicyMembershipEntry()
        .setTargetType(PolicyUpdateEntityType.ROLE)
        .setTargetId("target")
        .setMemberType(EntityType.USER)
        .setMemberId("member");
  }

  private static PolicyDeleteEntry delete() {
    return new PolicyDeleteEntry().setEntityType(PolicyUpdateEntityType.GROUP).setId("deleted");
  }

  private static PolicyAuthorizationDeleteEntry authorizationDelete() {
    return new PolicyAuthorizationDeleteEntry()
        .setOwnerType(AuthorizationOwnerType.CLIENT)
        .setOwnerId("owner")
        .setResourceType(AuthorizationResourceType.BATCH)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("")
        .setResourcePropertyName("");
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
