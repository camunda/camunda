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
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "update-key", description = "Overwrite the next key in the state")
public class StateUpdateKeyCommand extends SnapshotEditCommand {

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
  String operationName() {
    return "key update";
  }

  @Override
  String runbookReference() {
    return "debug-cli/scripts/overwrite-key/README.md";
  }

  @Override
  void printVerboseOptions(final PrintWriter err) {
    err.println("Partition ID: " + partitionId);
    err.println("Target key: " + key);
    if (maxKey != null) {
      err.println("Target max key: " + maxKey);
    }
  }

  @Override
  String edit(final ZeebeDb db, final TransactionContext context) {
    final var dbKey = new DbKeyGenerator(partitionId, db, context);
    context.runInTransaction(() -> overwriteKeys(dbKey));
    return null;
  }

  private void overwriteKeys(final DbKeyGenerator dbKey) {
    final var err = err();
    final var currentKey = dbKey.getCurrentKey();
    if (maxKey != null) {
      // Have to first set maxKey to ensure that  currentKey <= maxKey
      err.println("  Setting max key to: " + maxKey);
      dbKey.setMaxKeyValue(maxKey);
      err.println("  ✓ Max key set successfully");
    }

    err.println("  Current key: " + currentKey);
    err.println("  New key: " + key);

    dbKey.overwriteKey(key);
    err.println("  ✓ Key overwritten successfully");
  }
}
