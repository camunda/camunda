/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.util.exception.UnrecoverableException;
import java.nio.file.Path;

@SuppressWarnings("unused")
public final class InvalidSnapshotChecksum extends UnrecoverableException {
  private static final String MESSAGE_FORMAT =
      "Expected snapshot %s to have checksum %d, but was %d";
  private final transient Path snapshotPath;
  private final long expectedChecksum;
  private final long actualChecksum;

  public InvalidSnapshotChecksum(
      final Path snapshotPath, final long expectedChecksum, final long actualChecksum) {
    super(String.format(MESSAGE_FORMAT, snapshotPath, expectedChecksum, actualChecksum));

    this.snapshotPath = snapshotPath;
    this.expectedChecksum = expectedChecksum;
    this.actualChecksum = actualChecksum;
  }

  public InvalidSnapshotChecksum(
      final Throwable cause,
      final Path snapshotPath,
      final long expectedChecksum,
      final long actualChecksum) {
    super(String.format(MESSAGE_FORMAT, snapshotPath, expectedChecksum, actualChecksum), cause);

    this.snapshotPath = snapshotPath;
    this.expectedChecksum = expectedChecksum;
    this.actualChecksum = actualChecksum;
  }

  public Path getSnapshotPath() {
    return snapshotPath;
  }

  public long getExpectedChecksum() {
    return expectedChecksum;
  }

  public long getActualChecksum() {
    return actualChecksum;
  }
}
