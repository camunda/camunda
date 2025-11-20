/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "log",
    description = "Truncate the log of a specific partition to a given index",
    subcommands = {LogCommand.StatusCommand.class, LogCommand.TruncateCommand.class})
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
      final var snapshotIndex = snapshotStore.getCurrentSnapshotIndex();
      System.out.println("Current snapshot index: " + snapshotIndex);

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
      if (verifySnapshot) {
        final var snapshotStore = logCommand.createSnapshotStore();
        final var snapshotIndex = snapshotStore.getCurrentSnapshotIndex();
        if (snapshotIndex != 0 && index < snapshotIndex) {
          System.err.printf(
              "Cannot truncate log to index %d as it is less than the current snapshot index %d%n",
              index, snapshotIndex);
          return 1;
        }
      }

      try (final var metaStore = storage.openMetaStore()) {
        try (final var log =
            storage.openLog(metaStore, () -> new SingleThreadContext("log-truncate-%d"))) {
          final var lastIndex = log.getLastIndex();
          log.setCommitIndex(index);
          log.deleteAfter(index);
          System.out.printf("Truncated log index from %s to %s %n", lastIndex, index);
        }
      } catch (final Exception e) {
        e.printStackTrace(System.err);
        return 1;
      }
      return 0;
    }
  }
}
