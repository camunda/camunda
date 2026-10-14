/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateKind;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class PolicyUpdateRecordTest {

  @Test
  void shouldRoundTripEverySectionOfAChunk() {
    // given
    final var tenant =
        new TenantRecord().setTenantKey(1L).setTenantId("tenant-a").setName("Tenant A");
    final var role = new RoleRecord().setRoleKey(2L).setRoleId("role-a").setName("Role A");
    final var authorization =
        new AuthorizationRecord()
            .setAuthorizationKey(3L)
            .setOwnerId("role-a")
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setResourceMatcher(AuthorizationResourceMatcher.ANY)
            .setResourceId("*")
            .setPermissionTypes(Set.of(PermissionType.READ));
    final var membership =
        new PolicyMembershipEntry()
            .setTargetType(PolicyUpdateEntityType.ROLE)
            .setTargetId("role-a")
            .setMemberType(EntityType.USER)
            .setMemberId("alice");
    final var delete =
        new PolicyDeleteEntry()
            .setEntityType(PolicyUpdateEntityType.MAPPING_RULE)
            .setId("rule-old");

    final var original =
        new PolicyUpdateRecord()
            .setEnvelopeId("envelope-1")
            .setKind(PolicyUpdateKind.DELTA)
            .setPolicyVersion(7L)
            .setChunkIndex(1)
            .setChunkCount(3)
            .setTenants(List.<TenantRecordValue>of(tenant))
            .setRoles(List.<RoleRecordValue>of(role))
            .setMemberships(List.<PolicyMembershipEntryValue>of(membership))
            .setAuthorizations(List.<AuthorizationRecordValue>of(authorization))
            .setDeletes(List.<PolicyDeleteEntryValue>of(delete))
            .setMembershipDeletes(List.<PolicyMembershipEntryValue>of(membership))
            .setAuthorizationDeletes(List.<AuthorizationRecordValue>of(authorization))
            .setLastPage(true);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new PolicyUpdateRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getEnvelopeId()).isEqualTo("envelope-1");
    assertThat(copy.getKind()).isEqualTo(PolicyUpdateKind.DELTA);
    assertThat(copy.getPolicyVersion()).isEqualTo(7L);
    assertThat(copy.getChunkIndex()).isEqualTo(1);
    assertThat(copy.getChunkCount()).isEqualTo(3);
    assertThat(copy.getTenants())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.getTenantId()).isEqualTo("tenant-a");
              assertThat(t.getName()).isEqualTo("Tenant A");
            });
    assertThat(copy.getRoles())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.getRoleId()).isEqualTo("role-a");
              assertThat(r.getRoleKey()).isEqualTo(2L);
            });
    assertThat(copy.getMemberships())
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getTargetType()).isEqualTo(PolicyUpdateEntityType.ROLE);
              assertThat(m.getMemberId()).isEqualTo("alice");
            });
    assertThat(copy.getAuthorizations())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.getAuthorizationKey()).isEqualTo(3L);
              assertThat(a.getPermissionTypes()).containsExactly(PermissionType.READ);
            });
    assertThat(copy.getDeletes())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getEntityType()).isEqualTo(PolicyUpdateEntityType.MAPPING_RULE);
              assertThat(d.getId()).isEqualTo("rule-old");
            });
    assertThat(copy.getMembershipDeletes()).hasSize(1);
    assertThat(copy.getAuthorizationDeletes()).hasSize(1);
    assertThat(copy.isLastPage()).isTrue();
  }

  @Test
  void shouldDefaultMissingPolicyUpdateFields() {
    // given
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"));

    // when
    final var record = new PolicyUpdateRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getKind()).isEqualTo(PolicyUpdateKind.UNSPECIFIED);
    assertThat(record.getPolicyVersion()).isZero();
    assertThat(record.getTenants()).isEmpty();
    assertThat(record.getDeletes()).isEmpty();
    assertThat(record.isLastPage()).isFalse();
  }

  @Test
  void shouldReadIdentityRecordsWithoutPolicyVersionAsUnversioned() {
    // given: a payload written before the policyVersion property existed
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"roleId\":\"r\"}"));

    // when
    final var record = new RoleRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getRoleId()).isEqualTo("r");
    assertThat(record.getPolicyVersion()).isZero();
  }

  @Test
  void shouldRoundTripPolicyVersionOnIdentityRecords() {
    // given
    final var original = new RoleRecord().setRoleKey(9L).setRoleId("r").setPolicyVersion(5L);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new RoleRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getPolicyVersion()).isEqualTo(5L);
  }
}
