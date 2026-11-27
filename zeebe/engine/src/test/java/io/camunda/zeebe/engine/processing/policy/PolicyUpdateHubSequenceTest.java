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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Every policy update is one Hub event. Once its version is committed or it failed, its Hub
 * sequence is finished, and the engine rejects any update at or below that sequence, so a chunk
 * that a previous leader wrote is not handled again. Each test gets its own engine, as the finished
 * sequence only grows.
 */
public final class PolicyUpdateHubSequenceTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectAnUpdateWithTheSequenceOfACommittedOne() {
    // given
    commit(5);

    // when
    final var again = update(5, 1);
    apply(engine, again);

    // then
    final var rejection = appendRejection(again.getUpdateId());
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
    assertThat(rejection.getRejectionReason())
        .contains("Hub sequence after 5", "already applied", "Hub sequence 5");
    assertThat(recordsOfUntilTheNextUpdateIsCommitted(again.getUpdateId(), 6))
        .noneMatch(record -> record.getIntent() == PolicyUpdateIntent.CHUNK_APPENDED);
  }

  @Test
  public void shouldRejectAnUpdateWithALowerSequence() {
    // given
    commit(5);

    // when
    final var earlier = update(3, 1);
    apply(engine, earlier);

    // then
    assertThat(appendRejection(earlier.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldRejectAnUpdateWithTheSequenceOfAFailedOne() {
    // given an update of sequence 5 that failed, as its second chunk has another policy version
    final var failed = update(5, 2);
    fail(failed);

    // when its first chunk arrives again
    apply(engine, copy(failed));

    // then it does not open the update again
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
            .onlyCommandRejections()
            .filter(record -> record.getValue().getUpdateId().equals(failed.getUpdateId()))
            .filter(record -> record.getRejectionType() == RejectionType.ALREADY_EXISTS)
            .getFirst();
    assertThat(rejection.getRejectionReason())
        .contains("Hub sequence after 5", "already rejected", "Hub sequence 5");
  }

  @Test
  public void shouldApplyAnUpdateWithTheSequenceAfterAFailedOne() {
    // given
    fail(update(5, 2));

    // when
    final var next = update(6, 1);
    apply(engine, next);

    // then
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
                .filter(record -> record.getValue().getUpdateId().equals(next.getUpdateId()))
                .getFirst()
                .getValue()
                .getSeqNo())
        .isEqualTo(6L);
  }

  @Test
  public void shouldRejectAnUpdateWithACommittedSequenceAfterARestart() {
    // given
    engine.snapshot();
    commit(5);

    // when the engine replays the commit
    engine.stop();
    engine.start();
    final var again = update(5, 1);
    apply(engine, again);

    // then
    assertThat(appendRejection(again.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldRejectAnUpdateWithACommittedSequenceAfterARestartFromASnapshot() {
    // given
    commit(5);
    engine.snapshot();

    // when the engine recovers the commit from the snapshot
    engine.stop();
    engine.start();
    final var again = update(5, 1);
    apply(engine, again);

    // then
    assertThat(appendRejection(again.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  /** Applies an update of one chunk with the sequence, and waits until its version is committed. */
  private void commit(final long seqNo) {
    final var update = update(seqNo, 1);
    apply(engine, update);
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
        .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
        .await();
  }

  /** Fails the update, by sending its second chunk with another policy version. */
  private void fail(final PolicyUpdateRecord update) {
    apply(engine, update);
    apply(engine, copy(update).setChunkIndex(1).setPolicyVersion(9));
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
        .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
        .await();
  }

  /**
   * The records of the update, up to the commit of a marker update with the given sequence. Since
   * commands are processed in order, they contain everything the update's earlier commands caused.
   */
  private List<Record<PolicyUpdateRecordValue>> recordsOfUntilTheNextUpdateIsCommitted(
      final String updateId, final long markerSeqNo) {
    final var marker = update(markerSeqNo, 1);
    apply(engine, marker);
    return RecordingExporter.policyUpdateRecords()
        .limit(
            record ->
                record.getIntent() == PolicyUpdateIntent.VERSION_COMMITTED
                    && record.getValue().getUpdateId().equals(marker.getUpdateId()))
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .toList();
  }

  private static Record<PolicyUpdateRecordValue> appendRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
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

  private static PolicyUpdateRecord copy(final PolicyUpdateRecord chunk) {
    final var copy = new PolicyUpdateRecord();
    copy.wrap(BufferUtil.createCopy(chunk));
    return copy;
  }
}
