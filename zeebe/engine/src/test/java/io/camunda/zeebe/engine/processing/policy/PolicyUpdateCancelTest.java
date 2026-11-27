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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * A new leader cancels the update that a previous leader left open while it still receives chunks,
 * so that the Hub event can be tried again under a new update ID. An update that is being applied
 * is not canceled; see {@link PolicyUpdateApplyingTest}. Each test gets its own engine, as only one
 * update is open at a time.
 */
public final class PolicyUpdateCancelTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCancelAnUpdateThatStillReceivesChunks() {
    // given an update of which only the first of two chunks is buffered
    final var update = update(5, 2);
    apply(engine, update);

    // when
    cancel(update, "a new leader took over");

    // then
    final var canceled =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CANCELED)
            .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
            .getFirst()
            .getValue();
    assertThat(canceled.getSeqNo()).isEqualTo(5L);
    assertThat(canceled.getErrorMessage()).isEqualTo("a new leader took over");
  }

  @Test
  public void shouldApplyTheSameHubEventAgainAfterItIsCanceled() {
    // given
    final var update = update(5, 2);
    apply(engine, update);
    cancel(update, "a new leader took over");

    // when the Hub event is tried again under a new update ID
    final var again = update(5, 1);
    apply(engine, again);

    // then
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
                .filter(record -> record.getValue().getUpdateId().equals(again.getUpdateId()))
                .getFirst()
                .getValue()
                .getSeqNo())
        .isEqualTo(5L);
  }

  @Test
  public void shouldRejectCancelingAnUpdateThatIsNotOpen() {
    // when
    final var update = update(5, 2);
    cancel(update, "a new leader took over");

    // then
    assertThat(cancelRejection(update.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldNotCancelAnotherOpenUpdate() {
    // given
    final var open = update(5, 2);
    apply(engine, open);

    // when
    final var other = update(5, 2);
    cancel(other, "a new leader took over");

    // then the open update still receives its last chunk
    assertThat(cancelRejection(other.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);
    apply(
        engine,
        update(5, 2)
            .setUpdateId(open.getUpdateId())
            .setPolicyId(open.getPolicyId())
            .setChunkIndex(1));
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
                .filter(record -> record.getValue().getUpdateId().equals(open.getUpdateId()))
                .exists())
        .isTrue();
  }

  private void cancel(final PolicyUpdateRecord update, final String reason) {
    engine.writeRecords(cancelCommand(update, reason));
  }

  static RecordToWrite cancelCommand(final PolicyUpdateRecord update, final String reason) {
    final var cancel =
        new PolicyUpdateRecord()
            .setPolicyId(update.getPolicyId())
            .setPolicyVersion(update.getPolicyVersion())
            .setBasePolicyVersion(update.getBasePolicyVersion())
            .setSeqNo(update.getSeqNo())
            .setUpdateId(update.getUpdateId())
            .setChunkCount(update.getChunkCount())
            .setErrorMessage(reason);
    return RecordToWrite.commandWithoutClaims().policyUpdate(PolicyUpdateIntent.CANCEL, cancel);
  }

  static Record<PolicyUpdateRecordValue> cancelRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CANCEL)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  /** The first chunk of a new update with the sequence. */
  private static PolicyUpdateRecord update(final long seqNo, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + uid())
        .setPolicyVersion(seqNo)
        .setBasePolicyVersion(seqNo - 1)
        .setSeqNo(seqNo)
        .setUpdateId("update-" + uid())
        .setChunkIndex(0)
        .setChunkCount(chunkCount);
  }
}
