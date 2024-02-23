/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

final class SnapshotChecksum {

  private SnapshotChecksum() {
    throw new IllegalStateException("Utility class");
  }

  public static ImmutableChecksumsSFV read(final Path checksumPath) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "r")) {
      if (checksumFile.length() == 8) {
        // compatibility mode
        final long combinedChecksum = checksumFile.readLong();
        return new SfvChecksumImpl(combinedChecksum);
      }
      final SfvChecksumImpl sfvChecksum = new SfvChecksumImpl();
      String line;
      while ((line = checksumFile.readLine()) != null) {
        sfvChecksum.updateFromSfvFile(line);
      }
      return sfvChecksum;
    }
  }

  public static MutableChecksumsSFV calculate(final Path snapshotDirectory) throws IOException {
    try (final var fileStream =
        Files.list(snapshotDirectory).filter(SnapshotChecksum::isNotMetadataFile).sorted()) {
      final var sfvChecksum = createCombinedChecksum(fileStream);

      // While persisting transient snapshot, the checksum of metadata file is added at the end.
      // Hence when we recalculate the checksum, we must follow the same order. Otherwise base on
      // the file name, the sorted file list will have a differnt order and thus result in a
      // different checksum.
      final var metadataFile = snapshotDirectory.resolve(FileBasedSnapshotStore.METADATA_FILE_NAME);
      if (metadataFile.toFile().exists()) {
        sfvChecksum.updateFromFile(metadataFile);
      }
      return sfvChecksum;
    }
  }

  private static boolean isNotMetadataFile(final Path file) {
    return !file.getFileName().toString().equals(FileBasedSnapshotStore.METADATA_FILE_NAME);
  }

  public static void persist(final Path checksumPath, final ImmutableChecksumsSFV checksum)
      throws IOException {
    // FileOutputStream#flush does nothing, so use a file channel to enforce it
    try (final var channel =
            FileChannel.open(
                checksumPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        final var output = Channels.newOutputStream(channel)) {
      checksum.write(output);
      channel.force(true);
    }
  }

  /**
   * computes a checksum for the files, in the order they're presented
   *
   * @return the SfvChecksum object
   */
  private static SfvChecksumImpl createCombinedChecksum(final Stream<Path> files) {
    final SfvChecksumImpl checksum = new SfvChecksumImpl();
    files.forEachOrdered(
        path -> {
          try {
            checksum.updateFromFile(path);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    return checksum;
  }
}
