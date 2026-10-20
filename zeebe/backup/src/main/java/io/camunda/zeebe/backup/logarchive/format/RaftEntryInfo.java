/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.zeebe.journal.record.SBESerializer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * What the Raft entry inside a journal entry says about itself. The segment decoder leaves the Raft
 * entry alone; readers that need the term or the positions of an entry read them here.
 *
 * @param term the term of the Raft entry
 * @param lowestPosition lowest record position, -1 if the entry is not an application entry
 * @param highestPosition highest record position, -1 if the entry is not an application entry
 */
public record RaftEntryInfo(long term, long lowestPosition, long highestPosition) {

  /**
   * @param serializedRecord the serialized {@code RecordData} of a journal entry, as in {@link
   *     LogArchiveFrame#serializedRecord()}
   * @throws LogObjectFormatException if it does not hold a Raft entry
   */
  public static RaftEntryInfo read(final byte[] serializedRecord) {
    try {
      final var data = new SBESerializer().readData(new UnsafeBuffer(serializedRecord), 0);
      final var entry = new RaftEntrySBESerializer().readRaftLogEntry(data.data());
      if (entry.entry() instanceof final ApplicationEntry application) {
        return new RaftEntryInfo(
            entry.term(), application.lowestPosition(), application.highestPosition());
      }
      return new RaftEntryInfo(entry.term(), -1, -1);
    } catch (final RuntimeException e) {
      throw new LogObjectFormatException("invalid Raft entry", e);
    }
  }
}
