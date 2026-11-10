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
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Policy updates of several chunks. Each test gets its own engine, because a test may leave an
 * update open, and an open update blocks every other one.
 */
public final class PolicyUpdateMultiChunkTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyTheUpdateOnlyAfterItsLastChunk() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    apply(engine, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // when
    apply(engine, chunk(updateId, 1, 2));

    // then
    final var lastChunkAppended = appendedRecord(updateId, 1);
    final var roleCreated =
        RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).getFirst();
    assertThat(roleCreated.getPosition()).isGreaterThan(lastChunkAppended.getPosition());
  }

  @Test
  public void shouldApplyChunksInTheirOrderEvenIfTheyArriveInAnotherOrder() {
    // given
    final var updateId = "update-" + uid();
    final var roleIds = List.of("role-0-" + uid(), "role-1-" + uid(), "role-2-" + uid());

    // when
    for (final int chunkIndex : List.of(2, 0, 1)) {
      apply(
          engine,
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
    apply(
        engine,
        chunk(updateId, 1, 2)
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.ROLE)
                        .setTargetId(roleId)
                        .setMemberType(EntityType.CLIENT)
                        .setMemberId(clientId))));

    // when
    apply(engine, chunk(updateId, 0, 2).setRoles(List.<RoleRecordValue>of(role(roleId))));

    // then
    final var entityAdded =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED).withRoleId(roleId).getFirst();
    assertThat(entityAdded.getValue().getEntityId()).isEqualTo(clientId);
  }

  @Test
  public void shouldAcceptTheNextUpdateOnceTheOpenOneIsApplied() {
    // given
    final var firstUpdateId = "update-" + uid();
    apply(engine, chunk(firstUpdateId, 0, 2));
    apply(engine, chunk(firstUpdateId, 1, 2));
    appliedRecord(firstUpdateId);

    // when
    final var secondUpdateId = "update-" + uid();
    apply(engine, chunk(secondUpdateId, 0, 1));

    // then
    assertThat(appliedRecord(secondUpdateId).getValue().getChunkIndex()).isZero();
  }

  @Test
  public void shouldRejectAChunkOfAnotherUpdateWhileOneIsOpen() {
    // given
    final var openUpdateId = "update-" + uid();
    apply(engine, chunk(openUpdateId, 0, 2));

    // when
    final var otherUpdateId = "update-" + uid();
    apply(engine, chunk(otherUpdateId, 0, 1));

    // then
    final var rejection = appendRejection(otherUpdateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(openUpdateId, "still open");
  }

  @Test
  public void shouldRejectADuplicateChunk() {
    // given
    final var updateId = "update-" + uid();
    apply(engine, chunk(updateId, 0, 2));

    // when
    apply(engine, chunk(updateId, 0, 2));

    // then
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
    assertThat(rejection.getRejectionReason()).contains("already buffered");
  }

  @Test
  public void shouldRejectAChunkThatDoesNotMatchTheChunksBefore() {
    // given
    final var updateId = "update-" + uid();
    apply(engine, chunk(updateId, 0, 2));

    // when
    apply(engine, chunk(updateId, 1, 2).setPolicyVersion(3));

    // then
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("to match the chunks before");
  }

  @Test
  public void shouldRejectAChunkOutsideItsUpdate() {
    // given
    final var updateId = "update-" + uid();

    // when
    apply(engine, chunk(updateId, 2, 2));

    // then
    final var rejection = appendRejection(updateId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("one of its 2 chunks");
  }

  @Test
  public void shouldRejectApplyingAnUpdateBeforeAllItsChunksAreBuffered() {
    // given
    final var updateId = "update-" + uid();
    apply(engine, chunk(updateId, 0, 2));

    // when
    engine.writeRecords(
        RecordToWrite.commandWithoutClaims()
            .policyUpdate(PolicyUpdateIntent.APPLY_CHUNK, chunk(updateId, 0, 2)));

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
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

  private static Record<PolicyUpdateRecordValue> appendedRecord(
      final String updateId, final int chunkIndex) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .filter(record -> record.getValue().getChunkIndex() == chunkIndex)
        .getFirst();
  }

  private static Record<PolicyUpdateRecordValue> appendRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }
}
