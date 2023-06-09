/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.utils;

import java.util.function.LongConsumer;
import org.slf4j.Logger;

public class StateUtil {

  /** throws IllegalStateException if state is inconsistent */
  public static void verifySnapshotLogConsistent(
      final long partitionId,
      final long snapshotIndex,
      final long firstIndex,
      final boolean isLogEmpty,
      final LongConsumer logResetter,
      final Logger log) {
    final boolean noGapExists =
        firstIndex == 1 || (snapshotIndex > 0L && snapshotIndex + 1 >= firstIndex);

    if (noGapExists) {
      return;
    }

    if (!isLogEmpty) {
      // There is a gap between snapshot and first log entry
      throw new IllegalStateException(
          String.format(
              "In partition %d expected to find a snapshot at index >= log's first index %d,"
                  + " but found snapshot %d. A previous snapshot is most likely corrupted.",
              partitionId, firstIndex, snapshotIndex));
    } else {
      log.info(
          "In partition %d current snapshot index ({}) is lower than log's first index {}. "
              + "But the log is empty. Most likely the node crashed while committing a snapshot "
              + "at index {}. Resetting log to {}",
          partitionId, snapshotIndex, firstIndex, firstIndex - 1, snapshotIndex + 1);
      logResetter.accept(snapshotIndex + 1);
    }
  }
}
