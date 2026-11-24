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
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public final class HubPolicyUpdateSchedulerTest {

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final FakeHubPolicyClient hub = new FakeHubPolicyClient();

  @Rule public final EngineRule engine = EngineRule.singlePartition().withHubPolicyClient(hub);

  @Test
  public void shouldCommitAnUpdateFromHubAndPollAfterIt() {
    // given
    final var updateId = "update-" + uid();
    awaitPolls(0L);

    // when
    hub.completePoll(update(updateId, 1, 1));

    // then
    assertThat(committed(updateId).getValue().getSeqNo()).isEqualTo(1);
    awaitPolls(0L, 1L);
  }

  @Test
  public void shouldWriteTheChunksOfAnUpdateInIndexOrder() {
    // given
    final var updateId = "update-" + uid();
    awaitPolls(0L);

    // when
    hub.completePoll(update(updateId, 1, 3));

    // then
    committed(updateId);
    assertThat(
            RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
                .filter(record -> record.getValue().getUpdateId().equals(updateId))
                .limit(3))
        .extracting(record -> record.getValue().getChunkIndex())
        .containsExactly(0, 1, 2);
  }

  @Test
  public void shouldSendTheNextUpdateOnlyOnceTheOpenOneIsCommitted() {
    // given an update that is open while Hub answers the poll
    final var openUpdateId = "update-" + uid();
    final var nextUpdateId = "update-" + uid();
    awaitPolls(0L);
    apply(engine, chunk(openUpdateId, 1, 0, 2));
    hub.completePoll(update(nextUpdateId, 2, 1));
    passCycles(3);

    // when
    apply(engine, chunk(openUpdateId, 1, 1, 2));

    // then
    final var openCommitted = committed(openUpdateId);
    final var nextAppended =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
            .filter(record -> record.getValue().getUpdateId().equals(nextUpdateId))
            .getFirst();
    assertThat(nextAppended.getPosition()).isGreaterThan(openCommitted.getPosition());
    assertThat(committed(nextUpdateId).getValue().getSeqNo()).isEqualTo(2);
  }

  @Test
  public void shouldDropAnUpdateWhoseSequenceIsAlreadyCommitted() {
    // given a newer update that is committed while Hub answers with an older one
    final var newerUpdateId = "update-" + uid();
    final var olderUpdateId = "update-" + uid();
    awaitPolls(0L);
    apply(engine, chunk(newerUpdateId, 5, 0, 1));
    committed(newerUpdateId);

    // when
    hub.completePoll(update(olderUpdateId, 3, 1));

    // then the next poll starts after the newer update; a marker update sent from there shows that
    // nothing of the older update was written before it
    awaitPolls(0L, 5L);
    final var markerUpdateId = "update-" + uid();
    hub.completePoll(update(markerUpdateId, 6, 1));
    committed(markerUpdateId);
    assertThat(RecordingExporter.getRecords())
        .noneMatch(
            record ->
                record.getValue() instanceof final PolicyUpdateRecordValue value
                    && value.getUpdateId().equals(olderUpdateId));
  }

  @Test
  public void shouldPollAgainAfterAFailedPoll() {
    // given
    awaitPolls(0L);

    // when
    hub.failPoll(new RuntimeException("Hub is not reachable"));

    // then
    awaitPolls(0L, 0L, HubPolicyUpdateScheduler.RETRY_DELAY);
  }

  @Test
  public void shouldPollFromTheSameSequenceAgainWhenTheUpdateFails() {
    // given an update whose second chunk has another policy version, which fails it
    final var updateId = "update-" + uid();
    awaitPolls(0L);

    // when
    hub.completePoll(chunk(updateId, 1, 0, 2), chunk(updateId, 1, 1, 2).setPolicyVersion(3));

    // then
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.FAILED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
    awaitPolls(0L, 0L);
  }

  @Test
  public void shouldPollAgainWhenTheUpdateIsNeverOpened() {
    // given an update that starts with chunk 1, which the engine rejects without opening it
    final var updateId = "update-" + uid();
    awaitPolls(0L);

    // when
    hub.completePoll(chunk(updateId, 1, 1, 2));

    // then
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPEND_CHUNK)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
    awaitPolls(0L, 0L, HubPolicyUpdateScheduler.SENT_UPDATE_TIMEOUT);
  }

  private Record<PolicyUpdateRecordValue> committed(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  private void awaitPolls(final Long... afterSeqNos) {
    awaitPolls(List.of(afterSeqNos), HubPolicyUpdateScheduler.WAIT_INTERVAL);
  }

  private void awaitPolls(final long first, final long second, final Duration step) {
    awaitPolls(List.of(first, second), step);
  }

  /** Moves the clock until Hub was polled from exactly these sequences, in this order. */
  private void awaitPolls(final List<Long> afterSeqNos, final Duration step) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              engine.increaseTime(step);
              assertThat(hub.polls).containsExactlyElementsOf(afterSeqNos);
            });
  }

  private void passCycles(final int cycles) {
    IntStream.range(0, cycles)
        .forEach(cycle -> engine.increaseTime(HubPolicyUpdateScheduler.WAIT_INTERVAL));
  }

  private static PolicyUpdateRecord[] update(
      final String updateId, final long seqNo, final int chunkCount) {
    return IntStream.range(0, chunkCount)
        .mapToObj(index -> chunk(updateId, seqNo, index, chunkCount))
        .toArray(PolicyUpdateRecord[]::new);
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final long seqNo, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + updateId)
        .setPolicyVersion(seqNo)
        .setBasePolicyVersion(seqNo - 1)
        .setSeqNo(seqNo)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }

  /**
   * Records every poll. Each poll returns a future that the test completes; until then it is
   * pending, like a long poll that Hub keeps open.
   */
  private static final class FakeHubPolicyClient implements HubPolicyClient {

    private final List<Long> polls = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<CompletableFuture<List<PolicyUpdateRecord>>> pending =
        new ConcurrentLinkedQueue<>();

    @Override
    public CompletableFuture<List<PolicyUpdateRecord>> poll(final long afterSeqNo) {
      final var result = new CompletableFuture<List<PolicyUpdateRecord>>();
      pending.add(result);
      polls.add(afterSeqNo);
      return result;
    }

    void completePoll(final PolicyUpdateRecord... chunks) {
      nextPending().complete(new ArrayList<>(List.of(chunks)));
    }

    void failPoll(final Throwable error) {
      nextPending().completeExceptionally(error);
    }

    private CompletableFuture<List<PolicyUpdateRecord>> nextPending() {
      final var next = pending.poll();
      assertThat(next).describedAs("a poll that Hub has not answered yet").isNotNull();
      return next;
    }
  }
}
