/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.MutableSnapshotManifest;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotManifest;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

final class SnapshotManifests {

  private SnapshotManifests() {
    throw new IllegalStateException("Utility class");
  }

  public static SnapshotManifest read(final Path checksumPath) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "r")) {
      final SfvManifestImpl sfvChecksum = new SfvManifestImpl();
      String line;
      while ((line = checksumFile.readLine()) != null) {
        sfvChecksum.updateFromSfvFile(line);
      }
      return sfvChecksum;
    }
  }

  public static MutableSnapshotManifest calculate(final Path snapshotDirectory) throws IOException {
    return createChecksumForSnapshot(snapshotDirectory, snapshotPath -> Map.of());
  }

  public static MutableSnapshotManifest calculateWithProvidedChecksums(
      final Path snapshotDirectory, final SnapshotFileInfoProvider provider) throws IOException {
    return createChecksumForSnapshot(snapshotDirectory, provider);
  }

  private static MutableSnapshotManifest createChecksumForSnapshot(
      final Path snapshotDirectory, final SnapshotFileInfoProvider provider) throws IOException {

    try (final var fileStream =
        Files.list(snapshotDirectory).filter(SnapshotManifests::isNotMetadataFile).sorted()) {
      final SfvManifestImpl sfvChecksum = new SfvManifestImpl();
      final Map<String, Long> fullFileChecksums = provider.getSnapshotChecksums(snapshotDirectory);
      fileStream.forEachOrdered(path -> updateChecksum(sfvChecksum, fullFileChecksums, path));

      // While persisting transient snapshot, the checksum of metadata file is added at the end.
      // Hence when we recalculate the checksum, we must follow the same order. Otherwise base on
      // the file name, the sorted file list will have a differnt order and thus result in a
      // different checksum.
      final var metadataFile =
          snapshotDirectory.resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
      if (metadataFile.toFile().exists()) {
        sfvChecksum.updateFromFile(metadataFile);
      }
      return sfvChecksum;
    }
  }

  /**
   * Returns true for snapshot data files, excluding the metadata file. The metadata file is
   * excluded from both checksum computation and directory size accounting because it is handled
   * separately in both cases: during persist it is written last (after the checksum/size are
   * already captured), and on receive it is decoded directly from its own chunk.
   */
  static boolean isNotMetadataFile(final Path file) {
    return !file.getFileName().toString().equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
  }

  public static void persist(final Path checksumPath, final SnapshotManifest checksum)
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

  private static void updateChecksum(
      final MutableSnapshotManifest checksum,
      final Map<String, Long> fullFileChecksums,
      final Path file) {
    final String fileName = file.getFileName().toString();
    if (fullFileChecksums.containsKey(fileName)) {
      checksum.updateFromChecksum(file, fullFileChecksums.get(fileName));
    } else {
      try {
        checksum.updateFromFile(file);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
