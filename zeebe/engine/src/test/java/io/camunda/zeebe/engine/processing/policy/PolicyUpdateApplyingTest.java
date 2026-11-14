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
 * <p>To hold an update in the apply phase, its last chunk has an entry that cannot be applied: its
 * first chunk is applied, and the last one is rejected, so the update stays open with all its
 * chunks buffered. The held update blocks every later update, so each test gets its own engine.
 */
public final class PolicyUpdateApplyingTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String updateId = "update-" + uid();

  @Test
  public void shouldRejectAChunkThatIsReceivedAgainWithoutFailingTheUpdate() {
    // given
    holdUpdateWhileApplying();

    // when
    apply(engine, chunk(0));

    // then
    assertRejectedWithoutFailingTheUpdate(0);
  }

  @Test
  public void shouldRejectAChunkWithOtherIdentifiersWithoutFailingTheUpdate() {
    // given
    holdUpdateWhileApplying();

    // when
    apply(engine, chunk(1).setSeqNo(8));

    // then
    assertRejectedWithoutFailingTheUpdate(1);
  }

  @Test
  public void shouldRejectAChunkWithAnIndexOutOfRangeWithoutFailingTheUpdate() {
    // given
    holdUpdateWhileApplying();

    // when
    apply(engine, chunk(2));

    // then
    assertRejectedWithoutFailingTheUpdate(2);
  }

  @Test
  public void shouldRejectAChunkWithAnEntryWithoutEntityType() {
    // given
    final var id = "entity-" + uid();

    // when
    apply(engine, chunk(0).setChunkCount(1).setDeletes(List.of(deleteWithoutEntityType(id))));

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(updateId))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(id, "not specified");
  }

  /** Applies the first of two chunks and leaves the update open with both chunks buffered. */
  private void holdUpdateWhileApplying() {
    final var roleId = "role-" + uid();
    apply(engine, chunk(0).setRoles(List.<RoleRecordValue>of(role(roleId))));
    apply(engine, chunk(1).setDeletes(List.of(deleteWithoutEntityType("entity-" + uid()))));

    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY_CHUNK)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .await();
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
