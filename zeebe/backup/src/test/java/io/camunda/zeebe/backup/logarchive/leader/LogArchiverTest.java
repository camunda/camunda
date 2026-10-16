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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.LogObjectDecoder;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.Either;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

final class LogArchiverTest {

  private static final int PARTITION = 2;
  private static final long TERM = 7;
  private static final String TIMELINE = "tl-0";
  private static final String PREFIX = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);
  private static final int RECORD_BYTES = 100;
  private static final int FRAME_BYTES = 6 * Long.BYTES + Integer.BYTES + RECORD_BYTES;
  private static final Duration CUT_INTERVAL = Duration.ofSeconds(2);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final FakeEntrySource source = new FakeEntrySource();
  private final FakeStore store = new FakeStore();
  private final LogStreamWriter writer = mock(LogStreamWriter.class);
  private final List<Written> written = new ArrayList<>();
  private int failWrites;
  private LogArchiver archiver;

  @BeforeEach
  void setUp() {
    scheduler.getClock().pinCurrentTime();
    when(writer.tryWrite(any(), ArgumentMatchers.<LogAppendEntry>any()))
        .thenAnswer(
            invocation -> {
              if (failWrites > 0) {
                failWrites--;
                return Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
              }
              final LogAppendEntry entry = invocation.getArgument(1);
              // the archiver creates a new record for every write, so it can be kept
              final var record = (LogArchiveRecord) entry.recordValue();
              written.add(
                  new Written((LogArchiveIntent) entry.recordMetadata().getIntent(), record));
              return Either.right(1L);
            });
  }

  @Test
  void shouldCutBySize() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(7);

    // when
    recover(TIMELINE, 0);

    // then
    assertThat(archiveLogRanges()).containsExactly(range(1, 3));
    assertThat(store.keys()).containsExactly(PREFIX + LogArchiveKeys.dataKey(1, 3, TERM));
  }

  @Test
  void shouldKeepOneObjectInFlight() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(9);

    // when
    recover(TIMELINE, 0);
    scheduler.updateClock(CUT_INTERVAL.multipliedBy(3));
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(1, 3));
    assertThat(archiver.state()).isEqualTo(LogArchiver.State.AWAIT_COMMIT);
  }

  @Test
  void shouldCutByIntervalWhenSizeIsNotReached() {
    // given
    startArchiver(1024 * 1024, () -> TIMELINE);
    source.commit(2);
    recover(TIMELINE, 0);
    assertThat(written).isEmpty();

    // when
    scheduler.updateClock(CUT_INTERVAL);
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(1, 2));
  }

  @Test
  void shouldNotCutWithoutPendingEntries() {
    // given
    startArchiver(1024 * 1024, () -> TIMELINE);
    recover(TIMELINE, 0);

    // when
    scheduler.updateClock(CUT_INTERVAL.multipliedBy(2));
    scheduler.workUntilDone();

    // then
    assertThat(written).isEmpty();
    assertThat(store.keys()).isEmpty();
  }

  @Test
  void shouldArchiveContiguousRangesAfterEachCommit() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(10);
    recover(TIMELINE, 0);

    // when
    acknowledgeLastArchiveLog();
    acknowledgeLastArchiveLog();
    acknowledgeLastArchiveLog();
    scheduler.updateClock(CUT_INTERVAL);
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges())
        .containsExactly(range(1, 3), range(4, 6), range(7, 9), range(10, 10));
  }

  @Test
  void shouldWakeUpOnCommit() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    recover(TIMELINE, 0);
    source.commit(2);
    archiver.onCommit();
    scheduler.workUntilDone();
    assertThat(written).isEmpty();

    // when
    source.commit(3);
    archiver.onCommit();
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(1, 3));
  }

  @Test
  void shouldWriteArchiveLogDescribingTheUploadedObject() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(3);

    // when
    recover(TIMELINE, 0);

    // then
    final var command = written.getFirst();
    assertThat(command.intent()).isEqualTo(LogArchiveIntent.ARCHIVE_LOG);
    assertThat(command.record().getTimelineId()).isEqualTo(TIMELINE);
    assertThat(command.record().getObjects()).hasSize(1);
    final LogObjectValue object = command.record().getObjects().getFirst();
    final var key = LogArchiveKeys.dataKey(1, 3, TERM);
    final byte[] bytes = store.objects.get(PREFIX + key);
    assertThat(object.getKey()).isEqualTo(key);
    assertThat(object.getTerm()).isEqualTo(TERM);
    assertThat(object.getFirstIndex()).isEqualTo(1);
    assertThat(object.getLastIndex()).isEqualTo(3);
    assertThat(object.getFirstPosition()).isEqualTo(source.frame(1).lowestPosition());
    assertThat(object.getLastPosition()).isEqualTo(source.frame(3).highestPosition());
    assertThat(object.getEntryCount()).isEqualTo(3);
    assertThat(object.getSizeBytes()).isEqualTo(bytes.length);
    assertThat(object.getSha256()).isEqualTo(sha256(bytes));

    final var decoded = LogObjectDecoder.decode(bytes);
    assertThat(decoded.header().timelineId()).isEqualTo(TIMELINE);
    assertThat(decoded.header().partitionId()).isEqualTo(PARTITION);
    assertThat(decoded.header().term()).isEqualTo(TERM);
    assertThat(decoded.frames()).containsExactly(source.frame(1), source.frame(2), source.frame(3));
  }

  @Test
  void shouldResumeAfterArchivedIndex() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(8);

    // when
    recover(TIMELINE, 4);

    // then
    assertThat(archiveLogRanges()).containsExactly(range(5, 7));
  }

  @Test
  void shouldResetToArchivedIndexOnRejection() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(8);
    recover(TIMELINE, 0);

    // when - F is actually 2, e.g. after an object of an earlier term was archived
    archiver.onArchiveLogRejected(2);
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(1, 3), range(3, 5));
  }

  @Test
  void shouldIgnoreArchivedNotificationsAtOrBelowCursor() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(8);
    recover(TIMELINE, 3);

    // when
    archiver.onLogArchived(3, 30);
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(4, 6));
    assertThat(archiver.state()).isEqualTo(LogArchiver.State.AWAIT_COMMIT);
  }

  @Test
  void shouldStartTimelineWhenItDiffers() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> "tl-0.r42");
    source.setFirstIndex(5);
    source.commit(10);

    // when
    recover("tl-0", 20);

    // then
    assertThat(written).hasSize(1);
    final var command = written.getFirst();
    assertThat(command.intent()).isEqualTo(LogArchiveIntent.START_TIMELINE);
    assertThat(command.record().getTimelineId()).isEqualTo("tl-0.r42");
    assertThat(command.record().getStartIndex()).isEqualTo(5);
    assertThat(command.record().getParentTimelineId()).isEqualTo("tl-0");
    assertThat(store.keys()).isEmpty();
  }

  @Test
  void shouldArchiveFromStartIndexOnceTimelineStarted() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> "tl-0.r42");
    source.setFirstIndex(5);
    source.commit(10);
    recover("tl-0", 20);

    // when
    archiver.onTimelineStarted("tl-0.r42");
    scheduler.workUntilDone();

    // then
    assertThat(archiveLogRanges()).containsExactly(range(5, 7));
    assertThat(store.keys())
        .containsExactly(
            LogArchiveKeys.partitionPrefix("tl-0.r42", PARTITION)
                + LogArchiveKeys.dataKey(5, 7, TERM));
  }

  @Test
  void shouldStartTimelineWhenNoneWasStarted() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(3);

    // when
    archiver.onRecovered(new LogArchiveMeta());
    scheduler.workUntilDone();

    // then
    final var command = written.getFirst();
    assertThat(command.intent()).isEqualTo(LogArchiveIntent.START_TIMELINE);
    assertThat(command.record().getTimelineId()).isEqualTo(TIMELINE);
    assertThat(command.record().getStartIndex()).isEqualTo(1);
    assertThat(command.record().getParentTimelineId()).isEmpty();
  }

  @Test
  void shouldRetryRejectedStartTimeline() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    archiver.onRecovered(new LogArchiveMeta());
    scheduler.workUntilDone();

    // when
    archiver.onCommandRejected(
        LogArchiveIntent.START_TIMELINE, RejectionType.INVALID_STATE, "test");
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then
    assertThat(written)
        .extracting(Written::intent)
        .containsExactly(LogArchiveIntent.START_TIMELINE, LogArchiveIntent.START_TIMELINE);
  }

  @Test
  void shouldUseTimelineFileOverConfiguration(@TempDir final Path dir) throws Exception {
    // given
    Files.writeString(dir.resolve(LogArchiverTimeline.TIMELINE_FILE_NAME), "tl-0.r42\n");
    startArchiver(3 * FRAME_BYTES, () -> LogArchiverTimeline.resolve(dir, TIMELINE));

    // when
    recover(TIMELINE, 0);

    // then
    final var command = written.getFirst();
    assertThat(command.intent()).isEqualTo(LogArchiveIntent.START_TIMELINE);
    assertThat(command.record().getTimelineId()).isEqualTo("tl-0.r42");
    assertThat(command.record().getParentTimelineId()).isEqualTo(TIMELINE);
  }

  @Test
  void shouldUseConfiguredTimelineWithoutTimelineFile(@TempDir final Path dir) {
    // when
    final var timeline = LogArchiverTimeline.resolve(dir, TIMELINE);

    // then
    assertThat(timeline).isEqualTo(TIMELINE);
  }

  @Test
  void shouldRetryFailedUploadFromSameRange() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(5);
    store.failPuts = 2;

    // when
    recover(TIMELINE, 0);
    assertThat(written).isEmpty();
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();
    assertThat(written).isEmpty();
    scheduler.updateClock(Duration.ofMillis(200));
    scheduler.workUntilDone();

    // then
    assertThat(store.putAttempts).isEqualTo(3);
    assertThat(archiveLogRanges()).containsExactly(range(1, 3));
  }

  @Test
  void shouldRetryFailedWrite() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(5);
    failWrites = 1;

    // when
    recover(TIMELINE, 0);
    assertThat(written).isEmpty();
    scheduler.updateClock(Duration.ofMillis(100));
    scheduler.workUntilDone();

    // then
    assertThat(store.putAttempts).isEqualTo(1);
    assertThat(archiveLogRanges()).containsExactly(range(1, 3));
  }

  @Test
  void shouldDoNothingBeforeRecovery() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);
    source.commit(5);

    // when
    archiver.onCommit();
    scheduler.updateClock(CUT_INTERVAL);
    scheduler.workUntilDone();

    // then
    assertThat(written).isEmpty();
    assertThat(store.keys()).isEmpty();
  }

  @Test
  void shouldCloseSourceOnClose() {
    // given
    startArchiver(3 * FRAME_BYTES, () -> TIMELINE);

    // when
    archiver.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(source.closed).isTrue();
  }

  // -----------------------------------------------------------------------------------------------

  private void startArchiver(final long cutMaxBytes, final Supplier<String> timeline) {
    archiver =
        new LogArchiver(
            PARTITION, TERM, store, writer, source, timeline, cutMaxBytes, CUT_INTERVAL);
    scheduler.submitActor(archiver);
    scheduler.workUntilDone();
  }

  private void recover(final String timelineId, final long archivedIndex) {
    archiver.onRecovered(
        new LogArchiveMeta()
            .setTimelineId(timelineId)
            .setStartIndex(1)
            .setArchivedIndex(archivedIndex));
    scheduler.workUntilDone();
  }

  /** Simulates the processor committing the last ARCHIVE_LOG command. */
  private void acknowledgeLastArchiveLog() {
    final var object = written.getLast().record().getObjects().getLast();
    archiver.onLogArchived(object.getLastIndex(), object.getLastPosition());
    scheduler.workUntilDone();
  }

  private List<String> archiveLogRanges() {
    return written.stream()
        .filter(w -> w.intent() == LogArchiveIntent.ARCHIVE_LOG)
        .flatMap(w -> w.record().getObjects().stream())
        .map(o -> range(o.getFirstIndex(), o.getLastIndex()))
        .toList();
  }

  private static String range(final long first, final long last) {
    return first + ".." + last;
  }

  private static String sha256(final byte[] bytes) throws RuntimeException {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private record Written(LogArchiveIntent intent, LogArchiveRecord record) {}

  /** Committed log with entries {@code firstIndex..commitIndex}; all are application entries. */
  private static final class FakeEntrySource implements LogArchiverEntrySource {
    private long firstIndex = 1;
    private long commitIndex;
    private long nextIndex = 1;
    private boolean closed;

    void setFirstIndex(final long firstIndex) {
      this.firstIndex = firstIndex;
      nextIndex = Math.max(nextIndex, firstIndex);
    }

    void commit(final long index) {
      commitIndex = index;
    }

    LogArchiveFrame frame(final long index) {
      final var bytes = new byte[RECORD_BYTES];
      Arrays.fill(bytes, (byte) index);
      final long lowest = index * 10;
      return new LogArchiveFrame(index, TERM - 1, lowest, lowest, lowest + 2, index, bytes);
    }

    @Override
    public long firstIndex() {
      return firstIndex;
    }

    @Override
    public void seek(final long index) {
      nextIndex = Math.max(index, firstIndex);
    }

    @Override
    public boolean hasNext() {
      return nextIndex <= commitIndex;
    }

    @Override
    public LogArchiveFrame next() {
      return frame(nextIndex++);
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class FakeStore implements LogArchiveStore {
    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private int failPuts;
    private int putAttempts;

    List<String> keys() {
      return List.copyOf(objects.keySet());
    }

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      putAttempts++;
      if (failPuts > 0) {
        failPuts--;
        return CompletableFuture.failedFuture(new RuntimeException("expected"));
      }
      objects.put(key, content.clone());
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      return CompletableFuture.completedFuture(Optional.ofNullable(objects.get(key)));
    }

    @Override
    public CompletableFuture<byte[]> getRange(final String key, final long offset, final int len) {
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
    public CompletableFuture<Void> closeAsync() {
      return CompletableFuture.completedFuture(null);
    }
  }
}
