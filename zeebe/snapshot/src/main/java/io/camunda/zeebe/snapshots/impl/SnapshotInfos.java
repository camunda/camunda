/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

final class SnapshotInfos {

  private SnapshotInfos() {
    throw new IllegalStateException("Utility class");
  }

  public static ImmutableChecksumsSFV read(final Path checksumPath) throws IOException {
    try (final RandomAccessFile checksumFile = new RandomAccessFile(checksumPath.toFile(), "r")) {
      final SfvChecksumImpl sfvChecksum = new SfvChecksumImpl();
      String line;
      while ((line = checksumFile.readLine()) != null) {
        sfvChecksum.updateFromSfvFile(line);
      }
      return sfvChecksum;
    }
  }

  public static MutableChecksumsSFV calculate(final Path snapshotDirectory) throws IOException {
    return computeSnapshotInfos(snapshotDirectory, snapshotPath -> SnapshotFilesInfo.none(), false)
        .checksum();
  }

  public static Result of(
      final Path snapshotDirectory,
      final SnapshotFileInfoProvider provider,
      final boolean needsTotalSize)
      throws IOException {
    return computeSnapshotInfos(snapshotDirectory, provider, needsTotalSize);
  }

  private static Result computeSnapshotInfos(
      final Path snapshotDirectory,
      final SnapshotFileInfoProvider provider,
      final boolean needsTotalSize)
      throws IOException {

    try (final var fileStream =
        Files.list(snapshotDirectory).filter(SnapshotInfos::isNotMetadataFile).sorted()) {
      final SfvChecksumImpl sfvChecksum = new SfvChecksumImpl();
      final SnapshotFilesInfo filesInfo = provider.getSnapshotFilesInfo(snapshotDirectory);
      final Map<String, Long> fileChecksums = filesInfo.checksums();
      final Map<String, Long> fileSizes = filesInfo.sizes();
      var totalSizeInBytes = 0L;
      for (final var iterator = fileStream.iterator(); iterator.hasNext(); ) {
        final var file = iterator.next();
        final var fileName = file.getFileName().toString();
        if (fileChecksums.containsKey(fileName)) {
          sfvChecksum.updateFromChecksum(file, fileChecksums.get(fileName));
        } else {
          sfvChecksum.updateFromFile(file);
        }

        if (needsTotalSize) {
          final var size = fileSizes.get(fileName);
          totalSizeInBytes += size != null ? size : Files.size(file);
        }
      }

      // While persisting transient snapshot, the checksum of metadata file is added at the end.
      // Hence when we recalculate the checksum, we must follow the same order. Otherwise base on
      // the file name, the sorted file list will have a differnt order and thus result in a
      // different checksum.
      final var metadataFile =
          snapshotDirectory.resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
      if (metadataFile.toFile().exists()) {
        sfvChecksum.updateFromFile(metadataFile);
      }
      return new Result(sfvChecksum, totalSizeInBytes);
    }
  }

  private static boolean isNotMetadataFile(final Path file) {
    return !file.getFileName().toString().equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
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

  record Result(MutableChecksumsSFV checksum, long totalSizeInBytes) {}
}
