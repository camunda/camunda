/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommitVersionTest.versionCommitted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * An identity command of a policy update that its processor rejects fails the whole update: the
 * update is not committed, and its remaining identity commands and chunks are not applied. The
 * commands applied before the rejection stay applied. A command that asks for what is already the
 * case, like deleting a role that doesn't exist, is rejected without failing the update.
 */
public final class PolicyUpdateRejectedCommandTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldFailTheUpdateWhenOneOfItsCommandsIsRejected() {
    // given
    final var missingRoleId = "role-" + uid();
    final var update = chunk().setMemberships(addToMissingRole(missingRoleId));

    // when
    apply(ENGINE, update);

    // then
    final var rejection =
        RecordingExporter.roleRecords(RoleIntent.ADD_ENTITY)
            .onlyCommandRejections()
            .withRoleId(missingRoleId)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    final var failed = failedRecord(update.getUpdateId());
    assertThat(failed.getValue().getErrorMessage())
        .contains(update.getUpdateId(), "ROLE.ADD_ENTITY", "NOT_FOUND", missingRoleId);
  }

  @Test
  public void shouldContinueTheUpdateWhenACommandAsksForWhatIsAlreadyTheCase() {
    // given a group of an earlier update
    final var groupId = "group-" + uid();
    final var earlier =
        chunk()
            .setGroups(
                List.<GroupRecordValue>of(new GroupRecord().setGroupId(groupId).setName(groupId)));
    apply(ENGINE, earlier);
    versionCommitted(earlier.getUpdateId());

    final var missingRoleId = "role-" + uid();
    final var update =
        chunk()
            .setPolicyVersion(2)
            .setDeletes(
                List.of(
                    delete(PolicyUpdateEntityType.ROLE, missingRoleId),
                    delete(PolicyUpdateEntityType.GROUP, groupId)));

    // when
    apply(ENGINE, update);

    // then the delete of the missing role is rejected as a warning only
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.DELETE)
                .onlyCommandRejections()
                .withRoleId(missingRoleId)
                .getFirst()
                .getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);
    final var records = recordsUntilAMarkerUpdateIsCommitted(update.getUpdateId());
    assertThat(records)
        .anyMatch(
            record ->
                record.getIntent() == GroupIntent.DELETED
                    && ((GroupRecordValue) record.getValue()).getGroupId().equals(groupId))
        .noneMatch(record -> isPolicyUpdateEvent(record, PolicyUpdateIntent.FAILED, update));
  }

  @Test
  public void shouldNotCommitAFailedUpdate() {
    // given
    final var update = chunk().setMemberships(addToMissingRole("role-" + uid()));

    // when
    apply(ENGINE, update);

    // then the commit, written after the identity commands of the last chunk, is rejected
    final var commitRejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.COMMIT_VERSION)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
            .getFirst();
    assertThat(commitRejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(recordsUntilAMarkerUpdateIsCommitted(update.getUpdateId()))
        .noneMatch(
            record -> isPolicyUpdateEvent(record, PolicyUpdateIntent.VERSION_COMMITTED, update));
  }

  @Test
  public void shouldRejectTheRemainingCommandsOfAFailedUpdate() {
    // given a group of an earlier update
    final var groupId = "group-" + uid();
    final var earlier =
        chunk()
            .setGroups(
                List.<GroupRecordValue>of(new GroupRecord().setGroupId(groupId).setName(groupId)));
    apply(ENGINE, earlier);
    versionCommitted(earlier.getUpdateId());

    // the membership is applied before the delete
    final var update =
        chunk()
            .setPolicyVersion(2)
            .setMemberships(addToMissingRole("role-" + uid()))
            .setDeletes(List.of(delete(PolicyUpdateEntityType.GROUP, groupId)));

    // when
    apply(ENGINE, update);

    // then the group delete, which its processor would accept, is rejected
    final var groupRejection =
        RecordingExporter.groupRecords(GroupIntent.DELETE)
            .onlyCommandRejections()
            .withGroupId(groupId)
            .getFirst();
    assertThat(groupRejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(groupRejection.getRejectionReason()).contains("failed or no longer exists");

    final var records = recordsUntilAMarkerUpdateIsCommitted(update.getUpdateId());
    assertThat(records)
        .noneMatch(
            record ->
                record.getIntent() == GroupIntent.DELETED
                    && ((GroupRecordValue) record.getValue()).getGroupId().equals(groupId));
    assertThat(records)
        .filteredOn(record -> isPolicyUpdateEvent(record, PolicyUpdateIntent.FAILED, update))
        .describedAs("only the first rejection fails the update")
        .hasSize(1);
  }

  @Test
  public void shouldRejectTheRemainingCommandsOfEveryIdentityType() {
    // given an update whose second tenant create is rejected, as the first creates the tenant;
    // tenants are applied first, so every other identity type follows the rejection
    final var tenantId = "tenant-" + uid();
    final var roleId = "role-" + uid();
    final var groupId = "group-" + uid();
    final var mappingRuleId = "rule-" + uid();
    final var tenant = new TenantRecord().setTenantId(tenantId).setName(tenantId);
    final var update =
        chunk()
            .setTenants(List.<TenantRecordValue>of(tenant, tenant))
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
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.TENANT)
                        .setTargetId(tenantId)
                        .setMemberType(EntityType.ROLE)
                        .setMemberId(roleId)))
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    new AuthorizationRecord()
                        .setOwnerType(AuthorizationOwnerType.ROLE)
                        .setOwnerId(roleId)
                        .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                        .setResourceMatcher(AuthorizationResourceMatcher.ID)
                        .setResourceId("process-" + roleId)
                        .setPermissionTypes(Set.of(PermissionType.READ_PROCESS_DEFINITION))));

    // when
    apply(ENGINE, update);

    // then
    // the records from the update on, as the rejections don't carry its reference
    final var records =
        recordsUntilAMarkerUpdateIsCommitted(update.getUpdateId()).stream()
            .dropWhile(
                record -> !isPolicyUpdateEvent(record, PolicyUpdateIntent.CHUNK_APPLIED, update))
            .toList();
    assertThat(records)
        .filteredOn(record -> record.getRecordType() == RecordType.COMMAND_REJECTION)
        .filteredOn(record -> record.getValueType() != ValueType.POLICY_UPDATE)
        .extracting(Record::getValueType, Record::getIntent, Record::getRejectionType)
        .containsExactly(
            tuple(ValueType.TENANT, TenantIntent.CREATE, RejectionType.ALREADY_EXISTS),
            tuple(ValueType.ROLE, RoleIntent.CREATE, RejectionType.INVALID_STATE),
            tuple(ValueType.GROUP, GroupIntent.CREATE, RejectionType.INVALID_STATE),
            tuple(ValueType.MAPPING_RULE, MappingRuleIntent.CREATE, RejectionType.INVALID_STATE),
            tuple(ValueType.TENANT, TenantIntent.ADD_ENTITY, RejectionType.INVALID_STATE),
            tuple(
                ValueType.AUTHORIZATION, AuthorizationIntent.CREATE, RejectionType.INVALID_STATE));
    assertThat(records)
        .filteredOn(record -> isPolicyUpdateEvent(record, PolicyUpdateIntent.FAILED, update))
        .singleElement()
        .satisfies(
            failed ->
                assertThat(((PolicyUpdateRecordValue) failed.getValue()).getErrorMessage())
                    .contains("TENANT.CREATE", "ALREADY_EXISTS", tenantId));
  }

  @Test
  public void shouldNotApplyTheChunksAfterAFailedChunk() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    final var first = chunkOf(updateId, 0, 2).setMemberships(addToMissingRole("role-" + uid()));
    final var second =
        chunkOf(updateId, 1, 2)
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)));

    // when
    apply(ENGINE, first);
    apply(ENGINE, second);

    // then
    failedRecord(updateId);
    final var applyRejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(applyRejection.getValue().getChunkIndex()).isOne();
    assertThat(applyRejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);

    // and the next update applies, as the failed one was removed
    assertThat(recordsUntilAMarkerUpdateIsCommitted(updateId))
        .noneMatch(
            record ->
                record.getIntent() == RoleIntent.CREATED
                    && ((RoleRecordValue) record.getValue()).getRoleId().equals(roleId));
  }

  private static PolicyUpdateRecord chunkOf(
      final String updateId, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + updateId)
        .setPolicyVersion(1)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }

  /** A membership whose target role doesn't exist; adding a member to it fails the update. */
  private static List<PolicyMembershipEntryValue> addToMissingRole(final String missingRoleId) {
    return List.of(
        new PolicyMembershipEntry()
            .setTargetType(PolicyUpdateEntityType.ROLE)
            .setTargetId(missingRoleId)
            .setMemberType(EntityType.MAPPING_RULE)
            .setMemberId("rule-" + uid()));
  }

  private static PolicyDeleteEntryValue delete(
      final PolicyUpdateEntityType entityType, final String id) {
    return new PolicyDeleteEntry().setEntityType(entityType).setId(id);
  }

  private static Record<PolicyUpdateRecordValue> failedRecord(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  /**
   * Waits until the update is committed or failed, then applies an update of one chunk and returns
   * all records up to its commit. Since commands are processed in order, the records contain
   * everything that the earlier commands caused.
   *
   * <p>The identity commands of an update are processed in their own batches, after the command
   * that appended its last chunk. A marker written before the update ends could be processed while
   * the update is still open, and be rejected.
   */
  private static List<Record<?>> recordsUntilAMarkerUpdateIsCommitted(final String afterUpdateId) {
    RecordingExporter.policyUpdateRecords()
        .onlyEvents()
        .filter(record -> record.getValue().getUpdateId().equals(afterUpdateId))
        .filter(
            record ->
                record.getIntent() == PolicyUpdateIntent.VERSION_COMMITTED
                    || record.getIntent() == PolicyUpdateIntent.FAILED)
        .await();
    final var marker = chunk();
    apply(ENGINE, marker);
    return RecordingExporter.records()
        .limit(record -> isPolicyUpdateEvent(record, PolicyUpdateIntent.VERSION_COMMITTED, marker))
        .<Record<?>>map(record -> record)
        .toList();
  }

  private static boolean isPolicyUpdateEvent(
      final Record<?> record, final PolicyUpdateIntent intent, final PolicyUpdateRecord update) {
    return record.getRecordType() == RecordType.EVENT
        && record.getIntent() == intent
        && ((PolicyUpdateRecordValue) record.getValue()).getUpdateId().equals(update.getUpdateId());
  }
}
