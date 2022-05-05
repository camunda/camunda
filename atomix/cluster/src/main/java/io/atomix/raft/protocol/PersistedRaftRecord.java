/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

import io.camunda.zeebe.journal.JournalRecord;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedRaftRecord implements JournalRecord {

  private final long index;
  private final long asqn;
  private final long checksum;
  private final byte[] serializedRaftLogEntry;
  private final long term;

  public PersistedRaftRecord(
      final long term,
      final long index,
      final long asqn,
      final long checksum,
      final byte[] serializedRaftLogEntry) {
    this.index = index;
    this.asqn = asqn;
    this.checksum = checksum;
    this.serializedRaftLogEntry = serializedRaftLogEntry;
    this.term = term;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long asqn() {
    return asqn;
  }

  @Override
  public long checksum() {
    return checksum;
  }

  @Override
  public DirectBuffer data() {
    return new UnsafeBuffer(serializedRaftLogEntry);
  }

  /**
   * Returns the approximate size needed when serializing this class. The exact size depends on the
   * serializer.
   *
   * @return approximate size
   */
  public int approximateSize() {
    return serializedRaftLogEntry.length + Long.BYTES + Long.BYTES + Long.BYTES;
  }

  /**
   * Returns the term for this record
   *
   * @return term
   */
  public long term() {
    return term;
  }
}
