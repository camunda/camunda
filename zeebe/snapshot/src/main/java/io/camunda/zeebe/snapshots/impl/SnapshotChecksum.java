/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ChecksumProvider;
import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;

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
    return createChecksumForSnapshot(snapshotDirectory, null);
  }

  public static MutableChecksumsSFV calculateWithFullFileChecksums(
      final Path snapshotDirectory, final ChecksumProvider provider) throws IOException {
    return createChecksumForSnapshot(snapshotDirectory, provider);
  }

  private static MutableChecksumsSFV createChecksumForSnapshot(
      final Path snapshotDirectory, final ChecksumProvider provider) throws IOException {

    try (final var fileStream =
        Files.list(snapshotDirectory).filter(SnapshotChecksum::isNotMetadataFile).sorted()) {
      final SfvChecksumImpl sfvChecksum = new SfvChecksumImpl();
      final Map<String, byte[]> fullFileChecksums =
          provider == null
              ? Collections.emptyMap()
              : provider.getSnapshotChecksums(snapshotDirectory);
      fileStream.forEachOrdered(path -> updateChecksum(sfvChecksum, fullFileChecksums, path));

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

  private static void updateChecksum(
      final MutableChecksumsSFV checksum,
      final Map<String, byte[]> fullFileChecksums,
      final Path file) {
    final String fileName = file.getFileName().toString();
    if (fullFileChecksums.containsKey(fileName)) {
      final Integer sstChecksum =
          ByteBuffer.wrap(fullFileChecksums.get(fileName)).order(ByteOrder.BIG_ENDIAN).getInt();
      checksum.updateFromChecksum(file, Integer.toUnsignedLong(sstChecksum));
    } else {
      try {
        checksum.updateFromFile(file);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
