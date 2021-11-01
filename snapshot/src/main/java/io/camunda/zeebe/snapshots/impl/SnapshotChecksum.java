/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class SnapshotChecksum {

  private SnapshotChecksum() {
    throw new IllegalStateException("Utility class");
  }

  public static SfvChecksum read(final Path checksumPath) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "r")) {
      if (checksumFile.length() == 8) {
        // compatibility mode
        final long combinedChecksum = checksumFile.readLong();
        return new SfvChecksum(combinedChecksum);
      }
      final SfvChecksum sfvChecksum = new SfvChecksum();
      String line;
      while ((line = checksumFile.readLine()) != null) {
        sfvChecksum.updateFromSfvFile(line);
      }
      return sfvChecksum;
    }
  }

  public static SfvChecksum calculate(final Path snapshotDirectory) throws IOException {
    try (final var fileStream = Files.list(snapshotDirectory).sorted()) {
      final SfvChecksum sfvChecksum =
          createCombinedChecksum(fileStream.collect(Collectors.toList()));
      sfvChecksum.setSnapshotDirectoryComment(snapshotDirectory.toString());
      return sfvChecksum;
    }
  }

  public static void persist(final Path checksumPath, final SfvChecksum checksum)
      throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "rwd")) {
      final byte[] data = checksum.serializeSfvFileData();
      checksumFile.write(data);
      checksumFile.setLength(data.length);
    }
  }

  /**
   * computes a checksum for the files, in the order they're presented
   *
   * @return the SfvChecksum object
   */
  private static SfvChecksum createCombinedChecksum(final List<Path> files) throws IOException {
    final SfvChecksum checksum = new SfvChecksum();
    for (final var f : files) {
      checksum.updateFromFile(f);
    }
    return checksum;
  }
}
