/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.journal.JournalRecord;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * A JournalRecord stored in a buffer.
 *
 * <p>A {@link PersistedJournalRecord} consists of two parts. The first part is {@link
 * RecordMetadata}. The second part is {@link RecordData}.
 */
public final class PersistedJournalRecord implements JournalRecord {

  private final RecordMetadata metadata;
  private final RecordData record;

  public PersistedJournalRecord(final RecordMetadata metadata, final RecordData record) {
    this.metadata = metadata;
    this.record = record;
  }

  public RecordMetadata metadata() {
    return metadata;
  }

  public RecordData record() {
    return record;
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadata, record);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (PersistedJournalRecord) obj;
    return Objects.equals(metadata, that.metadata) && Objects.equals(record, that.record);
  }

  @Override
  public String toString() {
    return "PersistedJournalRecord[" + "metadata=" + metadata + ", " + "record=" + record + ']';
  }

  @Override
  public long index() {
    return record.index();
  }

  @Override
  public long asqn() {
    return record.asqn();
  }

  @Override
  public long checksum() {
    return metadata.checksum();
  }

  @Override
  public DirectBuffer data() {
    return record.data();
  }
}
