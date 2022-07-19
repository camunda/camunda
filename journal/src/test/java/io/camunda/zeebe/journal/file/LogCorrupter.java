/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.file.record.JournalRecordReaderUtil;
import io.camunda.zeebe.journal.file.record.SBESerializer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class LogCorrupter {

  /**
   * Corrupts the record associated with the specified index, if it's present in the file.
   *
   * @param file file where the record is persisted
   * @param index index of record to be corrupted
   * @return true if the specified record was successfully corrupted; otherwise, returns false
   */
  public static boolean corruptRecord(final File file, final long index) throws IOException {
    final byte[] bytes = new byte[Math.toIntExact(file.length())];
    int read;

    try (final BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
      final int available = in.available();
      read = in.read(bytes, 0, bytes.length);

      // reached EOF
      if (read == -1) {
        read = available;
      }
    }

    if (!corruptRecord(bytes, index)) {
      return false;
    }

    try (final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
      out.write(bytes, 0, read);
    }

    return true;
  }

  public static void corruptDescriptor(final File file) throws IOException {
    final byte[] bytes = new byte[SegmentDescriptor.getEncodingLength()];
    int read;

    try (final BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
      final int available = in.available();
      read = in.read(bytes, 0, bytes.length);

      // reached EOF
      if (read == -1) {
        read = available;
      }
    }

    final byte schemaId = bytes[MessageHeaderDecoder.schemaIdEncodingOffset() + 1];
    bytes[MessageHeaderDecoder.schemaIdEncodingOffset() + 1] = (byte) ~schemaId;

    try (final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
      out.write(bytes, 0, read);
    }
  }

  private static boolean corruptRecord(final byte[] bytes, final long targetIndex) {
    final JournalRecordReaderUtil reader = new JournalRecordReaderUtil(new SBESerializer());
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.position(SegmentDescriptor.getEncodingLength());

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
