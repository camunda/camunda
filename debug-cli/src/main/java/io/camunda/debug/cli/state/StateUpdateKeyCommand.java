/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.debug.cli.CommonOptions;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "update-key", description = "Overwrite the next key in the state")
public class StateUpdateKeyCommand extends CommonOptions implements Callable<Integer> {

  @Spec CommandSpec spec;
  @ParentCommand private StateCommand parentCommand;

  @Option(
      names = {"--runtime"},
      description = "Path to the temporary runtime directory",
      required = true)
  private Path runtimePath;

  @Option(
      names = {"-s", "--snapshot"},
      description = "Path to the source snapshot directory",
      required = true)
  private String snapshotId;

  @Option(
      names = {"-k", "--key"},
      description = "Key to set",
      required = true)
  private long key;

  @Option(
      names = {"--max-key"},
      description = "Max key to set")
  private Long maxKey;

  @Option(names = "--partition-id", description = "Partition ID", required = true)
  private int partitionId;

  @Override
  public Integer call() throws Exception {
    final var out = spec.commandLine().getOut();
    final var err = spec.commandLine().getErr();

    out.println("=== Starting key update process ===");

    if (verbose) {
      err.println("Partition ID: " + partitionId);
      err.println("Target key: " + key);
      if (maxKey != null) {
        err.println("Target max key: " + maxKey);
      }
      err.println("Snapshot ID: " + snapshotId);
      err.println("Runtime path: " + runtimePath);
    }

    final var snapshotUtil = new SnapshotUtil();
    final var snapshotPath =
        root.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY).resolve(snapshotId);

    if (verbose) {
      err.println("\nOpening snapshot from: " + snapshotPath);
    }
    final var db = snapshotUtil.openSnapshot(snapshotPath, runtimePath);
    final var context = db.createContext();

    final var dbKey = new DbKeyGenerator(partitionId, db, context);

    if (verbose) {
      err.println("\nExecuting key update transaction...");
    }
    context.runInTransaction(() -> context.getCurrentTransaction().run(() -> overwriteKeys(dbKey)));
    context.getCurrentTransaction().commit();

    final var lastFollowupEventPosition = SnapshotUtil.getLastFollowupEventPosition(snapshotPath);
    if (verbose) {
      err.println("\nTaking new snapshot...");
    }
    final var persistedSnapshot =
        snapshotUtil.takeSnapshot(db, root, snapshotId, lastFollowupEventPosition);

    out.println("\n=== Key update completed successfully ===");
    out.println("Created new snapshot with updated key at: " + persistedSnapshot.getPath());
    out.println("\nNext steps:");
    out.println("1. Delete the previous snapshot: " + snapshotId);
    out.println("2. Copy the data folder of partition to all replicas");
    out.println("3. Restart the brokers to apply changes to the cluster");

    return 0;
  }

  private void overwriteKeys(final DbKeyGenerator dbKey) {
    final var out = spec.commandLine().getOut();
    final var currentKey = dbKey.getCurrentKey();
    if (maxKey != null) {
      // Have to first set maxKey to ensure that  currentKey <= maxKey
      out.println("  Setting max key to: " + maxKey);
      dbKey.setMaxKeyValue(maxKey);
      out.println("  ✓ Max key set successfully");
    }

    out.println("  Current key: " + currentKey);
    out.println("  New key: " + key);

    dbKey.overwriteKey(key);
    out.println("  ✓ Key overwritten successfully");
  }
}
