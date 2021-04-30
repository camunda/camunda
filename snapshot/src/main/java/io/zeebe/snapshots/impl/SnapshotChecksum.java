/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.Checksum;
import org.agrona.IoUtil;

final class SnapshotChecksum {

  private SnapshotChecksum() {
    throw new IllegalStateException("Utility class");
  }

  public static long read(final Path checksumPath) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "r")) {
      return checksumFile.readLong();
    }
  }

  public static long calculate(final Path snapshotDirectory) throws IOException {
    try (final var fileStream = Files.list(snapshotDirectory).sorted()) {
      return createCombinedChecksum(fileStream.collect(Collectors.toList()));
    }
  }

  public static void persist(final Path checksumPath, final long checksum) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "rwd")) {
      checksumFile.writeLong(checksum);
    }
  }

  /** computes a checksum for the files, in the order they're presented */
  private static long createCombinedChecksum(final List<Path> paths) throws IOException {
    final Checksum checksum = SnapshotChunkUtil.newChecksum();
    final ByteBuffer readBuffer = ByteBuffer.allocate(IoUtil.BLOCK_SIZE);

    for (final var path : paths) {
      final byte[] chunkId = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
      checksum.update(chunkId);

      try (final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        readBuffer.clear();
        while (channel.read(readBuffer) > 0) {
          readBuffer.flip();
          checksum.update(readBuffer);
          readBuffer.clear();
        }
      }
    }

    return checksum.getValue();
  }
}
