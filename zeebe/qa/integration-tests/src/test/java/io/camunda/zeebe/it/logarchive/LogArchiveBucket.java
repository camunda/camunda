/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemLogArchiveStore;
import io.camunda.zeebe.backup.logarchive.format.LogObjectDecoder;
import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry.Type;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreException;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * Reads the log archive of a broker from its filesystem backup store and checks everything the
 * writers guarantee about it. Shared by the log archive integration tests.
 */
final class LogArchiveBucket implements AutoCloseable {

  static final String TIMELINE = "tl-0";

  private final Path basePath;
  private final LogArchiveStore store;

  LogArchiveBucket(final Path basePath) {
    this.basePath = basePath;
    store = FilesystemLogArchiveStore.of(new FilesystemBackupConfig(basePath.toString()));
  }

  /** Configures the filesystem backup store at the given base path. */
  static void configureStore(final Camunda cfg, final Path basePath) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(basePath.toAbsolutePath().toString());
  }

  /**
   * Enables the log archive with small limits, so that objects are cut, chunks sealed and roots
   * written within seconds. The log archive settings have no unified configuration yet.
   */
  static TestStandaloneBroker enableLogArchive(final TestStandaloneBroker broker) {
    final var prefix = "zeebe.broker.data.backup.logArchive.";
    return broker
        .withProperty(prefix + "enabled", true)
        .withProperty(prefix + "cutInterval", "PT0.2S")
        .withProperty(prefix + "cutMaxBytes", "64KB")
        .withProperty(prefix + "snapshotUploadInterval", "PT2S")
        .withProperty(prefix + "chunkSize", 3)
        .withProperty(prefix + "rootEvery", 2);
  }

  LogArchiveStore store() {
    return store;
  }

  /** The directory holding the given partition's archive of the given timeline. */
  Path partitionDirectory(final String timelineId, final int partitionId) {
    return basePath.resolve(LogArchiveKeys.partitionPrefix(timelineId, partitionId));
  }

  /** The folded view, or empty while the partition has no archive yet. */
  Optional<LogArchiveView> tryView(final String timelineId, final int partitionId) {
    try {
      return Optional.of(LogArchiveView.read(store, timelineId, partitionId));
    } catch (final LogArchiveRestoreException e) {
      return Optional.empty();
    }
  }

  /** All archived objects of a view in index order: those of the sealed chunks, then open ones. */
  static List<LogObjectJson> objects(final LogArchiveView view) {
    final var objects = new ArrayList<LogObjectJson>();
    view.chunks().forEach(chunk -> objects.addAll(view.readChunk(chunk).objects()));
    objects.addAll(view.openObjects());
    return objects;
  }

  /**
   * Reads the whole archive of a partition from seq 1 and checks it. Call this only once the
   * brokers writing to it are stopped, so that it does not change while being read.
   *
   * <p>Checks: commit entries are exactly {@code 1..N} without gaps, each parses and describes its
   * seq; seq 1 is {@code TIMELINE_STARTED}; archived objects are contiguous from the start index
   * and F only moves forward (I1); every archived snapshot's index is at most F when it is archived
   * (I5); every archived object exists with the size and sha256 its commit entry lists, and its
   * header and frames match that description; positions of application entries are contiguous
   * across all objects; every root parses and describes its seq; the folded view (root plus later
   * commit entries) describes the same objects and snapshots as the full fold.
   */
  Archive readAndVerify(final String timelineId, final int partitionId) {
    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);

    // commit entries: gapless from 1
    final var commitSeqs =
        store.list(prefix + "commits/").join().stream()
            .mapToLong(LogArchiveKeys::seqFromCommitKey)
            .sorted()
            .toArray();
    assertThat(commitSeqs).as("commit entries of %s", prefix).isNotEmpty();
    assertThat(commitSeqs)
        .as("commit entries of %s are gapless from 1", prefix)
        .containsExactly(LongStream.rangeClosed(1, commitSeqs.length).toArray());

    final var commits = new ArrayList<CommitEntry>();
    for (final long seq : commitSeqs) {
      final var bytes = store.getIfExists(prefix + LogArchiveKeys.commitKey(seq)).join();
      assertThat(bytes).as("commit entry %d of %s", seq, prefix).isPresent();
      final var entry = LogArchiveJson.read(bytes.orElseThrow(), CommitEntry.class);
      assertThat(entry.seq()).as("seq of commit entry %d", seq).isEqualTo(seq);
      assertThat(entry.partitionId()).isEqualTo(partitionId);
      assertThat(entry.timelineId()).isEqualTo(timelineId);
      commits.add(entry);
    }

    // fold: contiguity, F monotonic, snapshots below F
    final var first = commits.getFirst();
    assertThat(first.type()).as("type of seq 1").isEqualTo(Type.TIMELINE_STARTED);
    assertThat(first.startIndex()).isNotNull().isPositive();
    final long startIndex = first.startIndex();
    final String parentTimelineId = first.parentTimelineId();

    final var objects = new ArrayList<LogObjectJson>();
    final var snapshots = new ArrayList<ArchivedSnapshotJson>();
    final var chunks = new ArrayList<IndexChunkJson>();
    long archivedIndex = startIndex - 1;
    long archivedPosition = -1;
    for (final var entry : commits.subList(1, commits.size())) {
      switch (entry.type()) {
        case LOG_ARCHIVED -> {
          assertThat(entry.objects()).as("objects of commit entry %d", entry.seq()).isNotEmpty();
          for (final var object : entry.objects()) {
            assertThat(object.firstIndex())
                .as("first index of %s (commit entry %d)", object.key(), entry.seq())
                .isEqualTo(archivedIndex + 1);
            assertThat(object.lastIndex()).isGreaterThanOrEqualTo(object.firstIndex());
            assertThat(object.key())
                .isEqualTo(
                    LogArchiveKeys.dataKey(object.firstIndex(), object.lastIndex(), object.term()));
            archivedIndex = object.lastIndex();
            objects.add(object);
          }
          assertThat(entry.archivedIndex()).isEqualTo(archivedIndex);
          assertThat(entry.archivedPosition()).isGreaterThanOrEqualTo(archivedPosition);
          archivedPosition = entry.archivedPosition();
        }
        case SNAPSHOT_ARCHIVED -> {
          assertThat(entry.snapshot()).isNotNull();
          assertThat(entry.snapshot().index())
              .as("index of snapshot %s is at most F (I5)", entry.snapshot().id())
              .isLessThanOrEqualTo(archivedIndex);
          snapshots.add(entry.snapshot());
        }
        case INDEX_SEALED -> {
          assertThat(entry.chunk()).isNotNull();
          chunks.add(entry.chunk());
        }
        default ->
            throw new AssertionError(
                "unexpected commit entry type %s at seq %d".formatted(entry.type(), entry.seq()));
      }
    }

    // objects: bytes match their description
    final var frames = new ArrayList<FrameRef>();
    long lastHighestPosition = -1;
    for (final var object : objects) {
      final var bytes = store.getIfExists(prefix + object.key()).join();
      assertThat(bytes).as("archived object %s exists", object.key()).isPresent();
      final byte[] content = bytes.orElseThrow();
      assertThat((long) content.length)
          .as("size of %s", object.key())
          .isEqualTo(object.sizeBytes());
      assertThat(sha256Hex(content)).as("sha256 of %s", object.key()).isEqualTo(object.sha256());

      final var decoded = LogObjectDecoder.decode(content);
      final var header = decoded.header();
      assertThat(header.partitionId()).isEqualTo(partitionId);
      assertThat(header.timelineId()).isEqualTo(timelineId);
      assertThat(header.term()).isEqualTo(object.term());
      assertThat(header.firstIndex()).isEqualTo(object.firstIndex());
      assertThat(header.lastIndex()).isEqualTo(object.lastIndex());
      assertThat(header.firstPosition()).isEqualTo(object.firstPosition());
      assertThat(header.lastPosition()).isEqualTo(object.lastPosition());
      assertThat(decoded.frames())
          .as("frames of %s", object.key())
          .hasSize(object.entryCount())
          .hasSize((int) (object.lastIndex() - object.firstIndex() + 1));

      long expectedIndex = object.firstIndex();
      for (final var frame : decoded.frames()) {
        assertThat(frame.index()).as("frame index in %s", object.key()).isEqualTo(expectedIndex++);
        assertThat(frame.term())
            .as("term of frame %d", frame.index())
            .isLessThanOrEqualTo(header.term());
        if (frame.asqn() >= 0) {
          assertThat(frame.lowestPosition()).isEqualTo(frame.asqn());
          if (lastHighestPosition >= 0) {
            assertThat(frame.lowestPosition())
                .as("positions are contiguous at index %d (%s)", frame.index(), object.key())
                .isEqualTo(lastHighestPosition + 1);
          }
          lastHighestPosition = frame.highestPosition();
        }
        frames.add(new FrameRef(frame.index(), frame.term(), frame.asqn(), frame.checksum()));
      }
    }
    assertThat(lastHighestPosition).isEqualTo(archivedPosition);

    // roots
    final var rootKeys = store.list(prefix + "root/").join();
    for (final var rootKey : rootKeys) {
      final long seq = LogArchiveKeys.seqFromRootKey(rootKey);
      final var root =
          LogArchiveJson.read(store.getIfExists(rootKey).join().orElseThrow(), RootFile.class);
      assertThat(root.seq()).as("seq of %s", rootKey).isEqualTo(seq);
      assertThat(root.startIndex()).isEqualTo(startIndex);
    }

    // the folded view describes the same archive
    final var view = LogArchiveView.read(store, timelineId, partitionId);
    assertThat(view.lastSeq()).isEqualTo(commitSeqs.length);
    assertThat(view.startIndex()).isEqualTo(startIndex);
    assertThat(view.archivedIndex()).isEqualTo(archivedIndex);
    assertThat(view.archivedPosition()).isEqualTo(archivedPosition);
    assertThat(view.chunks()).containsExactlyElementsOf(chunks);
    assertThat(view.snapshots()).containsExactlyElementsOf(snapshots);
    assertThat(objects(view)).containsExactlyElementsOf(objects);

    final var dataKeys = store.list(prefix + "data/").join();
    return new Archive(
        view,
        startIndex,
        parentTimelineId,
        List.copyOf(commits),
        List.copyOf(objects),
        List.copyOf(snapshots),
        List.copyOf(chunks),
        List.copyOf(rootKeys),
        List.copyOf(dataKeys),
        List.copyOf(frames));
  }

  @Override
  public void close() {
    store.closeAsync().join();
  }

  private static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The identity of one archived journal entry. */
  record FrameRef(long index, long term, long asqn, long checksum) {}

  /** A fully read and verified archive of one partition of one timeline. */
  record Archive(
      LogArchiveView view,
      long startIndex,
      String parentTimelineId,
      List<CommitEntry> commits,
      List<LogObjectJson> objects,
      List<ArchivedSnapshotJson> snapshots,
      List<IndexChunkJson> chunks,
      List<String> rootKeys,
      List<String> dataKeys,
      List<FrameRef> frames) {

    long archivedIndex() {
      return view.archivedIndex();
    }

    long archivedPosition() {
      return view.archivedPosition();
    }

    /** Counts only; the record's own toString lists every entry. */
    String summary() {
      return "timeline=%s, startIndex=%d, F=(%d, %d), commits=%d, objects=%d, unlisted data objects=%d, chunks=%d, snapshots=%d, roots=%d"
          .formatted(
              view.timelineId(),
              startIndex,
              archivedIndex(),
              archivedPosition(),
              commits.size(),
              objects.size(),
              dataKeys.size() - objects.size(),
              chunks.size(),
              snapshots.size(),
              rootKeys.size());
    }
  }
}
