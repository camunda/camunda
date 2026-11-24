/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Chunks that arrive while their update is being applied. Applying writes identity commands that
 * nothing can undo, so such a chunk is only rejected and the update never fails.
 *
 * <p>To receive a chunk while the update is being applied, the test writes it in one batch after
 * all chunks of the update: the last of them writes the command that applies the update, which
 * lands on the log after the test's chunk, since the engine processes one command per batch. Each
 * test gets its own engine, so the updates don't affect each other.
 */
public final class PolicyUpdateApplyingTest {

  // one command per batch, so the command that applies an update lands on the log after the
  // chunks that the test wrote in the same batch as the update's chunks
  @Rule public final EngineRule engine = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String updateId = "update-" + uid();

  @Test
  public void shouldRejectAChunkThatIsReceivedAgainWithoutFailingTheUpdate() {
    // when
    appendWhileApplying(chunk(0));

    // then
    assertRejectedWithoutFailingTheUpdate(0);
  }

  @Test
  public void shouldRejectAChunkWithOtherIdentifiersWithoutFailingTheUpdate() {
    // when
    appendWhileApplying(chunk(1).setSeqNo(8));

    // then
    assertRejectedWithoutFailingTheUpdate(1);
  }

  @Test
  public void shouldRejectAChunkWithAnIndexOutOfRangeWithoutFailingTheUpdate() {
    // when
    appendWhileApplying(chunk(2));

    // then
    assertRejectedWithoutFailingTheUpdate(2);
  }

  @Test
  public void shouldFailAnUpdateWithAnEntryWithoutEntityType() {
    // given
    final var id = "entity-" + uid();

    // when
    apply(engine, chunk(0).setChunkCount(1).setDeletes(List.of(deleteWithoutEntityType(id))));

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(id, "not specified");
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
                .filter(record -> record.getValue().getUpdateId().equals(updateId))
                .exists())
        .isTrue();
  }

  /**
   * Appends both chunks of an update and then the given chunk, in one batch, so the given chunk is
   * received after the update is complete and before it is applied.
   */
  private void appendWhileApplying(final PolicyUpdateRecord chunk) {
    final var roleId = "role-" + uid();
    engine.writeRecords(
        append(chunk(0).setRoles(List.<RoleRecordValue>of(role(roleId)))),
        append(chunk(1)),
        append(chunk));

    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
  }

  private static RecordToWrite append(final PolicyUpdateRecord chunk) {
    return RecordToWrite.commandWithoutClaims()
        .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk);
  }

  private void assertRejectedWithoutFailingTheUpdate(final int chunkIndex) {
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .filter(record -> record.getValue().getChunkIndex() == chunkIndex)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(updateId, "already being applied");

    // a failure would be written before the rejection
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() == rejection.getPosition())
                .toList())
        .noneMatch(
            record ->
                record.getIntent() == PolicyUpdateIntent.FAILED
                    && record.getRecordType() == RecordType.EVENT
                    && ((PolicyUpdateRecordValue) record.getValue())
                        .getUpdateId()
                        .equals(updateId));
  }

  private PolicyUpdateRecord chunk(final int chunkIndex) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy")
        .setPolicyVersion(2)
        .setBasePolicyVersion(1)
        .setSeqNo(7)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(2);
  }

  private static PolicyDeleteEntryValue deleteWithoutEntityType(final String id) {
    return new PolicyDeleteEntry().setEntityType(PolicyUpdateEntityType.UNSPECIFIED).setId(id);
  }

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId).setName(roleId);
  }
}
