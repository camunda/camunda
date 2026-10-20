/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

/** Builds serialized journal {@code RecordData} messages, as the journal stores them. */
public final class TestJournalRecords {

  private static final int RECORD_DATA_OVERHEAD = 8 + 8 + 8 + 4;

  private TestJournalRecords() {}

  /**
   * @return a {@code RecordData} message of {@code totalLength} bytes for the given index and asqn,
   *     the payload of which is filled with {@code fill}
   */
  public static byte[] recordData(
      final long index, final long asqn, final int totalLength, final byte fill) {
    final byte[] payload = new byte[totalLength - RECORD_DATA_OVERHEAD];
    java.util.Arrays.fill(payload, fill);
    final byte[] serialized = new byte[totalLength];
    new SBESerializer()
        .writeData(
            index,
            asqn,
            new DirectBufferWriter(new UnsafeBuffer(payload)),
            new UnsafeBuffer(serialized),
            0)
        .get();
    return serialized;
  }
}
