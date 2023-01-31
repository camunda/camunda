/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.journal.JournalRecord;
import org.agrona.DirectBuffer;

/**
 * A JournalRecord stored in a buffer.
 *
 * <p>A {@link PersistedJournalRecord} consists of two parts. The first part is {@link
 * RecordMetadata}. The second part is {@link RecordData}.
 */
public record PersistedJournalRecord(RecordMetadata metadata, RecordData record, int length)
    implements JournalRecord {

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
