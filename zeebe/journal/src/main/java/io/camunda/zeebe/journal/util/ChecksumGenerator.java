/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.util;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;
import org.agrona.DirectBuffer;

public final class ChecksumGenerator {

  private final CRC32C crc32 = new CRC32C();

  public long compute(final DirectBuffer buffer, final int offset, final int length) {
    if (buffer.byteArray() != null) {
      return compute(buffer.byteArray(), offset, length);
    } else if (buffer.byteBuffer() != null) {
      // compute makes a copy of the ByteBuffer already
      return compute(buffer.byteBuffer(), offset, length);
    } else {
      throw new IllegalStateException(
          "Provided DirectBuffer does not have either a byteArray or a byteBuffer");
    }
  }

  /** Compute checksum of given ByteBuffer */
  public long compute(final ByteBuffer buffer, final int offset, final int length) {
    final var slice = buffer.asReadOnlyBuffer().position(offset).slice();
    crc32.reset();
    crc32.update(slice.limit(length));
    return crc32.getValue();
  }

  public long compute(final byte[] byteArray, final int offset, final int length) {
    crc32.reset();
    crc32.update(byteArray, offset, length);
    return crc32.getValue();
  }
}
