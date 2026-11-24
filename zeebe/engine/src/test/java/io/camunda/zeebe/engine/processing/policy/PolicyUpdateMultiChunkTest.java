/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.appliedRecord;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.authorization;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.rejectionOf;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Policy updates of several chunks. */
public final class PolicyUpdateMultiChunkTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyTheUpdateOnlyAfterItsLastChunk() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    apply(ENGINE, chunk(updateId, 1, 2));

    // then
    final var lastChunkAppended = appendedRecord(updateId, 1);
    final var roleCreated =
        RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).getFirst();
    assertThat(roleCreated.getPosition()).isGreaterThan(lastChunkAppended.getPosition());
  }

  @Test
  public void shouldApplyChunksInTheirOrder() {
    // given
    final var updateId = "update-" + uid();
    final var roleIds = List.of("role-0-" + uid(), "role-1-" + uid(), "role-2-" + uid());

    // when
    for (int chunkIndex = 0; chunkIndex < 3; chunkIndex++) {
      apply(
          ENGINE,
          chunk(updateId, chunkIndex, 3)
              .setRoles(List.<RoleRecordValue>of(role(roleIds.get(chunkIndex)))));
    }

    // then
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPLIED)
                .filter(record -> record.getValue().getUpdateId().equals(updateId))
                .limit(3))
        .extracting(record -> record.getValue().getChunkIndex())
        .containsExactly(0, 1, 2);
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.CREATED)
                .filter(record -> roleIds.contains(record.getValue().getRoleId()))
                .limit(3))
        .extracting(record -> record.getValue().getRoleId())
        .containsExactlyElementsOf(roleIds);
  }

  @Test
  public void shouldApplyAChunkThatRefersToAnEntityOfAnEarlierChunk() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    final var clientId = "client-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    apply(
        ENGINE,
        chunk(updateId, 1, 2)
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.ROLE)
                        .setTargetId(roleId)
                        .setMemberType(EntityType.CLIENT)
                        .setMemberId(clientId))));

    // then
    final var entityAdded =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED).withRoleId(roleId).getFirst();
    assertThat(entityAdded.getValue().getEntityId()).isEqualTo(clientId);
  }

  @Test
  public void shouldRejectAChunkOtherThanTheFirstWhenNoUpdateIsOpen() {
    // given
    final var updateId = "update-" + uid();

    // when
    apply(ENGINE, chunk(updateId, 1, 2));

    // then
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(updateId, "start with chunk 0", "chunk 1 was received first");
    assertThat(recordsUntilAMarkerUpdateIsApplied())
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPENDED, updateId))
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.FAILED, updateId));
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkIsSkipped() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setChunkIndex(2), "Expected chunk 1", "received next", "received chunk 2");
  }

  @Test
  public void shouldRejectAChunkOfAnotherUpdateWhileOneIsOpen() {
    // given
    final var openUpdateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(ENGINE, chunk(openUpdateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    final var otherUpdateId = "update-" + uid();
    apply(ENGINE, chunk(otherUpdateId, 0, 1));

    // then
    final var rejection = appendRejection(otherUpdateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(otherUpdateId, "policy update '%s' is open".formatted(openUpdateId));

    // and the open update is still complete when its last chunk arrives
    apply(ENGINE, chunk(openUpdateId, 1, 2));
    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
    assertThat(recordsUntilAMarkerUpdateIsApplied())
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.FAILED, openUpdateId))
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.FAILED, otherUpdateId))
        .noneMatch(
            record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPENDED, otherUpdateId));
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkHasAnotherPolicyId() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setPolicyId("other-policy"), "identifiers of the update", "policyId");
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkHasAnotherPolicyVersion() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setPolicyVersion(3),
        "identifiers of the update",
        "policyVersion 3 instead of 2");
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkHasAnotherBasePolicyVersion() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setBasePolicyVersion(0),
        "identifiers of the update",
        "basePolicyVersion 0 instead of 1");
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkHasAnotherSeqNo() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setSeqNo(8), "identifiers of the update", "seqNo 8 instead of 7");
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkHasAnotherChunkCount() {
    assertFailsWithAChunkThatHas(
        chunk -> chunk.setChunkCount(2), "identifiers of the update", "chunkCount 2 instead of 3");
  }

  @Test
  public void shouldFailTheUpdateWhenAChunkIsReceivedAgain() {
    // given
    final var updateId = "update-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2));

    // when
    apply(ENGINE, chunk(updateId, 0, 2));

    // then
    final var failed =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(failed.getValue().getChunkIndex()).isZero();
    assertThat(failed.getValue().getErrorMessage())
        .contains("Expected chunk 1", updateId, "received next", "received chunk 0");
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).isEqualTo(failed.getValue().getErrorMessage());
  }

  @Test
  public void shouldNotApplyAFailedUpdateWhenItsRemainingChunksArrive() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    apply(ENGINE, chunk(updateId, 1, 2));

    // then the failed update's chunks are gone, and the last chunk neither reopens nor completes it
    final var records = recordsUntilAMarkerUpdateIsApplied();
    assertThat(records)
        .filteredOn(record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPENDED, updateId))
        .extracting(record -> ((PolicyUpdateRecordValue) record.getValue()).getChunkIndex())
        .containsExactly(0);
    assertThat(records)
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPLIED, updateId));
    assertThat(records)
        .filteredOn(record -> record.getIntent() == RoleIntent.CREATED)
        .extracting(record -> ((RoleRecordValue) record.getValue()).getRoleId())
        .doesNotContain(roleId);
  }

  @Test
  public void shouldAcceptTheNextUpdateOnceTheOpenOneIsApplied() {
    // given
    final var firstUpdateId = "update-" + uid();
    apply(ENGINE, chunk(firstUpdateId, 0, 2));
    apply(ENGINE, chunk(firstUpdateId, 1, 2));
    appliedRecord(firstUpdateId);

    // when
    final var secondUpdateId = "update-" + uid();
    apply(ENGINE, chunk(secondUpdateId, 0, 1));

    // then
    assertThat(appliedRecord(secondUpdateId).getValue().getChunkIndex()).isZero();
  }

  @Test
  public void shouldFailAnUpdateWhoseLastChunkIsInvalidWithoutApplyingAnyChunk() {
    // given a valid first chunk, and a last chunk with an authorization of a role that doesn't
    // exist
    final var updateId = "update-" + uid();
    final var validRoleId = "role-" + uid();
    final var missingRoleId = "role-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(validRoleId))));

    // when
    apply(
        ENGINE,
        chunk(updateId, 1, 2)
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(missingRoleId, PermissionType.READ_PROCESS_DEFINITION))));

    // then
    final var rejection = rejectionOf(updateId);
    assertThat(rejection.getValue().getChunkIndex()).isOne();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(missingRoleId);
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
                .filter(record -> record.getValue().getUpdateId().equals(updateId))
                .exists())
        .isTrue();

    // a later update, which can only open once the failed one is removed, marks the end marks the
    // end of everything the invalid one could have caused
    final var laterUpdateId = "update-" + uid();
    apply(ENGINE, chunk(laterUpdateId, 0, 1));
    final var recordsUntilLaterUpdateApplied =
        RecordingExporter.records()
            .limit(
                record ->
                    record.getIntent() == PolicyUpdateIntent.CHUNK_APPLIED
                        && ((PolicyUpdateRecordValue) record.getValue())
                            .getUpdateId()
                            .equals(laterUpdateId))
            .toList();
    assertThat(recordsUntilLaterUpdateApplied)
        .noneMatch(
            record ->
                record.getIntent() == PolicyUpdateIntent.APPLY_CHUNK
                    && ((PolicyUpdateRecordValue) record.getValue()).getUpdateId().equals(updateId))
        .noneMatch(
            record ->
                record.getValue() instanceof final RoleRecordValue role
                    && role.getRoleId().equals(validRoleId));
  }

  @Test
  public void shouldRejectAnUpdateThatUpsertsARoleInTwoChunks() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(ENGINE, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    apply(ENGINE, chunk(updateId, 1, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // then
    final var rejection = rejectionOf(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains("role with ID '%s' appears more than once".formatted(roleId));
  }

  @Test
  public void shouldFailAnUpdateWithAnAuthorizationOfARoleThatAnEarlierChunkDeletes() {
    // given
    final var roleId = "role-" + uid();
    final var setupUpdateId = "update-" + uid();
    apply(ENGINE, chunk(setupUpdateId, 0, 1).setRoles(List.<RoleRecordValue>of(role(roleId))));
    appliedRecord(setupUpdateId);

    // when the first chunk deletes the role, and the second one authorizes it
    final var updateId = "update-" + uid();
    apply(
        ENGINE,
        chunk(updateId, 0, 2)
            .setDeletes(
                List.<PolicyDeleteEntryValue>of(
                    new PolicyDeleteEntry()
                        .setEntityType(PolicyUpdateEntityType.ROLE)
                        .setId(roleId))));
    apply(
        ENGINE,
        chunk(updateId, 1, 2)
            .setAuthorizations(
                List.<AuthorizationRecordValue>of(
                    authorization(roleId, PermissionType.READ_PROCESS_DEFINITION))));

    // then
    final var rejection = rejectionOf(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(roleId);

    // a later update marks the end of everything the invalid one could have caused
    final var laterUpdateId = "update-" + uid();
    apply(ENGINE, chunk(laterUpdateId, 0, 1));
    assertThat(
            RecordingExporter.records()
                .limit(
                    record ->
                        record.getIntent() == PolicyUpdateIntent.CHUNK_APPLIED
                            && ((PolicyUpdateRecordValue) record.getValue())
                                .getUpdateId()
                                .equals(laterUpdateId))
                .roleRecords()
                .withIntent(RoleIntent.DELETED)
                .withRoleId(roleId)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldFailAnUpdateThatRemovesAMemberWhichAnEarlierChunkDeletes() {
    // given a group that is a member of a role
    final var roleId = "role-" + uid();
    final var groupId = "group-" + uid();
    final var setupUpdateId = "update-" + uid();
    apply(
        ENGINE,
        chunk(setupUpdateId, 0, 1)
            .setRoles(List.<RoleRecordValue>of(role(roleId)))
            .setGroups(
                List.<GroupRecordValue>of(new GroupRecord().setGroupId(groupId).setName(groupId)))
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.ROLE)
                        .setTargetId(roleId)
                        .setMemberType(EntityType.GROUP)
                        .setMemberId(groupId))));
    appliedRecord(setupUpdateId);

    // when the first chunk deletes the group, which removes it from the role, and the second
    // chunk removes it from the role again
    final var updateId = "update-" + uid();
    apply(
        ENGINE,
        chunk(updateId, 0, 2)
            .setDeletes(
                List.<PolicyDeleteEntryValue>of(
                    new PolicyDeleteEntry()
                        .setEntityType(PolicyUpdateEntityType.GROUP)
                        .setId(groupId))));
    apply(
        ENGINE,
        chunk(updateId, 1, 2)
            .setMembershipDeletes(
                List.<PolicyMembershipEntryValue>of(
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.ROLE)
                        .setTargetId(roleId)
                        .setMemberType(EntityType.GROUP)
                        .setMemberId(groupId))));

    // then
    final var rejection = rejectionOf(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(groupId, roleId);
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
                .filter(record -> record.getValue().getUpdateId().equals(updateId))
                .exists())
        .isTrue();
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy")
        .setPolicyVersion(2)
        .setBasePolicyVersion(1)
        .setSeqNo(7)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId).setName(roleId);
  }

  /**
   * Fails the update of three chunks with its second chunk, changed by the modifier, and checks
   * that the failure explains why.
   */
  private static void assertFailsWithAChunkThatHas(
      final UnaryOperator<PolicyUpdateRecord> modifier, final String... reasonParts) {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(ENGINE, chunk(updateId, 0, 3).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    final var invalidChunk = modifier.apply(chunk(updateId, 1, 3));
    apply(ENGINE, invalidChunk);

    // then
    final var failed =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(failed.getValue().getChunkIndex()).isEqualTo(invalidChunk.getChunkIndex());
    assertThat(failed.getValue().getErrorMessage()).contains(updateId).contains(reasonParts);
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).isEqualTo(failed.getValue().getErrorMessage());

    // and the failed update is not applied when its remaining chunk arrives
    apply(ENGINE, chunk(updateId, 2, 3));
    assertThat(recordsUntilAMarkerUpdateIsApplied())
        .noneMatch(record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPLIED, updateId))
        .noneMatch(
            record ->
                record.getIntent() == RoleIntent.CREATED
                    && ((RoleRecordValue) record.getValue()).getRoleId().equals(roleId));
  }

  /**
   * Applies an update of one chunk and returns all records up to its application. Since commands
   * are processed in order, the records contain everything that the earlier commands caused.
   */
  private static List<Record<?>> recordsUntilAMarkerUpdateIsApplied() {
    final var markerId = "update-" + uid();
    apply(ENGINE, chunk(markerId, 0, 1));
    return RecordingExporter.records()
        .limit(record -> isPolicyUpdate(record, PolicyUpdateIntent.CHUNK_APPLIED, markerId))
        .<Record<?>>map(record -> record)
        .toList();
  }

  private static boolean isPolicyUpdate(
      final Record<?> record, final PolicyUpdateIntent intent, final String updateId) {
    return record.getIntent() == intent
        && ((PolicyUpdateRecordValue) record.getValue()).getUpdateId().equals(updateId);
  }

  private static Record<PolicyUpdateRecordValue> appendRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  private static Record<PolicyUpdateRecordValue> appendedRecord(
      final String updateId, final int chunkIndex) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .filter(record -> record.getValue().getChunkIndex() == chunkIndex)
        .getFirst();
  }
}
