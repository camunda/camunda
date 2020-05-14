/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class ChecksumUtil {

  private ChecksumUtil() {}

  /** computes a checksum for the files, in the order they're presented */
  public static long createCombinedChecksum(final List<Path> paths) throws IOException {
    final CRC32 checksumGenerator = new CRC32();
    final List<Long> chunkChecksum = new ArrayList<>();

    for (final var path : paths) {
      checksumGenerator.update(Files.readAllBytes(path));
      chunkChecksum.add(checksumGenerator.getValue());
      checksumGenerator.reset();
    }

    chunkChecksum.forEach(
        c -> checksumGenerator.update(ByteBuffer.allocate(Long.BYTES).putLong(0, c)));
    return checksumGenerator.getValue();
  }
}
