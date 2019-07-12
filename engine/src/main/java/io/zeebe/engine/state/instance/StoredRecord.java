/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class StoredRecord implements DbValue {

  private static final int PURPOSE_OFFSET = 0;
  private static final int PURPOSE_LENGTH = BitUtil.SIZE_OF_BYTE;
  private static final int RECORD_OFFSET = PURPOSE_LENGTH;

  private final IndexedRecord record;
  private Purpose purpose;

  public StoredRecord(IndexedRecord record, Purpose purpose) {
    this.record = record;
    this.purpose = purpose;
  }

  /** deserialization constructor */
  public StoredRecord() {
    this.record = new IndexedRecord();
  }

  public IndexedRecord getRecord() {
    return record;
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public long getKey() {
    return record.getKey();
  }

  @Override
  public int getLength() {
    return PURPOSE_LENGTH + record.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putByte(PURPOSE_OFFSET, (byte) purpose.ordinal());
    record.write(buffer, RECORD_OFFSET);
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int purposeOrdinal = buffer.getByte(offset + PURPOSE_OFFSET);
    purpose = Purpose.values()[purposeOrdinal];
    record.wrap(buffer, offset + RECORD_OFFSET, length - PURPOSE_LENGTH);
  }

  public enum Purpose {
    // Order is important, as we use the ordinal for persistence
    DEFERRED,
    FAILED
  }
}
