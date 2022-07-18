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
public class PersistedJournalRecord implements JournalRecord {
  private final RecordMetadata metadata;
  private final RecordData record;

  public PersistedJournalRecord(final RecordMetadata metadata, final RecordData record) {
    this.metadata = metadata;
    this.record = record;
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

  @Override
  public int hashCode() {
    return Objects.hash(record);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JournalRecord that = (JournalRecord) o;
    return that.index() == index()
        && that.asqn() == asqn()
        && that.checksum() == checksum()
        && Objects.equals(that.data(), data());
  }

  @Override
  public String toString() {
    return "PersistedJournalRecord{" + "metadata=" + metadata + ", record=" + record + '}';
  }
}
