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
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Scans every partition's snapshot for process definitions that are stuck {@code DRAINING} without
 * valid deletion coordination and can therefore never finish deleting. This is a rare remnant of a
 * pre-draining defect (a deletion in flight while the deployment queue was blocked, carried across
 * an upgrade); draining under a supported version is reliable and needs no such check. See {@link
 * ProcessDefinitionDeletionScan} for the detection rationale.
 *
 * <p>Point {@code --root} at the broker's {@code raft-partition/partitions} directory. The scan is
 * cross-partition and refuses to run on incomplete input: it reads the routing state to learn which
 * partitions the cluster has, and requires every one of them to be present with a readable snapshot
 * under {@code --root}. Gather all partitions there first if they are spread across brokers. Each
 * snapshot is copied into a throwaway runtime and read strictly read-only.
 *
 * <p>Output convention: a human-readable report goes to stderr; one machine-readable line per stuck
 * definition, plus a trailing summary line, goes to stdout. Exit {@code 0} = none found, {@code 2}
 * = stuck definitions found, {@code 1} = configuration/IO error (including missing partition data).
 */
@Command(
    name = "check-process-definition-deletions",
    description =
        "Scan all partition snapshots for process definitions stuck DRAINING without valid deletion "
            + "coordination.")
public class StateCheckProcessDefinitionDeletionsCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Option(
      names = {"-r", "--root"},
      description =
          "Path of the 'raft-partition/partitions' directory holding a subdirectory per partition. "
              + "Must contain the data of all partitions of the cluster, including partition 1.",
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
    final Path deploymentDir = partitionDirs.get(Protocol.DEPLOYMENT_PARTITION);
    if (deploymentDir == null) {
      err.println(
          "No subdirectory for the deployment partition (id "
              + Protocol.DEPLOYMENT_PARTITION
              + ") under "
              + root
              + ". The scan needs it to read the cluster's routing and deletion-coordination state.");
      return 1;
    }
    final Optional<Path> deploymentSnapshot = latestSnapshot(deploymentDir);
    if (deploymentSnapshot.isEmpty()) {
      err.println(
          "The deployment partition (id "
              + Protocol.DEPLOYMENT_PARTITION
              + ") has no snapshot under "
              + deploymentDir
              + "; cannot read routing or coordination state.");
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

    err.println("=== Scanning for stuck DRAINING process definitions ===");

    // One SnapshotUtil is shared across all partitions so its 512MB RocksDB block cache is
    // allocated once, not once per partition.
    final SnapshotUtil snapshotUtil = new SnapshotUtil();

    try {
      final var definitions = new HashMap<Long, DefinitionInfo>();
      final var definitionsWithActiveInstances = new HashSet<Long>();

      // Read the deployment partition first: it carries the routing state (which partitions exist)
      // and the deletion-coordination state used to tell a stuck drain from a healthy one.
      err.println(
          "Partition " + Protocol.DEPLOYMENT_PARTITION + ": reading " + deploymentSnapshot.get());
      final DeploymentPartitionData deployment =
          readDeploymentPartition(
              snapshotUtil,
              deploymentSnapshot.get(),
              runtimeRoot.resolve("p" + Protocol.DEPLOYMENT_PARTITION),
              definitions,
              definitionsWithActiveInstances);

      // Determine the authoritative set of partitions to scan. Fall back to the discovered
      // directories only when routing state is unavailable (very old clusters).
      final Set<Integer> expectedPartitions;
      if (deployment.currentPartitions().isEmpty()) {
        expectedPartitions = new TreeSet<>(partitionDirs.keySet());
        err.println(
            "Warning: routing state is empty; cannot verify the full partition set. Falling back to"
                + " the "
                + expectedPartitions.size()
                + " partition directories found under --root.");
      } else {
        expectedPartitions = new TreeSet<>(deployment.currentPartitions());
      }

      // Refuse to run on incomplete input: every expected partition must be present with a readable
      // snapshot, otherwise a stuck definition living only on a missing partition is silently
      // missed.
      final Map<Integer, Path> snapshotsToScan = new TreeMap<>();
      final List<Integer> missingDirs = new ArrayList<>();
      final List<Integer> missingSnapshots = new ArrayList<>();
      for (final int partitionId : expectedPartitions) {
        if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
          continue;
        }
        final Path dir = partitionDirs.get(partitionId);
        if (dir == null) {
          missingDirs.add(partitionId);
          continue;
        }
        final Optional<Path> snapshot = latestSnapshot(dir);
        if (snapshot.isEmpty()) {
          missingSnapshots.add(partitionId);
          continue;
        }
        snapshotsToScan.put(partitionId, snapshot.get());
      }
      if (!missingDirs.isEmpty() || !missingSnapshots.isEmpty()) {
        err.println("Cannot run: the partition data under --root is incomplete.");
        if (!missingDirs.isEmpty()) {
          err.println("  Missing partition directories: " + missingDirs);
        }
        if (!missingSnapshots.isEmpty()) {
          err.println("  Partitions without a snapshot: " + missingSnapshots);
        }
        err.println(
            "The check is cross-partition and needs every partition of the cluster "
                + expectedPartitions
                + ". Gather the missing partitions' data under --root and re-run.");
        return 1;
      }

      final var partitionStates = new TreeMap<Integer, Map<Long, PersistedProcessState>>();
      partitionStates.put(Protocol.DEPLOYMENT_PARTITION, deployment.states());
      for (final var entry : snapshotsToScan.entrySet()) {
        final int partitionId = entry.getKey();
        err.println("Partition " + partitionId + ": reading " + entry.getValue());
        final Map<Long, PersistedProcessState> states =
            readPartition(
                snapshotUtil,
                entry.getValue(),
                runtimeRoot.resolve("p" + partitionId),
                definitions,
                definitionsWithActiveInstances);
        partitionStates.put(partitionId, states);
      }

      final List<Finding> findings =
          new ProcessDefinitionDeletionScan(Protocol.DEPLOYMENT_PARTITION)
              .scan(
                  partitionStates,
                  definitions,
                  deployment.pendingByDefinition(),
                  definitionsWithActiveInstances);

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

  private static Map<Long, PersistedProcessState> readPartition(
      final SnapshotUtil snapshotUtil,
      final Path snapshotPath,
      final Path runtime,
      final Map<Long, DefinitionInfo> definitions,
      final Set<Long> definitionsWithActiveInstances)
      throws Exception {
    try (final ZeebeDb<ZbColumnFamilies> db = snapshotUtil.openReadOnly(snapshotPath, runtime)) {
      final TransactionContext context = db.createContext();
      final var states = new LinkedHashMap<Long, PersistedProcessState>();
      readProcessCacheAndInstances(
          db, context, definitions, definitionsWithActiveInstances, states);
      return states;
    }
  }

  /**
   * The deployment partition additionally holds the routing state and the pending-deletion
   * coordination entries that only it has.
   */
  private static DeploymentPartitionData readDeploymentPartition(
      final SnapshotUtil snapshotUtil,
      final Path snapshotPath,
      final Path runtime,
      final Map<Long, DefinitionInfo> definitions,
      final Set<Long> definitionsWithActiveInstances)
      throws Exception {
    try (final ZeebeDb<ZbColumnFamilies> db = snapshotUtil.openReadOnly(snapshotPath, runtime)) {
      final TransactionContext context = db.createContext();
      final var states = new LinkedHashMap<Long, PersistedProcessState>();
      final var pendingByDefinition = new HashMap<Long, Set<Integer>>();
      readProcessCacheAndInstances(
          db, context, definitions, definitionsWithActiveInstances, states);
      readPendingDeletions(db, context, pendingByDefinition);
      final var currentPartitions =
          new TreeSet<>(new DbRoutingState(db, context).currentPartitions());
      return new DeploymentPartitionData(states, pendingByDefinition, currentPartitions);
    }
  }

  // DefinitionInfo fields are copied out during iteration because the persisted process's buffers
  // are only valid for the duration of the forEachProcess callback.
  private static void readProcessCacheAndInstances(
      final ZeebeDb<ZbColumnFamilies> db,
      final TransactionContext context,
      final Map<Long, DefinitionInfo> definitions,
      final Set<Long> definitionsWithActiveInstances,
      final Map<Long, PersistedProcessState> states) {
    SnapshotUtil.openProcessState(db, context)
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
                        process.getTenantId()));
              }
              return true;
            });

    // Only DRAINING definitions can be stuck, so limit the (potentially large) instance lookup to
    // them. Reuse the engine's own active-instance check so the definition of "active" stays in
    // sync with how the engine finalizes a drain.
    final var elementInstanceState =
        new DbElementInstanceState(db, context, new DbVariableState(db, context));
    for (final var entry : states.entrySet()) {
      if (entry.getValue() != PersistedProcessState.DRAINING
          || definitionsWithActiveInstances.contains(entry.getKey())) {
        continue;
      }
      if (elementInstanceState.hasActiveProcessInstances(entry.getKey(), List.of())) {
        definitionsWithActiveInstances.add(entry.getKey());
      }
    }
  }

  private static void readPendingDeletions(
      final ZeebeDb<ZbColumnFamilies> db,
      final TransactionContext context,
      final Map<Long, Set<Integer>> pendingByDefinition) {
    final DbLong processDefinitionKey = new DbLong();
    final DbInt partitionId = new DbInt();
    final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> pendingDeletions =
        db.createColumnFamily(
            ZbColumnFamilies.PENDING_PROCESS_DELETIONS_PER_PARTITION,
            context,
            new DbCompositeKey<>(processDefinitionKey, partitionId),
            DbNil.INSTANCE);
    pendingDeletions.forEach(
        (key, nil) ->
            pendingByDefinition
                .computeIfAbsent(key.first().getValue(), ignored -> new HashSet<>())
                .add(key.second().getValue()));
  }

  private void report(
      final PrintWriter err,
      final PrintWriter out,
      final Iterable<Integer> scannedPartitions,
      final List<Finding> findings) {
    err.println("=== Done ===");
    err.println("Partitions scanned: " + scannedPartitions);
    err.println("Stuck definitions: " + findings.size());
    if (!findings.isEmpty()) {
      reportFindingDetails(err, findings);
      reportRemediation(err);
    }

    // Machine-readable stdout: keep this format byte-stable, downstream tooling parses it.
    for (final Finding f : findings) {
      final DefinitionInfo d = f.definition();
      out.printf(
          "key=%d bpmnProcessId=%s version=%d tenant=%s draining=%s uncoordinated=%s "
              + "orphanedInstances=%s%n",
          d.processDefinitionKey(),
          d.bpmnProcessId(),
          d.version(),
          d.tenantId(),
          f.drainingPartitions(),
          f.uncoordinatedPartitions(),
          f.orphanedInstances());
    }
    out.printf("stranded=%d partitions=%s%n", findings.size(), scannedPartitions);
  }

  private static void reportFindingDetails(final PrintWriter err, final List<Finding> findings) {
    err.println();
    err.println("Stuck process definitions");
    err.println("-------------------------");
    int index = 0;
    for (final Finding f : findings) {
      final DefinitionInfo d = f.definition();
      err.printf(
          "[%d] %s  (processDefinitionKey %d)%n",
          ++index, d.bpmnProcessId(), d.processDefinitionKey());
      err.printf("    version            %d%n", d.version());
      err.printf("    tenant             %s%n", d.tenantId());
      err.printf(
          "    draining on        partitions %s  - still hold the definition, waiting to drain%n",
          f.drainingPartitions());
      err.printf(
          "    no coordination on partitions %s  - deletion cannot finish here%n",
          f.uncoordinatedPartitions());
      err.printf(
          "    orphaned instances %s  - %s%n",
          f.orphanedInstances() ? "yes" : "no",
          f.orphanedInstances()
              ? "running instances remain and must be finished first"
              : "no running instances remain");
    }
  }

  private static void reportRemediation(final PrintWriter err) {
    err.println();
    err.println("How to resolve");
    err.println("--------------");
    err.println("For each stuck definition above:");
    err.println(
        "  1. If it has orphaned instances, complete, terminate, or migrate them so the deletion");
    err.println("     can finish.");
    err.println(
        "  2. Then decide on history: if the definition's history should also be removed, re-issue");
    err.println(
        "     resource deletion with deleteHistory=true to purge the leftover history from secondary");
    err.println(
        "     storage. The original deletion's history intent is not recoverable from the state, so");
    err.println("     this is a deliberate choice.");
    err.println("  3. Re-run this scan; a resolved cluster reports stranded=0.");
  }

  private record DeploymentPartitionData(
      Map<Long, PersistedProcessState> states,
      Map<Long, Set<Integer>> pendingByDefinition,
      Set<Integer> currentPartitions) {}
}
