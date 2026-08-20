/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Base class for offline snapshot-edit commands: opens the source snapshot into a temporary
 * runtime, lets the subclass apply its edit, and persists a new checksum-valid snapshot that
 * preserves the source snapshot's id (index/term/processed/exported positions).
 *
 * <p>Output convention: stdout carries machine-readable output only (the path of the newly created
 * snapshot); all human-readable progress goes to stderr.
 */
abstract class SnapshotEditCommand implements Callable<Integer> {

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  protected boolean verbose;

  @Spec protected CommandSpec spec;

  @Option(
      names = {"-r", "--root"},
      description =
          "Path of the partition directory (the folder containing 'snapshots/'), e.g. "
              + "<data>/raft-partition/partitions/<id>",
      required = true)
  protected Path root;

  @Option(
      names = {"--runtime"},
      description = "Path to the temporary runtime directory",
      required = true)
  protected Path runtimePath;

  @Option(
      names = {"-s", "--snapshot"},
      description = "Id of the source snapshot directory",
      required = true)
  protected String snapshotId;

  @ParentCommand private StateCommand parentCommand;

  @Override
  public final Integer call() throws Exception {
    final var err = err();

    err.println("=== Starting " + operationName() + " ===");

    if (verbose) {
      printVerboseOptions(err);
      err.println("Root path: " + root);
      err.println("Snapshot ID: " + snapshotId);
      err.println("Runtime path: " + runtimePath);
    }

    final var snapshotUtil = new SnapshotUtil();
    final var snapshotPath =
        root.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY).resolve(snapshotId);

    if (verbose) {
      err.println("\nOpening snapshot from: " + snapshotPath);
    }
    // try-with-resources so the RocksDB instance (and its file locks on --runtime) is always
    // released, including on the validation-error return path.
    try (final var db = snapshotUtil.openSnapshot(snapshotPath, runtimePath)) {
      final var context = db.createContext();

      if (verbose) {
        err.println("\nExecuting state edit transaction...");
      }
      final var error = edit(db, context);
      if (error != null) {
        err.println("Error: " + error);
        return 1;
      }

      final var lastFollowupEventPosition = SnapshotUtil.getLastFollowupEventPosition(snapshotPath);
      if (verbose) {
        err.println("\nTaking new snapshot...");
      }
      final var persistedSnapshot =
          snapshotUtil.takeSnapshot(db, root, snapshotId, lastFollowupEventPosition);

      err.println("\n=== Completed " + operationName() + " ===");
      err.println("Created new snapshot at: " + persistedSnapshot.getPath());
      err.println(
          "Delete the source snapshot '"
              + snapshotId
              + "' (and its .checksum file) before restarting the broker; see "
              + runbookReference()
              + " for the full procedure.");
      out().println(persistedSnapshot.getPath());

      return 0;
    }
  }

  protected PrintWriter out() {
    return spec.commandLine().getOut();
  }

  protected PrintWriter err() {
    return spec.commandLine().getErr();
  }

  /** Short human-readable name of the edit, used in progress messages. */
  abstract String operationName();

  /** Path of the operator runbook for this command, relative to the repository root. */
  abstract String runbookReference();

  /** Prints the subclass-specific options when {@code --verbose} is set. */
  abstract void printVerboseOptions(PrintWriter err);

  /**
   * Applies the subclass's edit on the opened runtime db, typically inside {@code
   * context.runInTransaction}. Returns {@code null} on success or a validation-error message; on
   * error no new snapshot is taken and the command exits with code 1.
   */
  abstract String edit(ZeebeDb db, TransactionContext context) throws Exception;
}
