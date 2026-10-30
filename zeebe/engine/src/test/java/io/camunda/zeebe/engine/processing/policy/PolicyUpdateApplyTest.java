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
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
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
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateApplyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyAChunkWithEverySection() {
    // given
    final var tenantId = "tenant-" + uid();
    final var roleId = "role-" + uid();
    final var groupId = "group-" + uid();
    final var mappingRuleId = "rule-" + uid();
    final var chunk =
        chunk()
            .setTenants(
                List.<TenantRecordValue>of(
                    new TenantRecord().setTenantId(tenantId).setName(tenantId)))
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)))
            .setGroups(
                List.<GroupRecordValue>of(new GroupRecord().setGroupId(groupId).setName(groupId)))
            .setMappingRules(
                List.<MappingRuleRecordValue>of(
                    new MappingRuleRecord()
                        .setMappingRuleId(mappingRuleId)
                        .setName(mappingRuleId)
                        .setClaimName("department")
                        .setClaimValue(mappingRuleId)))
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    membership(
                        PolicyUpdateEntityType.ROLE,
                        roleId,
                        EntityType.MAPPING_RULE,
                        mappingRuleId),
                    membership(PolicyUpdateEntityType.TENANT, tenantId, EntityType.ROLE, roleId),
                    membership(
                        PolicyUpdateEntityType.GROUP,
                        groupId,
                        EntityType.CLIENT,
                        "client-" + uid())))
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(roleId, PermissionType.READ_PROCESS_DEFINITION)));

    // when
    apply(chunk);

    // then
    assertThat(appliedRecord(chunk.getUpdateId()).getValue().getPolicyVersion()).isEqualTo(1L);
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATED).withTenantId(tenantId).exists())
        .isTrue();
    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
    assertThat(RecordingExporter.groupRecords(GroupIntent.CREATED).withGroupId(groupId).exists())
        .isTrue();
    assertThat(
            RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED)
                .withMappingRuleId(mappingRuleId)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED)
                .withRoleId(roleId)
                .withEntityId(mappingRuleId)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.ENTITY_ADDED)
                .withTenantId(tenantId)
                .withEntityId(roleId)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_ADDED).withGroupId(groupId).exists())
        .isTrue();
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId(roleId)
                .getFirst()
                .getValue()
                .getPermissionTypes())
        .containsExactly(PermissionType.READ_PROCESS_DEFINITION);
  }

  @Test
  public void shouldUpdateAndDeleteEntitiesOfAnEarlierUpdate() {
    // given
    final var roleId = "role-" + uid();
    final var groupId = "group-" + uid();
    final var first =
        chunk()
            .setRoles(
                List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName("before")))
            .setGroups(
                List.<GroupRecordValue>of(new GroupRecord().setGroupId(groupId).setName(groupId)))
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(roleId, PermissionType.READ_PROCESS_DEFINITION)));
    apply(first);
    appliedRecord(first.getUpdateId());
    RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED).withOwnerId(roleId).await();

    final var second =
        chunk()
            .setPolicyVersion(2)
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName("after")))
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(roleId, PermissionType.UPDATE_PROCESS_INSTANCE)))
            .setDeletes(
                List.<PolicyDeleteEntryValue>of(
                    new PolicyDeleteEntry()
                        .setEntityType(PolicyUpdateEntityType.GROUP)
                        .setId(groupId)));

    // when
    apply(second);

    // then
    appliedRecord(second.getUpdateId());
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.UPDATED)
                .withRoleId(roleId)
                .getFirst()
                .getValue()
                .getName())
        .isEqualTo("after");
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.UPDATED)
                .withOwnerId(roleId)
                .getFirst()
                .getValue()
                .getPermissionTypes())
        .containsExactly(PermissionType.UPDATE_PROCESS_INSTANCE);
    assertThat(RecordingExporter.groupRecords(GroupIntent.DELETED).withGroupId(groupId).exists())
        .isTrue();
  }

  @Test
  public void shouldRejectAnInvalidChunkWithoutApplyingAnyOfIt() {
    // given
    final var roleId = "role-" + uid();
    final var missingGroupId = "missing-" + uid();
    final var chunk =
        chunk()
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)))
            .setDeletes(
                List.<PolicyDeleteEntryValue>of(
                    new PolicyDeleteEntry()
                        .setEntityType(PolicyUpdateEntityType.GROUP)
                        .setId(missingGroupId)));

    // when
    apply(chunk);

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(chunk.getUpdateId()))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(missingGroupId, "does not exist");
    assertThat(
            RecordingExporter.records()
                .limit(
                    record ->
                        record.getRecordType() == RecordType.COMMAND_REJECTION
                            && record.getValueType() == ValueType.POLICY_UPDATE)
                .roleRecords()
                .withRoleId(roleId)
                .exists())
        .describedAs("no role command of the rejected chunk is written")
        .isFalse();
  }

  @Test
  public void shouldRejectAnUpdateOfMoreThanOneChunk() {
    // given
    final var chunk = chunk().setChunkIndex(0).setChunkCount(2);

    // when
    apply(chunk);

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(chunk.getUpdateId()))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("single chunk");
  }

  static PolicyUpdateRecord chunk() {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + uid())
        .setPolicyVersion(1)
        .setUpdateId("update-" + uid())
        .setChunkIndex(0)
        .setChunkCount(1);
  }

  static void apply(final EngineRule engine, final PolicyUpdateRecord chunk) {
    engine.writeRecords(RecordToWrite.command().policyUpdate(PolicyUpdateIntent.APPLY, chunk));
  }

  private static void apply(final PolicyUpdateRecord chunk) {
    apply(ENGINE, chunk);
  }

  static Record<PolicyUpdateRecordValue> appliedRecord(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLIED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
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
      final String roleId, final PermissionType permission) {
    return new AuthorizationRecord()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId(roleId)
        .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId("process-" + roleId)
        .setPermissionTypes(Set.of(permission));
  }

  static String uid() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
