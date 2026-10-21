/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.leader.LogArchiveEventPlaneClient.Answer;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class LogArchiveAnnouncerTest {

  private static final int PARTITION = 1;
  private static final String TIMELINE = "tl-0";

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final FakeStore store = new FakeStore();
  private final FakeClient client = new FakeClient();
  private LogArchiveAnnouncer announcer;

  @BeforeEach
  void setUp() {
    announcer = start(3);
  }

  @Test
  void shouldAnnounceNothingBeforeRecoveryCheck() {
    // given - no onRecoveryChecked yet

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).isEmpty();
  }

  @Test
  void shouldAnnounceInOrderAndAdvanceWithNextSeq() {
    // given
    recovered(TIMELINE, 0);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).extracting(Request::seqs).containsExactly(List.of(1L), List.of(2L));
    assertThat(client.requests).allSatisfy(r -> assertThat(r.timeline).isEqualTo(TIMELINE));
  }

  @Test
  void shouldExposeTheSeqTheEventPlaneConfirmed() {
    // given
    recovered(TIMELINE, 0);
    assertThat(announcer.confirmedNextSeq(TIMELINE)).isEqualTo(-1);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // then
    assertThat(announcer.confirmedNextSeq(TIMELINE)).isEqualTo(3);
    assertThat(announcer.confirmedNextSeq("tl-other")).isEqualTo(-1);
  }

  @Test
  void shouldNotConfirmAnythingWhileTheEventPlaneFails() {
    // given
    recovered(TIMELINE, 0);
    client.script = (r, n) -> new Answer(503, -1);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();

    // then
    assertThat(announcer.confirmedNextSeq(TIMELINE)).isEqualTo(-1);
  }

  @Test
  void shouldSendBatchesOfAtMostMaxBatchEntriesInOrder() {
    // given - the event plane is not reachable while the entries pile up
    recovered(TIMELINE, 0);
    client.hold = true;
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();
    for (long seq = 2; seq <= 7; seq++) {
      announcer.published(TIMELINE, seq, entry(seq));
    }
    scheduler.workUntilDone();

    // when
    client.hold = false;
    client.releaseHeld(new Answer(200, 2));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests)
        .extracting(Request::seqs)
        .containsExactly(List.of(1L), List.of(2L, 3L, 4L), List.of(5L, 6L, 7L));
  }

  @Test
  void shouldNeverSendBeyondHighestPublishedSeq() {
    // given
    recovered(TIMELINE, 0);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    announcer.published(TIMELINE, 2, entry(2));
    announcer.published(TIMELINE, 3, entry(3));
    scheduler.workUntilDone();

    // then - every seq is sent once and nothing above seq 3
    assertThat(client.requests.stream().flatMap(r -> r.seqs().stream()))
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldBackOffWhenEventPlaneAppliesButDoesNotAdvance() {
    // given
    recovered(TIMELINE, 0);
    client.script = (r, n) -> n == 1 ? new Answer(200, 1) : new Answer(200, 2);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).hasSize(1);

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).hasSize(2);
  }

  @Test
  void shouldResumeFromLowerNextSeqOf409UsingTheBucket() {
    // given - seqs 1..5 are in the bucket, the leader starts with lastPublishedSeq 5
    for (long seq = 1; seq <= 5; seq++) {
      store.put(TIMELINE, seq);
    }
    client.script =
        (r, n) ->
            n == 1 ? new Answer(409, 2) : new Answer(200, r.seqs().get(r.seqs().size() - 1) + 1);

    // when
    recovered(TIMELINE, 5);

    // then - the probe is the last published seq, the 409 sends the rest from the bucket
    assertThat(client.requests)
        .extracting(Request::seqs)
        .containsExactly(List.of(5L), List.of(2L, 3L, 4L), List.of(5L));
    assertThat(client.requests.get(1).bodies.get(0)).isEqualTo(entry(2));
    assertThat(store.reads).containsExactly(5L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldProbeWithLastPublishedSeqFromBucketOnStartWhenNothingIsBuffered() {
    // given
    store.put(TIMELINE, 7);
    client.script = (r, n) -> new Answer(200, 8);

    // when
    recovered(TIMELINE, 7);

    // then
    assertThat(client.requests).extracting(Request::seqs).containsExactly(List.of(7L));
    assertThat(client.requests.get(0).bodies.get(0)).isEqualTo(entry(7));
  }

  @Test
  void shouldContinueFromHigherNextSeqOf409() {
    // given
    recovered(TIMELINE, 0);
    client.hold = true;
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();
    for (long seq = 2; seq <= 6; seq++) {
      announcer.published(TIMELINE, seq, entry(seq));
    }
    scheduler.workUntilDone();

    // when - the event plane is already at 5
    client.hold = false;
    client.releaseHeld(new Answer(409, 5));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests)
        .extracting(Request::seqs)
        .containsExactly(List.of(1L), List.of(5L, 6L));
  }

  @Test
  void shouldWaitWhenEventPlaneIsAheadOfPublishedSeqs() {
    // given
    recovered(TIMELINE, 0);
    client.script = (r, n) -> new Answer(409, 50);

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // then - one request, then nothing until the published seqs reach 50
    assertThat(client.requests).hasSize(1);
  }

  @Test
  void shouldRetryWithBackoffOnErrors() {
    // given
    recovered(TIMELINE, 0);
    client.script =
        (r, n) -> {
          if (n == 1) {
            throw new IllegalStateException("connection refused");
          }
          return n == 2 ? new Answer(503, -1) : new Answer(200, 2);
        };

    // when
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).hasSize(1);

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then - second attempt failed with 503, the next delay doubled
    assertThat(client.requests).hasSize(2);
    scheduler.updateClock(Duration.ofMillis(200));
    scheduler.workUntilDone();
    assertThat(client.requests).hasSize(3);
    assertThat(client.requests).extracting(Request::seqs).containsOnly(List.of(1L));
  }

  @Test
  void shouldNotSendAgainWhilePublishedCallsArriveDuringBackoff() {
    // given
    recovered(TIMELINE, 0);
    client.script = (r, n) -> n == 1 ? new Answer(503, -1) : new Answer(200, 3);
    announcer.published(TIMELINE, 1, entry(1));
    scheduler.workUntilDone();

    // when
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).hasSize(1);
  }

  @Test
  void shouldNotSkipSeqOn400() {
    // given
    recovered(TIMELINE, 0);
    client.script = (r, n) -> n <= 2 ? new Answer(400, -1) : new Answer(200, 3);
    announcer.published(TIMELINE, 1, entry(1));
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofMillis(200));
    scheduler.workUntilDone();

    // then - the same batch again, never past it
    assertThat(client.requests).hasSize(3);
    assertThat(client.requests).allSatisfy(r -> assertThat(r.firstSeq()).isEqualTo(1L));
  }

  @Test
  void shouldRetryWhenBucketReadFails() {
    // given
    store.put(TIMELINE, 1);
    store.failures = 1;
    recovered(TIMELINE, 1);
    client.script = (r, n) -> new Answer(200, 2);

    // then - nothing sent yet
    assertThat(client.requests).isEmpty();

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).extracting(Request::seqs).containsExactly(List.of(1L));
  }

  @Test
  void shouldNotAnnounceDivergedTimeline() {
    // given - the bucket is ahead of the state
    announcer.onRecoveryChecked(
        new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(3).setLastPublishedSeq(2), true);
    scheduler.workUntilDone();

    // when
    announcer.published(TIMELINE, 3, entry(3));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).isEmpty();
  }

  @Test
  void shouldAnnounceForkOfDivergedTimeline() {
    // given
    announcer.onRecoveryChecked(
        new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(3).setLastPublishedSeq(2), true);

    // when
    announcer.published(TIMELINE + ".f3", 1, entry(1));
    scheduler.workUntilDone();

    // then
    assertThat(client.requests).hasSize(1);
    assertThat(client.requests.get(0).timeline).isEqualTo(TIMELINE + ".f3");
  }

  @Test
  void shouldAnnounceOnlyCurrentTimelineAfterSwitch() {
    // given - timeline tl-0 is behind at the event plane
    recovered(TIMELINE, 0);
    client.hold = true;
    announcer.published(TIMELINE, 1, entry(1));
    announcer.published(TIMELINE, 2, entry(2));
    scheduler.workUntilDone();

    // when - the timeline changes while the first request is outstanding
    announcer.published("tl-1", 1, entry(1));
    scheduler.workUntilDone();
    client.hold = false;
    client.releaseHeld(new Answer(200, 3));
    scheduler.workUntilDone();

    // then - the answer for the old timeline is ignored, nothing more is sent for it
    assertThat(client.requests).extracting(r -> r.timeline).containsExactly("tl-0", "tl-1");
    assertThat(client.requests.get(1).seqs()).containsExactly(1L);
  }

  private void recovered(final String timeline, final long lastPublishedSeq) {
    announcer.onRecoveryChecked(
        new LogArchiveMeta()
            .setTimelineId(timeline)
            .setNextSeq(lastPublishedSeq + 1)
            .setLastPublishedSeq(lastPublishedSeq),
        false);
    scheduler.workUntilDone();
  }

  private LogArchiveAnnouncer start(final int maxBatchEntries) {
    final var started = new LogArchiveAnnouncer(PARTITION, store, client, maxBatchEntries);
    scheduler.submitActor(started);
    scheduler.workUntilDone();
    return started;
  }

  private static byte[] entry(final long seq) {
    return ("{\"seq\":" + seq + "}").getBytes(StandardCharsets.UTF_8);
  }

  private record Request(String timeline, List<byte[]> bodies) {
    List<Long> seqs() {
      return bodies.stream()
          .map(b -> Long.parseLong(new String(b, StandardCharsets.UTF_8).replaceAll("\\D", "")))
          .toList();
    }

    long firstSeq() {
      return seqs().get(0);
    }
  }

  private static final class FakeClient implements LogArchiveEventPlaneClient {
    private final List<Request> requests = new ArrayList<>();
    private final List<CompletableFuture<Answer>> held = new ArrayList<>();
    // default: apply everything, expect the seq after the last one sent
    private BiFunction<Request, Integer, Answer> script =
        (r, n) -> new Answer(200, r.seqs().get(r.seqs().size() - 1) + 1);
    private boolean hold;

    @Override
    public CompletableFuture<Answer> send(
        final int partitionId, final String timelineId, final List<byte[]> entries) {
      assertThat(partitionId).isEqualTo(PARTITION);
      final var request = new Request(timelineId, List.copyOf(entries));
      requests.add(request);
      if (hold) {
        final var future = new CompletableFuture<Answer>();
        held.add(future);
        return future;
      }
      return CompletableFuture.completedFuture(script.apply(request, requests.size()));
    }

    private void releaseHeld(final Answer answer) {
      final var futures = List.copyOf(held);
      held.clear();
      futures.forEach(f -> f.complete(answer));
    }
  }

  private static final class FakeStore implements LogArchiveStore {
    private final Map<String, byte[]> objects = new HashMap<>();
    private final List<Long> reads = new ArrayList<>();
    private int failures;

    private void put(final String timeline, final long seq) {
      objects.put(
          LogArchiveKeys.partitionPrefix(timeline, PARTITION) + LogArchiveKeys.commitKey(seq),
          entry(seq));
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      if (failures > 0) {
        failures--;
        return CompletableFuture.failedFuture(new RuntimeException("boom"));
      }
      reads.add(LogArchiveKeys.seqFromCommitKey(key));
      return CompletableFuture.completedFuture(Optional.ofNullable(objects.get(key)));
    }

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<byte[]> getRange(
        final String key, final long offset, final int length) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> getToFile(final String key, final Path target) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<String>> list(final String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<String>> listChildPrefixes(final String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
      return CompletableFuture.completedFuture(null);
    }
  }
}
