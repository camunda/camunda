/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.volume;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** A utility class which can take an input stream and extract */
final class TarExtractor {

  /** Singleton instance of this utility class */
  public static final TarExtractor INSTANCE = new TarExtractor();

  /**
   * Extracts the contents of the input stream to the given destination.
   *
   * @param archiveInput the input stream of the archive
   * @param destination the path at which the contents of the archive should be extracted
   * @throws IOException if the archive cannot be read, or the destination is not writable
   */
  public void extract(final TarArchiveInputStream archiveInput, final Path destination)
      throws IOException {
    for (TarArchiveEntry entry = archiveInput.getNextEntry();
        entry != null;
        entry = archiveInput.getNextEntry()) {
      extractEntry(archiveInput, entry, destination);
    }
  }

  private void extractEntry(
      final TarArchiveInputStream archiveInput, final TarArchiveEntry entry, final Path destination)
      throws IOException {
    if (!archiveInput.canReadEntryData(entry)) {
      throw new IOException(
          String.format(
              "Expected to extract %s from TAR archive, but data cannot be read; possibly the "
                  + "archive is corrupted",
              entry));
    }

    final Path entryPath = destination.resolve(entry.getName());
    if (!entryPath.normalize().startsWith(destination)) {
      throw new IllegalStateException(
          String.format(
              "Expected to extract %s from TAR archive to the destination folder %s, but it would "
                  + "be extracted outside to %s; make sure no entry contains `..` or the likes in "
                  + "their name",
              entry.getName(), destination, entryPath));
    }

    if (entry.isDirectory()) {
      Files.createDirectories(entryPath);
      for (final TarArchiveEntry childEntry : entry.getDirectoryEntries()) {
        extractEntry(archiveInput, childEntry, entryPath);
      }

      return;
    }

    Files.createDirectories(entryPath.getParent());
    writeEntry(archiveInput, entry, entryPath);
  }

  @SuppressWarnings("java:S2095") // ensure we don't close the input channel
  private void writeEntry(
      final TarArchiveInputStream archiveInput, final TarArchiveEntry entry, final Path destination)
      throws IOException {
    final ReadableByteChannel input = Channels.newChannel(archiveInput);
    try (final FileChannel output =
        FileChannel.open(destination, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
      output.transferFrom(input, 0, entry.getRealSize());
      output.force(true);
    }
  }
}
