/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.SnapshotDescriptorFile;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

final class SnapshotUploaderTest {

  private static final int PARTITION = 1;
  private static final String TIMELINE = "tl-0";
  private static final String PREFIX = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);
  private static final Duration INTERVAL = Duration.ofMinutes(10);

  @RegisterExtension
  final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @TempDir Path tempDir;

  private final FakeStore store = new FakeStore();
  private final FakeWriter writer = new FakeWriter();
  private final PersistedSnapshotStore snapshotStore = mock(PersistedSnapshotStore.class);
  private final AtomicInteger reservations = new AtomicInteger();
  private final AtomicInteger releases = new AtomicInteger();
  private SnapshotUploader uploader;

  @BeforeEach
  void setUp() {
    uploader = new SnapshotUploader(PARTITION, store, snapshotStore, writer, INTERVAL);
    scheduler.submitActor(uploader);
    scheduler.workUntilDone();
  }

  @Test
  void shouldDoNothingBeforeTimelineIsKnown() {
    // given
    final var snapshot = givenSnapshot("1-1-10-10", 5);

    // when
    scheduler.updateClock(INTERVAL.multipliedBy(2));
    scheduler.workUntilDone();

    // then
    assertThat(store.objects).isEmpty();
    assertThat(writer.entries).isEmpty();
    assertThat(reservations).hasValue(0);
    assertThat(snapshot).isNotNull();
  }

  @Test
  void shouldUploadFilesAndDescriptorAndWriteCommand() throws IOException {
    // given
    givenSnapshot("1-1-10-10", 5);

    // when
    recoverAndTick(5);

    // then
    final var id = "1-1-10-10";
    assertThat(store.objects.keySet())
        .containsExactlyInAnyOrder(
            PREFIX + LogArchiveKeys.snapshotFileKey(id, "a.sst"),
            PREFIX + LogArchiveKeys.snapshotFileKey(id, "b.sst"),
            PREFIX + LogArchiveKeys.snapshotFileKey(id, "snapshot.checksum"),
            PREFIX + LogArchiveKeys.snapshotDescriptorKey(id));
    final var descriptor =
        LogArchiveJson.read(
            store.objects.get(PREFIX + LogArchiveKeys.snapshotDescriptorKey(id)),
            SnapshotDescriptorFile.class);
    assertThat(descriptor.files()).containsExactly("a.sst", "b.sst", "snapshot.checksum");
    assertThat(descriptor.fileCount()).isEqualTo(3);
    assertThat(descriptor.sizeBytes()).isEqualTo(3 + 4 + 2);
    assertThat(descriptor.index()).isEqualTo(5);
    assertThat(descriptor.term()).isEqualTo(2);
    assertThat(descriptor.processedPosition()).isEqualTo(10);
    assertThat(descriptor.exportedPosition()).isEqualTo(7);
    assertThat(descriptor.lastFollowupEventPosition()).isEqualTo(11);
    assertThat(descriptor.maxExportedPosition()).isEqualTo(8);

    assertThat(writer.entries).hasSize(1);
    final var entry = writer.entries.getFirst();
    assertThat(entry.recordMetadata().getValueType()).isEqualTo(ValueType.LOG_ARCHIVE);
    assertThat(entry.recordMetadata().getIntent()).isEqualTo(LogArchiveIntent.ARCHIVE_SNAPSHOT);
    final var record = (LogArchiveRecord) entry.recordValue();
    assertThat(record.getTimelineId()).isEqualTo(TIMELINE);
    final var archived = record.getSnapshot();
    assertThat(archived.getId()).isEqualTo(id);
    assertThat(archived.getIndex()).isEqualTo(5);
    assertThat(archived.getTerm()).isEqualTo(2);
    assertThat(archived.getProcessedPosition()).isEqualTo(10);
    assertThat(archived.getExportedPosition()).isEqualTo(7);
    assertThat(archived.getLastFollowupEventPosition()).isEqualTo(11);
    assertThat(archived.getMaxExportedPosition()).isEqualTo(8);
    assertThat(archived.getFileCount()).isEqualTo(3);
    assertThat(archived.getSizeBytes()).isEqualTo(9);
    assertThat(reservations).hasValue(1);
    assertThat(releases).hasValue(0);
  }

  @Test
  void shouldReleaseReservationWhenArchived() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);

    // when
    uploader.onSnapshotArchived("1-1-10-10");
    scheduler.workUntilDone();

    // then
    assertThat(releases).hasValue(1);
  }

  @Test
  void shouldReleaseReservationWhenRejectedAndNotRetrySameSnapshot() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);

    // when
    uploader.onSnapshotRejected("1-1-10-10");
    scheduler.workUntilDone();

    // then
    assertThat(releases).hasValue(1);
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldUploadSnapshotAgainForNewTimeline() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);
    uploader.onSnapshotRejected("1-1-10-10");
    scheduler.workUntilDone();

    // when
    uploader.onTimelineStarted("tl-new");
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();

    // then
    assertThat(writer.entries).hasSize(2);
  }

  @Test
  void shouldIgnoreAcceptanceOfOtherSnapshot() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);

    // when
    uploader.onSnapshotArchived("other");
    scheduler.workUntilDone();

    // then
    assertThat(releases).hasValue(0);
  }

  @Test
  void shouldNotReuploadArchivedSnapshot() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);
    uploader.onSnapshotArchived("1-1-10-10");
    scheduler.workUntilDone();

    // when
    scheduler.updateClock(INTERVAL.multipliedBy(2));
    scheduler.workUntilDone();

    // then
    assertThat(writer.entries).hasSize(1);
    assertThat(reservations).hasValue(1);
  }

  @Test
  void shouldSkipSnapshotAboveArchivedIndexAndRetryAfterItAdvances() {
    // given
    givenSnapshot("1-1-10-10", 50);
    recoverAndTick(5);
    assertThat(reservations).hasValue(0);

    // when
    uploader.onLogArchived(50, 100);
    scheduler.workUntilDone();
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();

    // then
    assertThat(reservations).hasValue(1);
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldReleaseReservationWhenUploadFails() {
    // given
    givenSnapshot("1-1-10-10", 5);
    store.failPuts = true;

    // when
    recoverAndTick(5);

    // then
    assertThat(reservations).hasValue(1);
    assertThat(releases).hasValue(1);
    assertThat(writer.entries).isEmpty();

    // and retried on the next tick
    store.failPuts = false;
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldRetryWriteWhenRejectedByBackpressure() {
    // given
    givenSnapshot("1-1-10-10", 5);
    writer.failures = 2;

    // when
    recoverAndTick(5);
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofSeconds(2));
    scheduler.workUntilDone();

    // then
    assertThat(writer.attempts).isEqualTo(3);
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldReleaseReservationWhenAwaitCommitTimesOut() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);

    // when
    scheduler.updateClock(SnapshotUploader.AWAIT_COMMIT_TIMEOUT.plusSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(releases).hasValue(1);
  }

  @Test
  void shouldReleaseReservationOnClose() {
    // given
    givenSnapshot("1-1-10-10", 5);
    recoverAndTick(5);

    // when
    uploader.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(releases).hasValue(1);
  }

  private void recoverAndTick(final int seconds) {
    uploader.onRecovered(new LogArchiveMeta().setTimelineId(TIMELINE).setArchivedIndex(10));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofSeconds(seconds));
    scheduler.workUntilDone();
  }

  private PersistedSnapshot givenSnapshot(final String id, final long index) {
    try {
      final var dir = Files.createDirectory(tempDir.resolve("snap-" + id + "-" + index));
      final var files = new TreeMap<String, Path>();
      files.put("a.sst", write(dir.resolve("a.sst"), "abc"));
      files.put("b.sst", write(dir.resolve("b.sst"), "abcd"));
      files.put("snapshot.checksum", write(tempDir.resolve(id + ".checksum"), "xy"));

      final var snapshot = mock(PersistedSnapshot.class);
      final var snapshotId = mock(SnapshotId.class);
      final var metadata = mock(SnapshotMetadata.class);
      final var reservation = mock(SnapshotReservation.class);
      when(reservation.release())
          .thenAnswer(
              i -> {
                releases.incrementAndGet();
                return CompletableActorFuture.completed();
              });
      when(snapshot.reserve())
          .thenAnswer(
              i -> {
                reservations.incrementAndGet();
                return CompletableActorFuture.completed(reservation);
              });
      when(snapshot.getId()).thenReturn(id);
      when(snapshot.getIndex()).thenReturn(index);
      when(snapshot.getTerm()).thenReturn(2L);
      when(snapshot.files()).thenReturn(Map.copyOf(files));
      when(snapshot.snapshotId()).thenReturn(snapshotId);
      when(snapshot.getMetadata()).thenReturn(metadata);
      when(snapshotId.getExportedPosition()).thenReturn(7L);
      when(metadata.processedPosition()).thenReturn(10L);
      when(metadata.lastFollowupEventPosition()).thenReturn(11L);
      when(metadata.maxExportedPosition()).thenReturn(8L);
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.of(snapshot));
      return snapshot;
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path write(final Path path, final String content) throws IOException {
    return Files.writeString(path, content);
  }

  private static final class FakeWriter implements LogStreamWriter {
    final List<LogAppendEntry> entries = new ArrayList<>();
    int failures;
    int attempts;

    @Override
    public Either<WriteFailure, Long> tryWrite(
        final WriteContext context,
        final List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      attempts++;
      if (failures > 0) {
        failures--;
        return Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
      }
      entries.addAll(appendEntries);
      return Either.right(1L);
    }
  }

  private static final class FakeStore implements LogArchiveStore {
    final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    volatile boolean failPuts;

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      if (failPuts) {
        return CompletableFuture.failedFuture(new IOException("boom"));
      }
      objects.put(key, content);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      if (failPuts) {
        return CompletableFuture.failedFuture(new IOException("boom"));
      }
      try {
        objects.put(key, Files.readAllBytes(file));
      } catch (final IOException e) {
        return CompletableFuture.failedFuture(e);
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      return CompletableFuture.completedFuture(Optional.ofNullable(objects.get(key)));
    }

    @Override
    public CompletableFuture<byte[]> getRange(
        final String key, final long offset, final int length) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public CompletableFuture<Void> getToFile(final String key, final Path target) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
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
