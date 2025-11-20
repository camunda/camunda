/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.log.entry.UnserializedApplicationEntry;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.camunda.debug.cli.LogCommand.StatusCommand;
import io.camunda.debug.cli.LogCommand.TruncateCommand;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryImpl;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.camunda.zeebe.stream.impl.state.DbLastProcessedPositionState;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "log",
    description = "Truncate the log of a specific partition to a given index",
    subcommands = {StatusCommand.class, TruncateCommand.class})
class LogCommand extends CommonOptions implements Callable<Integer> {
  @Option(
      names = {"-p", "--partition-id"},
      description = "Partition ID to display the log status for",
      required = true)
  int partitionId;

  @Override
  public Integer call() {
    return 0;
  }

  private Path partitionDirectory() {
    final var directory =
        root.resolve("raft-partition").resolve("partitions").resolve(Integer.toString(partitionId));
    if (!directory.toFile().exists()) {
      throw new IllegalArgumentException("Partition directory not found: " + directory);
    }
    return directory;
  }

  ConcurrencyControl createConcurrencyControl() {
    return new Actor() {
      @Override
      protected Map<String, String> createContext() {
        return super.createContext();
      }
    };
  }

  FileBasedSnapshotStoreImpl createSnapshotStore() {
    final var snapshotStore =
        new FileBasedSnapshotStoreImpl(
            1,
            partitionDirectory(),
            (path) -> Map.of(),
            createConcurrencyControl(),
            new SnapshotMetrics(new SimpleMeterRegistry()));
    snapshotStore.start();
    return snapshotStore;
  }

  private ZeebeDb<ZbColumnFamilies> openSnapshot(final File snapshotDirectory) {
    final var dbFactory = dbFactory();
    return dbFactory.openSnapshotOnlyDb(snapshotDirectory);
  }

  private ZeebeRocksDbFactory<ZbColumnFamilies> dbFactory() {
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        new ConsistencyChecksSettings(false, false),
        new AccessMetricsConfiguration(Kind.NONE, partitionId),
        SimpleMeterRegistry::new);
  }

  long loadLastProcessedPosition(final FileBasedSnapshotStoreImpl snapshotStore) {
    final var latestSnapshot = snapshotStore.getLatestSnapshot();
    if (latestSnapshot.isEmpty()) {
      return -1;
    }

    // Create a temporary copy of the snapshot to avoid accidentally modifying it
    try (final var snapshotDb = openSnapshot(latestSnapshot.get().getPath().toFile())) {
      final var tmp = Files.createTempDirectory("camunda-debug-").resolve("snapshot").toFile();
      snapshotDb.createSnapshot(tmp);
      try (final var db = dbFactory().createDb(tmp)) {
        final var lastProcessedPositionState =
            new DbLastProcessedPositionState(db, db.createContext());
        return lastProcessedPositionState.getLastSuccessfulProcessedRecordPosition();
      } finally {
        FileUtil.deleteFolderIfExists(tmp.toPath());
      }
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  RaftStorage createRaftStorage() {
    final var partitionPrefix = "raft-partition-partition-%d".formatted(partitionId);
    return RaftStorage.builder(new SimpleMeterRegistry())
        .withPartitionId(partitionId)
        .withPrefix(partitionPrefix)
        .withDirectory(partitionDirectory().toFile())
        .build();
  }

  @Command(name = "status", description = "Display the current status of the log")
  static class StatusCommand implements Callable<Integer> {
    @ParentCommand private LogCommand logCommand;

    @Override
    public Integer call() {
      final var snapshotStore = logCommand.createSnapshotStore();
      System.out.println("Current snapshot index: " + snapshotStore.getCurrentSnapshotIndex());
      System.out.println(
          "Last processed position: " + logCommand.loadLastProcessedPosition(snapshotStore));

      final var storage = logCommand.createRaftStorage();
      try (final var metaStore = storage.openMetaStore();
          final var log =
              storage.openLog(metaStore, () -> new SingleThreadContext("log-truncate-%d"))) {
        System.out.println("Current log index: " + log.getLastIndex());
      }
      return 0;
    }
  }

  @Command(name = "truncate", description = "Truncate the log to a given index")
  static class TruncateCommand implements Callable<Integer> {
    @ParentCommand private LogCommand logCommand;

    @Option(
        names = {"-s", "--verify-snapshot"},
        description =
            "Whether to verify that the given index is not less than the current snapshot index")
    private boolean verifySnapshot = true;

    @Parameters(index = "0", description = "The index to truncate the log to")
    private long index;

    @Override
    public Integer call() {
      final var storage = logCommand.createRaftStorage();
      final long snapshotIndex;
      final long snapshotPosition;
      if (verifySnapshot) {
        final var snapshotStore = logCommand.createSnapshotStore();
        snapshotIndex = snapshotStore.getCurrentSnapshotIndex();
        snapshotPosition = logCommand.loadLastProcessedPosition(snapshotStore);

        if (snapshotIndex != 0 && index < snapshotIndex) {
          System.err.printf(
              "Cannot truncate log to index %d as it is less than the current snapshot index %d%n",
              index, snapshotIndex);
          return 1;
        }
      } else {
        snapshotIndex = 0;
        snapshotPosition = 0;
      }

      try (final var metaStore = storage.openMetaStore()) {
        try (final var log =
            storage.openLog(metaStore, () -> new SingleThreadContext("log-truncate-%d"))) {
          final var lastIndex = log.getLastIndex();
          log.setCommitIndex(index);
          log.deleteAfter(index);
          System.out.printf("Truncated log index from %s to %s %n", lastIndex, index);

          if (snapshotIndex != 0) {
            try (final var reader = log.openCommittedReader()) {
              long latestPosition = -1;
              while (reader.hasNext()) {
                final var entry = reader.next();
                if (entry.isApplicationEntry()) {
                  latestPosition = entry.getApplicationEntry().highestPosition();
                  if (latestPosition >= snapshotPosition) {
                    System.out.printf(
                        "Found log entry with position %s at index %d to match snapshot position %d%n",
                        entry.getApplicationEntry().highestPosition(),
                        entry.index(),
                        snapshotPosition);
                    return 0;
                  }
                }
              }
              System.out.println("No log entries found after snapshot index.");
              final var dummyEntry =
                  log.append(
                      new RaftLogEntry(
                          metaStore.loadTerm(), fillerEntries(latestPosition, snapshotPosition)));
              System.out.printf(
                  "Wrote entries to match snapshot position %d: %s.%n",
                  snapshotPosition, dummyEntry);
              log.setCommitIndex(dummyEntry.index());
            }
          }
        }
      } catch (final Exception e) {
        e.printStackTrace(System.err);
        return 1;
      }
      return 0;
    }

    private static UnserializedApplicationEntry fillerEntries(
        final long latestPosition, final long snapshotPosition) {
      final var lowestPosition = latestPosition + 1;
      final var requiredEvents = snapshotPosition - lowestPosition;
      final var highestPosition = lowestPosition + requiredEvents;
      final var entries = new LinkedList<LogAppendEntry>();
      for (long p = lowestPosition; p <= highestPosition; p++) {
        entries.add(
            new LogAppendEntryImpl(
                -1,
                -1,
                new RecordMetadata()
                    .intent(Intent.UNKNOWN)
                    .recordType(RecordType.NULL_VAL)
                    .valueType(ValueType.SBE_UNKNOWN),
                new UnifiedRecordValue(0)));
      }
      return new UnserializedApplicationEntry(
          lowestPosition,
          highestPosition,
          new SequencedBatch(System.currentTimeMillis(), lowestPosition, -1, entries));
    }
  }
}
