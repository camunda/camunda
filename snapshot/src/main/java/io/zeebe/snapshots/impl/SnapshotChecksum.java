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

  private static final String CHECKSUM_FILE_NAME = "CHECKSUM";

  private SnapshotChecksum() {
    throw new IllegalStateException("Utility class");
  }

  public static boolean hasChecksum(final Path snapshotDirectory) {
    final var file = snapshotDirectory.resolve(CHECKSUM_FILE_NAME).toFile();
    return file.exists();
  }

  public static long read(final Path snapshotDirectory) throws IOException {
    final var file = snapshotDirectory.resolve(CHECKSUM_FILE_NAME).toFile();
    if (!file.exists()) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a checksum file named %s, but no such file exists.",
              CHECKSUM_FILE_NAME));
    }
    try (final RandomAccessFile checksumFile = new RandomAccessFile(file, "r")) {
      return checksumFile.readLong();
    }
  }

  public static long calculate(final Path snapshotDirectory) throws IOException {
    try (final var fileStream = Files.list(snapshotDirectory).sorted()) {
      return createCombinedChecksum(
          fileStream
              .filter(path -> !path.endsWith(CHECKSUM_FILE_NAME))
              .collect(Collectors.toList()));
    }
  }

  public static void persist(final Path snapshotDirectory, final long checksum) throws IOException {
    final var file = snapshotDirectory.resolve(CHECKSUM_FILE_NAME).toFile();
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
    final Checksum checksum = SnapshotChunkUtil.newChecksum();

    final ByteBuffer buff = ByteBuffer.allocate(IoUtil.BLOCK_SIZE);
    for (final var path : paths) {
      final byte[] chunkId = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
      checksum.update(chunkId);

      try (final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        buff.clear();
        while (channel.read(buff) > 0) {
          buff.flip();
          checksum.update(buff);
          buff.clear();
        }
      }
    }
    return checksum.getValue();
  }
}
