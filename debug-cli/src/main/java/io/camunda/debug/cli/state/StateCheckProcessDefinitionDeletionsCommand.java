/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static io.camunda.debug.cli.util.ErrorMessageUtil.rootMessage;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.debug.cli.state.ProcessDefinitionDeletionScan.DefinitionInfo;
import io.camunda.debug.cli.state.ProcessDefinitionDeletionScan.Finding;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Scans every partition's snapshot for process definitions that were <b>partially deleted</b> —
 * left {@code DRAINING} on some partitions while already gone from the deployment partition — and
 * can therefore never reconcile. This is a rare remnant of a pre-draining defect (a deletion issued
 * while the deployment queue was blocked); draining under a supported version is reliable and needs
 * no such check. See {@link ProcessDefinitionDeletionScan} for the detection rationale.
 *
 * <p>Point {@code --root} at the broker's {@code raft-partition/partitions} directory. The command
 * reads the latest snapshot of every partition subdirectory it finds, so that directory must hold
 * <b>all</b> partitions of the cluster (including the deployment partition); gather them first if
 * they are spread across brokers. Each snapshot is copied into a throwaway runtime and read
 * strictly read-only.
 *
 * <p>Output convention: a human-readable report goes to stderr; one machine-readable line per
 * stranded definition, plus a trailing summary line, goes to stdout. Exit {@code 0} = none found,
 * {@code 2} = stranded definitions found, {@code 1} = configuration/IO error.
 */
@Command(
    name = "check-process-definition-deletions",
    description =
        "Scan all partition snapshots for process definitions partially deleted across partitions "
            + "(DRAINING on some, gone from the deployment partition).")
public class StateCheckProcessDefinitionDeletionsCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Option(
      names = {"-r", "--root"},
      description =
          "Path of the 'raft-partition/partitions' directory holding a subdirectory per partition. "
              + "Must contain all partitions of the cluster, including the deployment partition.",
      required = true)
  private Path root;

  @Option(
      names = {"--runtime"},
      description =
          "Path to a temporary runtime directory the snapshots are copied into before reading. "
              + "A fresh temp directory is created and deleted automatically if omitted.")
  private Path runtimePath;

  @Override
  public Integer call() throws Exception {
    final PrintWriter out = spec.commandLine().getOut();
    final PrintWriter err = spec.commandLine().getErr();

    if (!Files.isDirectory(root)) {
      err.println("Partitions directory does not exist: " + root);
      return 1;
    }

    final Map<Integer, Path> partitionDirs = findPartitionDirs();
    if (partitionDirs.isEmpty()) {
      err.println("No partition subdirectories found under " + root);
      return 1;
    }
    if (!partitionDirs.containsKey(Protocol.DEPLOYMENT_PARTITION)) {
      err.println(
          "No subdirectory for the deployment partition (id "
              + Protocol.DEPLOYMENT_PARTITION
              + ") under "
              + root
              + ". The scan needs it to tell whether a definition was deleted there.");
      return 1;
    }

    final Path runtimeRoot;
    final Path ephemeralParent;
    if (runtimePath == null) {
      try {
        ephemeralParent = Files.createTempDirectory("cdbg-check-pd-deletions-");
      } catch (final IOException e) {
        err.println(
            "Failed to create a temporary runtime directory under "
                + System.getProperty("java.io.tmpdir")
                + ": "
                + rootMessage(e)
                + ". Retry with --runtime pointing at a directory the current user can write to.");
        return 1;
      }
      runtimeRoot = ephemeralParent;
    } else {
      ephemeralParent = null;
      runtimeRoot = runtimePath;
    }

    // Each partition's snapshot is checkpointed into runtimeRoot/p<id>; RocksDB requires that
    // target's parent to already exist.
    Files.createDirectories(runtimeRoot);

    err.println("=== Scanning for partially-deleted process definitions ===");

    try {
      final var partitionStates = new TreeMap<Integer, Map<Long, PersistedProcessState>>();
      final var definitions = new HashMap<Long, DefinitionInfo>();

      for (final var partition : partitionDirs.entrySet()) {
        final int partitionId = partition.getKey();
        final Optional<Path> snapshot = latestSnapshot(partition.getValue());
        if (snapshot.isEmpty()) {
          if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
            err.println(
                "The deployment partition (id "
                    + partitionId
                    + ") has no snapshot under "
                    + partition.getValue()
                    + "; cannot determine deletions.");
            return 1;
          }
          err.println("Skipping partition " + partitionId + ": no snapshot found.");
          continue;
        }

        err.println("Partition " + partitionId + ": reading " + snapshot.get());
        final Map<Long, PersistedProcessState> states =
            readProcessStates(snapshot.get(), runtimeRoot.resolve("p" + partitionId), definitions);
        partitionStates.put(partitionId, states);
      }

      final List<Finding> findings =
          new ProcessDefinitionDeletionScan(Protocol.DEPLOYMENT_PARTITION)
              .scan(partitionStates, definitions);

      report(err, out, partitionStates.keySet(), findings);
      return findings.isEmpty() ? 0 : 2;
    } finally {
      if (ephemeralParent != null) {
        FileUtil.deleteFolderIfExists(ephemeralParent);
      }
    }
  }

  /** Maps each integer-named subdirectory of {@code --root} to its path. */
  private Map<Integer, Path> findPartitionDirs() {
    final var dirs = new TreeMap<Integer, Path>();
    try (final Stream<Path> children = Files.list(root)) {
      children
          .filter(Files::isDirectory)
          .forEach(
              child -> {
                final String name = child.getFileName().toString();
                if (name.chars().allMatch(Character::isDigit) && !name.isEmpty()) {
                  dirs.put(Integer.parseInt(name), child);
                }
              });
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list partitions under " + root, e);
    }
    return dirs;
  }

  /**
   * Picks the latest snapshot directory (highest snapshot id) under {@code
   * <partitionDir>/snapshots}.
   */
  private static Optional<Path> latestSnapshot(final Path partitionDir) {
    final Path snapshotsDir = partitionDir.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY);
    if (!Files.isDirectory(snapshotsDir)) {
      return Optional.empty();
    }
    try (final Stream<Path> snapshots = Files.list(snapshotsDir)) {
      return snapshots
          .filter(Files::isDirectory)
          .filter(dir -> parseSnapshotId(dir).isPresent())
          .max(Comparator.comparing(dir -> parseSnapshotId(dir).orElseThrow()));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list snapshots under " + snapshotsDir, e);
    }
  }

  private static Optional<FileBasedSnapshotId> parseSnapshotId(final Path snapshotDir) {
    try {
      return Optional.of(FileBasedSnapshotId.ofPath(snapshotDir).getOrThrow());
    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  /**
   * Reads a partition snapshot's {@code PROCESS_CACHE} into a definition-key → state map, and
   * records display metadata for every definition it sees {@code DRAINING} (fields are copied out
   * because the persisted process's buffers are only valid during the iteration).
   */
  private static Map<Long, PersistedProcessState> readProcessStates(
      final Path snapshotPath, final Path runtime, final Map<Long, DefinitionInfo> definitions)
      throws Exception {
    final Map<Long, PersistedProcessState> states = new LinkedHashMap<>();
    try (final ZeebeDb<ZbColumnFamilies> db = openReadOnly(snapshotPath, runtime)) {
      openProcessState(db)
          .forEachProcess(
              null,
              process -> {
                final long key = process.getKey();
                final PersistedProcessState state = process.getState();
                states.put(key, state);
                if (state == PersistedProcessState.DRAINING) {
                  definitions.putIfAbsent(
                      key,
                      new DefinitionInfo(
                          key,
                          bufferAsString(process.getBpmnProcessId()),
                          process.getVersion(),
                          process.getTenantId(),
                          process.isDeleteHistory()));
                }
                return true;
              });
    }
    return states;
  }

  @SuppressWarnings("unchecked")
  private static ZeebeDb<ZbColumnFamilies> openReadOnly(
      final Path snapshotPath, final Path runtime) {
    return (ZeebeDb<ZbColumnFamilies>) new SnapshotUtil().openSnapshot(snapshotPath, runtime);
  }

  private static DbProcessState openProcessState(final ZeebeDb<ZbColumnFamilies> db) {
    final var stateTransformer =
        BpmnFactory.createTransformer(
            InstantSource.fixed(Instant.EPOCH),
            ExpressionLanguageMetrics.noop(),
            Integer.MAX_VALUE);
    return new DbProcessState(db, db.createContext(), new EngineConfiguration(), stateTransformer);
  }

  private void report(
      final PrintWriter err,
      final PrintWriter out,
      final Iterable<Integer> scannedPartitions,
      final List<Finding> findings) {
    // Human-readable overview on stderr.
    err.println("=== Done ===");
    err.println("Partitions scanned: " + scannedPartitions);
    err.println("Stranded definitions: " + findings.size());
    if (!findings.isEmpty()) {
      reportFindingDetails(err, findings);
      reportRemediation(err);
    }

    // Machine-readable output on stdout: one stable line per finding plus a summary line. Keep this
    // format byte-stable; downstream tooling parses it.
    for (final Finding f : findings) {
      final DefinitionInfo d = f.definition();
      out.printf(
          "key=%d bpmnProcessId=%s version=%d tenant=%s deleteHistory=%s draining=%s absent=%s%n",
          d.processDefinitionKey(),
          d.bpmnProcessId(),
          d.version(),
          d.tenantId(),
          d.deleteHistory(),
          f.drainingPartitions(),
          f.absentPartitions());
    }
    out.printf("stranded=%d partitions=%s%n", findings.size(), scannedPartitions);
  }

  private static void reportFindingDetails(final PrintWriter err, final List<Finding> findings) {
    err.println();
    err.println("Stranded process definitions");
    err.println("----------------------------");
    int index = 0;
    for (final Finding f : findings) {
      final DefinitionInfo d = f.definition();
      err.printf(
          "[%d] %s  (processDefinitionKey %d)%n",
          ++index, d.bpmnProcessId(), d.processDefinitionKey());
      err.printf("    version        %d%n", d.version());
      err.printf("    tenant         %s%n", d.tenantId());
      err.printf("    deleteHistory  %s%n", d.deleteHistory());
      err.printf(
          "    draining on    partitions %s  - still hold the definition, waiting to drain%n",
          f.drainingPartitions());
      err.printf(
          "    absent from    partitions %s  - deployment partition, already deleted%n",
          f.absentPartitions());
    }
  }

  private static void reportRemediation(final PrintWriter err) {
    err.println();
    err.println("How to resolve");
    err.println("--------------");
    err.println("For each stranded definition above:");
    err.println(
        "  1. Complete, terminate, or migrate its running instances so the deletion can complete.");
    err.println(
        "  2. Then re-issue resource deletion with deleteHistory=true to purge the leftover history");
    err.println("     from secondary storage.");
    err.println("  3. Re-run this scan; a resolved cluster reports stranded=0.");
  }
}
