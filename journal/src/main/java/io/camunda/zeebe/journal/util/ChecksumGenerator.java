/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.util;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

public final class ChecksumGenerator {

  private final CRC32C crc32 = new CRC32C();

  /** Compute checksum of given ByteBuffer */
  public long compute(final ByteBuffer buffer, final int offset, final int length) {
    final var slice = buffer.asReadOnlyBuffer().position(offset).slice();
    crc32.reset();
    crc32.update(slice.limit(length));
    return crc32.getValue();
  }
}
