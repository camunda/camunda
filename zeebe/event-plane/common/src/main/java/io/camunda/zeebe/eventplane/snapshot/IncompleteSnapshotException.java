/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import java.io.IOException;

/** A file of a snapshot is missing, or has another size than its descriptor says. */
public final class IncompleteSnapshotException extends IOException {

  /** The size of a file that isn't there. */
  public static final long MISSING = -1;

  private final String snapshotId;
  private final String fileName;
  private final long expectedSize;
  private final long actualSize;

  public IncompleteSnapshotException(
      final String snapshotId,
      final String fileName,
      final long expectedSize,
      final long actualSize) {
    super(
        "Expected file '%s' of snapshot %s to have %d bytes, but got %s"
            .formatted(
                fileName,
                snapshotId,
                expectedSize,
                actualSize == MISSING ? "no such file" : actualSize + " bytes"));
    this.snapshotId = snapshotId;
    this.fileName = fileName;
    this.expectedSize = expectedSize;
    this.actualSize = actualSize;
  }

  public String snapshotId() {
    return snapshotId;
  }

  public String fileName() {
    return fileName;
  }

  public long expectedSize() {
    return expectedSize;
  }

  /** The file's size, or {@link #MISSING} if it isn't there. */
  public long actualSize() {
    return actualSize;
  }
}
