/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

final class SnapshotChecksum {

  private SnapshotChecksum() {
    throw new IllegalStateException("Utility class");
  }

  public static long read(final Path checksumPath) throws IOException {
    if (!Files.exists(checksumPath)) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a checksum file named %s, but no such file exists.", checksumPath));
    }
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
    final var file = checksumPath.toFile();
    // If checksum file already exists, don't overwrite it
    if (file.createNewFile()) {
      try (final RandomAccessFile checksumFile = new RandomAccessFile(file, "rw")) {
        checksumFile.writeLong(checksum);
      }
    }
  }

  public static boolean verify(final Path snapshotDirectory) throws IOException {
    final var expectedChecksum = read(snapshotDirectory);
    final var actualChecksum = calculate(snapshotDirectory);
    return expectedChecksum == actualChecksum;
  }

  /** computes a checksum for the files, in the order they're presented */
  private static long createCombinedChecksum(final List<Path> paths) throws IOException {
    final Checksum checksumGenerator = new CRC32();
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
