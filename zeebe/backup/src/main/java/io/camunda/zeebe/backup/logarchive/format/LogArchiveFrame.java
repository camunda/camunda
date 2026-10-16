/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import java.util.Arrays;
import java.util.Objects;

/**
 * One journal entry of a log object. The serialized record and checksum are taken from {@code
 * IndexedRaftLogEntry#getReplicatableJournalRecord()}. Uses a plain byte array to keep the codec
 * free of buffer ownership concerns; the array is not copied and must not be mutated.
 *
 * @param asqn application sequence number, -1 for non-application entries
 * @param lowestPosition lowest record position of the application entry, -1 otherwise
 * @param highestPosition highest record position of the application entry, -1 otherwise
 */
public record LogArchiveFrame(
    long index,
    long term,
    long asqn,
    long lowestPosition,
    long highestPosition,
    long checksum,
    byte[] serializedRecord) {

  public LogArchiveFrame {
    Objects.requireNonNull(serializedRecord, "serializedRecord");
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final LogArchiveFrame f
        && index == f.index
        && term == f.term
        && asqn == f.asqn
        && lowestPosition == f.lowestPosition
        && highestPosition == f.highestPosition
        && checksum == f.checksum
        && Arrays.equals(serializedRecord, f.serializedRecord);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, term, asqn, lowestPosition, highestPosition, checksum) * 31
        + Arrays.hashCode(serializedRecord);
  }

  @Override
  public String toString() {
    return "LogArchiveFrame[index=%d, term=%d, asqn=%d, positions=%d..%d, bytes=%d]"
        .formatted(index, term, asqn, lowestPosition, highestPosition, serializedRecord.length);
  }
}
