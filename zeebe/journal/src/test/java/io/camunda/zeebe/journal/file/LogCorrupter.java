/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.record.JournalRecordReaderUtil;
import io.camunda.zeebe.journal.record.SBESerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LogCorrupter {

  /**
   * Corrupts the record associated with the specified index, if it's present in the file.
   *
   * @param file file where the record is persisted
   * @param index index of record to be corrupted
   * @return true if the specified record was successfully corrupted; otherwise, returns false
   */
  public static boolean corruptRecord(final Path file, final long index) throws IOException {
    final byte[] bytes = Files.readAllBytes(file);

    if (!corruptRecord(bytes, index)) {
      return false;
    }

    Files.write(file, bytes);
    return true;
  }

  public static void corruptDescriptor(final Path file) throws IOException {
    final byte[] bytes = Files.readAllBytes(file);

    final byte schemaId = bytes[MessageHeaderDecoder.schemaIdEncodingOffset() + 1];
    bytes[MessageHeaderDecoder.schemaIdEncodingOffset() + 1] = (byte) ~schemaId;

    Files.write(file, bytes);
  }

  private static boolean corruptRecord(final byte[] bytes, final long targetIndex) {
    final JournalRecordReaderUtil reader = new JournalRecordReaderUtil(new SBESerializer());
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.position(SegmentDescriptorSerializer.currentEncodingLength());

    for (long index = 1;
        FrameUtil.hasValidVersion(buffer) && FrameUtil.readVersion(buffer) == 1;
        index++) {
      final var record = reader.read(buffer, index);

      if (record.index() > targetIndex) {
        break;
      } else if (record.index() == targetIndex) {
        final int lastPos = buffer.position() - 1;
        buffer.put(lastPos, (byte) ~buffer.get(lastPos));
        return true;
      }
    }

    return false;
  }
}
