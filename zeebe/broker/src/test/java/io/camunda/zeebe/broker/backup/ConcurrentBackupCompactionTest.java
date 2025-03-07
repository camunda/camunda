/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.backup;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.impl.LogCompactor;
import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.broker.utils.InlineThreadContext;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.test.DynamicAutoCloseable;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public class ConcurrentBackupCompactionTest extends DynamicAutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentBackupCompactionTest.class);
  private static final String SNAPSHOT_FILE_NAME = "file1";
  @TempDir Path dataDirectory;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private ActorScheduler actorScheduler;
  private SegmentedJournal journal;
  private FileBasedSnapshotStore snapshotStore;
  private InMemoryMockBackupStore backupStore;
  private BackupService backupService;
  private final int nodeId = 1;
  private final int partitionId = 1;
  private LogCompactor logCompactor;
  private final ThreadContext threadContext = new InlineThreadContext();
  @Mock private RaftLog raftLog;
  @Mock private LogStorage logStorage;
  private final RaftServiceMetrics raftMetrics = new RaftServiceMetrics("1", meterRegistry);

  @BeforeEach
  void setUp() {
    actorScheduler = manage(ActorScheduler.newActorScheduler().build());
    actorScheduler.start();
    backupStore = manage(new InMemoryMockBackupStore());
    snapshotStore =
        manage(
            new FileBasedSnapshotStore(
                0, partitionId, dataDirectory, snapshotPath -> Map.of(), meterRegistry));
    actorScheduler.submitActor(snapshotStore, SchedulingHints.IO_BOUND);

    final var partitionMetadata =
        new PartitionMetadata(
            PartitionId.from("raft", partitionId), Set.of(), Map.of(), 1, new MemberId("1"));
    final var raftPartition =
        new RaftPartition(partitionMetadata, null, dataDirectory.toFile(), meterRegistry);

    journal =
        manage(
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(dataDirectory.toFile())
                .withName(raftPartition.name())
                .withMetaStore(mock(JournalMetaStore.class))
                .withMaxSegmentSize(128)
                .build());
    logCompactor = new LogCompactor(threadContext, raftLog, 3, raftMetrics, LOG);

    // raftLog just calls journal in the real implementation
    when(raftLog.deleteUntil(anyLong()))
        .thenAnswer(
            (Answer<Boolean>)
                invocation -> {
                  final Long index = invocation.getArgument(0);
                  return journal.deleteUntil(index);
                });

    backupService =
        manage(
            new BackupService(
                nodeId,
                partitionId,
                1,
                backupStore,
                snapshotStore,
                dataDirectory,
                // RaftPartitions implements this interface, but the RaftServer is not started
                index -> CompletableFuture.completedFuture(journal.getTailSegments(index).values()),
                meterRegistry,
                logStorage,
                raftPartition.name()));
    actorScheduler.submitActor(backupService);
  }

  @AfterEach
  public void tearDown() {
    close();
  }

  @Test
  public void shouldNotFailWhenCompactionTriggers() throws Exception {
    // given
    appendRecord(1L, "1");
    appendRecord(2L, "2");
    final var snapshot = takeSnapshot(2L, 2L);
    final var backupIdx = 3L;
    logCompactor.compactFromSnapshots(snapshotStore);
    Awaitility.await("compaction is done")
        .until(() -> logCompactor.getCompactableIndex() == snapshot.getIndex());
    appendRecord(3L, "3");

    // when
    // a backup is taken (but the snapshot store does not complete it,
    // because the BackupStore it's blocked)
    final var backupResultFut = backupService.takeBackup(backupIdx, backupIdx);

    Awaitility.await("snapshot is reserved")
        .until(
            () ->
                snapshotStore.getLatestSnapshot().map(PersistedSnapshot::isReserved).orElse(false));

    // backup service is blocked

    appendRecord(4L, "4");
    appendRecord(5L, "5");

    takeSnapshot(4L, 5L);
    logCompactor.compactFromSnapshots(snapshotStore);
    Awaitility.await("no compaction is done")
        .until(() -> logCompactor.getCompactableIndex() == snapshot.getIndex());

    // then
    // snapshot files are not deleted
    try (final var files =
        Files.list(dataDirectory.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))) {
      // 2 file per snapshot: snapshot + checksum
      assertThat(files.toList().size()).isEqualTo(4L);
    }
    // all segments needed are still in the file system
    final var activeSegmentPaths = journal.getTailSegments(backupIdx);
    activeSegmentPaths.values().forEach(p -> assertThat(p).exists());

    assertThat(backupResultFut.isDone()).isFalse();

    // given
    Awaitility.await("BackStore.save is called")
        .atMost(Duration.ofSeconds(5))
        .until(() -> !backupStore.backupInProgress().isEmpty());

    // when
    // unlock futures from the backupStore
    backupStore.completeSaveFutures();

    // then
    Awaitility.await("backup is completed successfully")
        .atMost(Duration.ofSeconds(5))
        .until(backupResultFut::isDone);
  }

  private void appendRecord(final long asqn, final String data) {
    journal.append(asqn, new DirectBufferWriter().wrap(new UnsafeBuffer(data.getBytes())));
  }

  private PersistedSnapshot takeSnapshot(final long index, final long lastWrittenPosition) {
    final var transientSnapshot = snapshotStore.newTransientSnapshot(index, 1, 1, 1).get();
    transientSnapshot.take(
        path -> {
          try {
            FileUtil.ensureDirectoryExists(path);

            Files.write(
                path.resolve(SNAPSHOT_FILE_NAME),
                "This is the content".getBytes(),
                CREATE_NEW,
                StandardOpenOption.WRITE);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });

    return transientSnapshot.withLastFollowupEventPosition(lastWrittenPosition).persist().join();
  }
}
