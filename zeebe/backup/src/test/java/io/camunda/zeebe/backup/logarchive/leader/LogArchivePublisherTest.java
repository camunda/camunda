/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.backup.logarchive.model.LatestHint;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.processing.PublishRequest;
import io.camunda.zeebe.backup.logarchive.processing.SealRequest;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.Either;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentMatchers;

final class LogArchivePublisherTest {

  private static final int PARTITION = 1;
  private static final String TIMELINE = "tl-0";
  private static final String PREFIX = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final List<String> events = new ArrayList<>();
  private final List<IndexChunk> sealedChunks = new ArrayList<>();
  private final FakeStore store = new FakeStore(events);
  private final LogStreamWriter writer = mock(LogStreamWriter.class);
  private final List<RecoveryCheck> recoveryChecks = new ArrayList<>();
  private int failWrites;
  private int failHandoverWrites;
  private byte[] handoverContent;
  private LogArchivePublisher publisher;

  @BeforeEach
  void setUp() {
    when(writer.tryWrite(any(), ArgumentMatchers.<LogAppendEntry>any()))
        .thenAnswer(
            invocation -> {
              if (failWrites > 0) {
                failWrites--;
                events.add("WRITE-FAILED");
                return Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
              }
              final LogAppendEntry entry = invocation.getArgument(1);
              final var intent = (LogArchiveIntent) entry.recordMetadata().getIntent();
              final var value = (LogArchiveRecord) entry.recordValue();
              if (intent == LogArchiveIntent.CONFIRM_PUBLISHED) {
                events.add("CONFIRM " + value.getPublishedSeq());
                assertThat(value.getTimelineId()).isEqualTo(TIMELINE);
              } else {
                events.add("SEAL " + value.getChunk().getChunkNo());
                assertThat(value.getTimelineId()).isEqualTo(TIMELINE);
                sealedChunks.add(new IndexChunk().copy(value.getChunk()));
              }
              return Either.right(1L);
            });
    publisher = startPublisher();
    // nothing in the bucket yet, so nothing is ahead of the state
    publisher.onRecovered(new LogArchiveMeta());
    scheduler.workUntilDone();
  }

  @Test
  void shouldNotPublishBeforeRecovery() {
    // given
    final var fresh = startPublisher();

    // when
    fresh.publish(commit(1));
    scheduler.workUntilDone();

    // then
    assertThat(events).isEmpty();
  }

  @Test
  void shouldRepublishWhenBucketIsNotAheadOfState() {
    // given - the bucket holds commits 1..3, the state continues at seq 4
    final var fresh = startPublisher();
    store.seed(PREFIX + LogArchiveKeys.commitKey(1), PREFIX + LogArchiveKeys.commitKey(3));
    final var meta = new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(4);

    // when
    fresh.onRecovered(meta);
    fresh.publish(commit(3));
    scheduler.workUntilDone();

    // then
    assertThat(store.getAttempts).isEqualTo(1);
    assertThat(recoveryChecks).containsExactly(new RecoveryCheck(TIMELINE, 4, false));
    assertThat(events).containsExactly("PUT " + PREFIX + LogArchiveKeys.commitKey(3));
  }

  @Test
  void shouldPublishNothingToTimelineWhenBucketIsAheadOfState() {
    // given - the state was rolled back: it continues at seq 3, but the bucket holds seq 3 already
    final var fresh = startPublisher();
    store.seed(PREFIX + LogArchiveKeys.commitKey(3), PREFIX + LogArchiveKeys.commitKey(4));
    final var meta = new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(3);

    // when - the processor republishes its outbox and a root, and later publishes another event
    fresh.onRecovered(meta);
    fresh.publish(commit(1));
    fresh.publish(commit(2));
    fresh.publish(new PublishRequest(TIMELINE, 2, null, bytes("root2"), seal(chunk(1))));
    scheduler.workUntilDone();
    fresh.publish(commit(3));
    scheduler.workUntilDone();

    // then
    assertThat(recoveryChecks).containsExactly(new RecoveryCheck(TIMELINE, 3, true));
    assertThat(events).isEmpty();
  }

  @Test
  void shouldPublishForkedTimelineAfterDivergence() {
    // given
    final var fresh = startPublisher();
    store.seed(PREFIX + LogArchiveKeys.commitKey(3));
    fresh.onRecovered(new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(3));
    scheduler.workUntilDone();

    // when - the archiver forked a new timeline, whose TIMELINE_STARTED takes seq 1
    final var fork = "tl-0.f3";
    fresh.publish(new PublishRequest(fork, 1, bytes("c1"), null, null));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + LogArchiveKeys.partitionPrefix(fork, PARTITION) + LogArchiveKeys.commitKey(1));
  }

  @Test
  void shouldRetryRecoveryCheckAndNotPublishBeforeItSucceeded() {
    // given
    final var fresh = startPublisher();
    store.failNextGets(2);
    final var meta = new LogArchiveMeta().setTimelineId(TIMELINE).setNextSeq(2);

    // when
    fresh.onRecovered(meta);
    fresh.publish(commit(1));
    scheduler.workUntilDone();

    // then - nothing is published while the check fails
    assertThat(events).isEmpty();
    assertThat(recoveryChecks).isEmpty();

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();
    assertThat(events).isEmpty();
    scheduler.updateClock(Duration.ofMillis(200));
    scheduler.workUntilDone();

    // then
    assertThat(store.getAttempts).isEqualTo(3);
    assertThat(recoveryChecks).containsExactly(new RecoveryCheck(TIMELINE, 2, false));
    assertThat(events).containsExactly("PUT " + PREFIX + LogArchiveKeys.commitKey(1));
  }

  @Test
  void shouldPutCommitEntryUnderCommitKey() {
    // when
    publisher.publish(commit(7));
    scheduler.workUntilDone();

    // then
    assertThat(events).containsExactly("PUT " + PREFIX + LogArchiveKeys.commitKey(7));
    assertThat(store.objects.get(PREFIX + LogArchiveKeys.commitKey(7))).isEqualTo(bytes("c7"));
  }

  @Test
  void shouldPutRootThenLatestThenConfirm() {
    // when
    publisher.publish(new PublishRequest(TIMELINE, 5, bytes("c5"), bytes("r5"), null));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.commitKey(5),
            "PUT " + PREFIX + LogArchiveKeys.rootKey(5),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "CONFIRM 5");
    final var latest =
        LogArchiveJson.read(
            store.objects.get(PREFIX + LogArchiveKeys.latestKey()), LatestHint.class);
    assertThat(latest)
        .isEqualTo(new LatestHint(LogArchiveJson.FORMAT_VERSION, TIMELINE, PARTITION, 5));
  }

  @Test
  void shouldPutChunkThenWriteSealIndexWithIdenticalChunk() {
    // given
    final var chunk = chunk(3);

    // when
    publisher.publish(new PublishRequest(TIMELINE, 9, bytes("c9"), null, seal(chunk)));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.commitKey(9),
            "PUT " + PREFIX + chunk.getKey(),
            "SEAL 3");
    assertThat(store.objects.get(PREFIX + chunk.getKey())).isEqualTo(bytes("chunk3"));
    final var written = sealedChunks.getFirst();
    assertThat(written.getChunkNo()).isEqualTo(chunk.getChunkNo());
    assertThat(written.getKey()).isEqualTo(chunk.getKey());
    assertThat(written.getFirstIndex()).isEqualTo(chunk.getFirstIndex());
    assertThat(written.getLastIndex()).isEqualTo(chunk.getLastIndex());
    assertThat(written.getFirstPosition()).isEqualTo(chunk.getFirstPosition());
    assertThat(written.getLastPosition()).isEqualTo(chunk.getLastPosition());
    assertThat(written.getObjectCount()).isEqualTo(chunk.getObjectCount());
    assertThat(written.getFirstCheckpointId()).isEqualTo(chunk.getFirstCheckpointId());
    assertThat(written.getLastCheckpointId()).isEqualTo(chunk.getLastCheckpointId());
    assertThat(written.getSha256()).isEqualTo(chunk.getSha256());
  }

  @Test
  void shouldProcessRootOnlyRequest() {
    // when
    publisher.publish(new PublishRequest(TIMELINE, 12, null, bytes("r12"), null));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.rootKey(12),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "CONFIRM 12");
  }

  @Test
  void shouldProcessSealOnlyRequest() {
    // when
    publisher.publish(new PublishRequest(TIMELINE, -1, null, null, seal(chunk(2))));
    scheduler.workUntilDone();

    // then
    assertThat(events).containsExactly("PUT " + PREFIX + chunk(2).getKey(), "SEAL 2");
  }

  @Test
  void shouldKeepFifoOrderWhenEarlierPutFailsAndIsRetried() {
    // given
    store.failNext(1);

    // when
    publisher.publish(commit(1));
    publisher.publish(commit(2));
    publisher.publish(commit(3));
    scheduler.workUntilDone();

    // then the failed PUT blocks everything behind it
    assertThat(events).containsExactly("PUT-FAILED " + PREFIX + LogArchiveKeys.commitKey(1));

    // when
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT-FAILED " + PREFIX + LogArchiveKeys.commitKey(1),
            "PUT " + PREFIX + LogArchiveKeys.commitKey(1),
            "PUT " + PREFIX + LogArchiveKeys.commitKey(2),
            "PUT " + PREFIX + LogArchiveKeys.commitKey(3));
  }

  @Test
  void shouldBackOffExponentiallyBetweenRetries() {
    // given
    store.failNext(2);

    // when
    publisher.publish(commit(1));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then the second attempt failed too and the next one waits 200 ms
    assertThat(events).hasSize(2);
    scheduler.updateClock(Duration.ofMillis(150));
    scheduler.workUntilDone();
    assertThat(events).hasSize(2);
    scheduler.updateClock(Duration.ofMillis(50));
    scheduler.workUntilDone();
    assertThat(events).hasSize(3).last().isEqualTo("PUT " + PREFIX + LogArchiveKeys.commitKey(1));
  }

  @Test
  void shouldRetryWriteOnBackpressure() {
    // given
    failWrites = 2;

    // when
    publisher.publish(new PublishRequest(TIMELINE, 4, null, bytes("r4"), null));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofMillis(200));
    scheduler.workUntilDone();

    // then the same step is retried, the PUTs are not repeated
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.rootKey(4),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "WRITE-FAILED",
            "WRITE-FAILED",
            "CONFIRM 4");
  }

  @Test
  void shouldNotWriteDuplicateSealIndexWhileOnePending() {
    // when
    publisher.publish(new PublishRequest(TIMELINE, -1, null, null, seal(chunk(5))));
    publisher.publish(new PublishRequest(TIMELINE, -1, null, null, seal(chunk(5))));
    scheduler.workUntilDone();

    // then
    assertThat(events.stream().filter(e -> e.startsWith("SEAL")).toList())
        .containsExactly("SEAL 5");

    // when the seal is applied or rejected, the chunk may be sealed again
    publisher.onIndexSealed(5);
    publisher.publish(new PublishRequest(TIMELINE, -1, null, null, seal(chunk(5))));
    scheduler.workUntilDone();
    publisher.onCommandRejected(LogArchiveIntent.SEAL_INDEX, RejectionType.INVALID_STATE, "dup");
    publisher.publish(new PublishRequest(TIMELINE, -1, null, null, seal(chunk(5))));
    scheduler.workUntilDone();
    assertThat(events.stream().filter(e -> e.startsWith("SEAL")).toList())
        .containsExactly("SEAL 5", "SEAL 5", "SEAL 5");
  }

  @Test
  void shouldContinueAfterRejectedConfirm() {
    // when
    publisher.publish(new PublishRequest(TIMELINE, 1, bytes("c1"), bytes("r1"), null));
    publisher.onCommandRejected(
        LogArchiveIntent.CONFIRM_PUBLISHED, RejectionType.INVALID_STATE, "already published");
    publisher.publish(commit(2));
    scheduler.workUntilDone();

    // then
    assertThat(events).last().isEqualTo("PUT " + PREFIX + LogArchiveKeys.commitKey(2));
  }

  @Test
  void shouldDropQueuedRequestsOnRecovery() {
    // given a blocked head request and one queued behind it
    store.failNext(1);
    publisher.publish(commit(1));
    publisher.publish(commit(2));
    scheduler.workUntilDone();

    // when
    publisher.onRecovered(new LogArchiveMeta());
    publisher.publish(commit(3));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT-FAILED " + PREFIX + LogArchiveKeys.commitKey(1),
            "PUT " + PREFIX + LogArchiveKeys.commitKey(3));
  }

  @Test
  void shouldWriteHandoverFileAfterCommitEntryAndBeforeRoot() {
    // given
    final var handover = startPublisherWithBackupStore();

    // when
    handover.publish(handoverRequest(6));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.commitKey(6),
            "HANDOVER 7",
            "PUT " + PREFIX + LogArchiveKeys.rootKey(6),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "CONFIRM 6");
    assertThat(new String(handoverContent, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"formatVersion\":1,\"partitionId\":1,\"checkpointId\":7,"
                + "\"checkpointPosition\":15,\"logArchiveTimeline\":\"tl-0\"}");
  }

  @Test
  void shouldRetryHandoverFileUntilItIsWrittenAndOnlyThenConfirm() {
    // given
    final var handover = startPublisherWithBackupStore();
    failHandoverWrites = 2;

    // when
    handover.publish(handoverRequest(6));
    scheduler.workUntilDone();

    // then - blocked on the old-side file: no root and no confirmation yet
    assertThat(events)
        .containsExactly("PUT " + PREFIX + LogArchiveKeys.commitKey(6), "HANDOVER-FAILED 7");

    // when
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.commitKey(6),
            "HANDOVER-FAILED 7",
            "HANDOVER-FAILED 7",
            "HANDOVER 7",
            "PUT " + PREFIX + LogArchiveKeys.rootKey(6),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "CONFIRM 6");
  }

  @Test
  void shouldWriteSameHandoverFileWhenRepublishedByANewLeader() {
    // given
    final var first = startPublisherWithBackupStore();
    first.publish(handoverRequest(6));
    scheduler.workUntilDone();
    final var firstContent = handoverContent;

    // when
    final var second = startPublisherWithBackupStore();
    second.publish(handoverRequest(6));
    scheduler.workUntilDone();

    // then
    assertThat(handoverContent).isEqualTo(firstContent);
  }

  @Test
  void shouldSkipHandoverFileWithoutBackupStore() {
    // when - the publisher of setUp has no store of old backups
    publisher.publish(handoverRequest(6));
    scheduler.workUntilDone();

    // then
    assertThat(events)
        .containsExactly(
            "PUT " + PREFIX + LogArchiveKeys.commitKey(6),
            "PUT " + PREFIX + LogArchiveKeys.rootKey(6),
            "PUT " + PREFIX + LogArchiveKeys.latestKey(),
            "CONFIRM 6");
  }

  @Test
  void shouldNotTouchBackupStoreForRequestsWithoutHandover() {
    // given
    final var handover = startPublisherWithBackupStore();

    // when
    handover.publish(new PublishRequest(TIMELINE, 5, bytes("c5"), bytes("r5"), null));
    scheduler.workUntilDone();

    // then
    assertThat(events).doesNotContain("HANDOVER 7");
  }

  private static PublishRequest handoverRequest(final long seq) {
    return new PublishRequest(
        TIMELINE, seq, bytes("c" + seq), bytes("r" + seq), null, new HandoverJson(7, 15));
  }

  private LogArchivePublisher startPublisherWithBackupStore() {
    final var backupStore = mock(BackupStore.class);
    when(backupStore.storeHandoverRecord(anyInt(), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              final long checkpointId = invocation.getArgument(1);
              assertThat((int) invocation.getArgument(0)).isEqualTo(PARTITION);
              if (failHandoverWrites > 0) {
                failHandoverWrites--;
                events.add("HANDOVER-FAILED " + checkpointId);
                return CompletableFuture.failedFuture(new RuntimeException("boom"));
              }
              events.add("HANDOVER " + checkpointId);
              handoverContent = invocation.getArgument(2);
              return CompletableFuture.completedFuture(null);
            });
    final var started = startPublisher(backupStore);
    started.onRecovered(new LogArchiveMeta());
    scheduler.workUntilDone();
    return started;
  }

  private LogArchivePublisher startPublisher() {
    return startPublisher(null);
  }

  private LogArchivePublisher startPublisher(final BackupStore backupStore) {
    final var started =
        new LogArchivePublisher(
            PARTITION,
            store,
            writer,
            (meta, bucketAhead) ->
                recoveryChecks.add(
                    new RecoveryCheck(meta.getTimelineId(), meta.getNextSeq(), bucketAhead)),
            backupStore);
    scheduler.submitActor(started);
    scheduler.workUntilDone();
    recoveryChecks.clear();
    return started;
  }

  private static PublishRequest commit(final long seq) {
    return new PublishRequest(TIMELINE, seq, bytes("c" + seq), null, null);
  }

  private static SealRequest seal(final IndexChunk chunk) {
    return new SealRequest(chunk, bytes("chunk" + chunk.getChunkNo()));
  }

  private static IndexChunk chunk(final long chunkNo) {
    return new IndexChunk()
        .setChunkNo(chunkNo)
        .setKey(LogArchiveKeys.indexKey(chunkNo * 10, chunkNo * 10 + 9))
        .setFirstIndex(chunkNo * 10)
        .setLastIndex(chunkNo * 10 + 9)
        .setFirstPosition(100 + chunkNo)
        .setLastPosition(200 + chunkNo)
        .setObjectCount(4)
        .setFirstCheckpointId(11)
        .setLastCheckpointId(12)
        .setSha256("sha-" + chunkNo);
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private record RecoveryCheck(String timelineId, long nextSeq, boolean bucketAhead) {}

  private static final class FakeStore implements LogArchiveStore {
    private final java.util.Map<String, byte[]> objects = new java.util.HashMap<>();
    private final List<String> events;
    private int failures;
    private int getFailures;
    private int getAttempts;

    private FakeStore(final List<String> events) {
      this.events = events;
    }

    private void failNext(final int count) {
      failures = count;
    }

    private void failNextGets(final int count) {
      getFailures = count;
    }

    /** Puts objects without recording them as events, e.g. those of an earlier leader. */
    private void seed(final String... keys) {
      for (final var key : keys) {
        objects.put(key, bytes("seeded"));
      }
    }

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      if (failures > 0) {
        failures--;
        events.add("PUT-FAILED " + key);
        return CompletableFuture.failedFuture(new RuntimeException("boom"));
      }
      events.add("PUT " + key);
      objects.put(key, content);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      getAttempts++;
      if (getFailures > 0) {
        getFailures--;
        return CompletableFuture.failedFuture(new RuntimeException("boom"));
      }
      return CompletableFuture.completedFuture(Optional.ofNullable(objects.get(key)));
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
      return CompletableFuture.completedFuture(
          objects.keySet().stream().filter(k -> k.startsWith(prefix)).sorted().toList());
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
