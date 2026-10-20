/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateMembershipAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldAddAndRemoveGroupMembershipOfRole() {
    // given
    engine.writeRecords(
        write(
            chunk(1)
                .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))
                .setGroups(List.<GroupRecordValue>of(new GroupRecord().setGroupId("g1")))));
    appliedLast(1);

    // when
    engine.writeRecords(
        write(
            chunk(2)
                .setMemberships(
                    List.<PolicyMembershipEntryValue>of(
                        membership(PolicyUpdateEntityType.ROLE, "r1", EntityType.GROUP, "g1")))));
    appliedLast(2);
    engine.writeRecords(
        write(
            chunk(3)
                .setMembershipDeletes(
                    List.<PolicyMembershipEntryValue>of(
                        membership(PolicyUpdateEntityType.ROLE, "r1", EntityType.GROUP, "g1")))));
    appliedLast(3);

    // then
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.ROLE)
                .withIntent(RoleIntent.ENTITY_ADDED)
                .getFirst()
                .getValue())
        .isNotNull();
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.ROLE)
                .withIntent(RoleIntent.ENTITY_REMOVED)
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldCreateAuthorizationThenUpdatePermissionsOfSameIdentity() {
    // given
    engine.writeRecords(
        write(
            chunk(1)
                .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))
                .setAuthorizations(
                    List.<AuthorizationRecordValue>of(
                        authorization(Set.of(PermissionType.READ))))));
    appliedLast(1);
    final Record<?> created =
        RecordingExporter.records()
            .withValueType(ValueType.AUTHORIZATION)
            .withIntent(AuthorizationIntent.CREATED)
            .getFirst();

    // when
    engine.writeRecords(
        write(
            chunk(2)
                .setAuthorizations(
                    List.<AuthorizationRecordValue>of(
                        authorization(Set.of(PermissionType.READ, PermissionType.UPDATE))))));
    appliedLast(2);

    // then
    final Record<?> updated =
        RecordingExporter.records()
            .withValueType(ValueType.AUTHORIZATION)
            .withIntent(AuthorizationIntent.UPDATED)
            .getFirst();
    assertThat(updated.getKey()).isEqualTo(created.getKey());
  }

  @Test
  public void shouldDeleteAuthorizationByItsIdentity() {
    // given
    engine.writeRecords(
        write(
            chunk(1)
                .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))
                .setAuthorizations(
                    List.<AuthorizationRecordValue>of(
                        authorization(Set.of(PermissionType.READ))))));
    appliedLast(1);
    final Record<?> created =
        RecordingExporter.records()
            .withValueType(ValueType.AUTHORIZATION)
            .withIntent(AuthorizationIntent.CREATED)
            .getFirst();

    // when
    engine.writeRecords(
        write(
            chunk(2)
                .setAuthorizationDeletes(
                    List.<AuthorizationRecordValue>of(authorization(Set.of())))));
    appliedLast(2);

    // then
    final Record<?> deleted =
        RecordingExporter.records()
            .withValueType(ValueType.AUTHORIZATION)
            .withIntent(AuthorizationIntent.DELETED)
            .getFirst();
    assertThat(deleted.getKey()).isEqualTo(created.getKey());
  }

  private static AuthorizationRecord authorization(final Set<PermissionType> permissions) {
    return new AuthorizationRecord()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId("r1")
        .setResourceType(AuthorizationResourceType.AUTHORIZATION)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("*")
        .setResourcePropertyName("")
        .setPermissionTypes(permissions);
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

  private static PolicyUpdateRecord chunk(final long version) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(UUID.randomUUID().toString())
        .setPolicyVersion(version)
        .setChunkCount(1)
        .setChunkIndex(0);
  }

  private static RecordToWrite write(final PolicyUpdateRecord chunk) {
    return RecordToWrite.command().policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk);
  }

  private static Record<?> appliedLast(final long version) {
    return RecordingExporter.records()
        .withValueType(ValueType.POLICY_UPDATE)
        .withIntent(PolicyUpdateIntent.APPLIED)
        .filter(
            record -> {
              final var value = (PolicyUpdateRecordValue) record.getValue();
              return value.isLastPage() && value.getPolicyVersion() == version;
            })
        .getFirst();
  }
}
