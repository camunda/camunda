/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * The default {@link ManifestStore}: the manifest is a single file, committed by writing a temp
 * file, fsyncing it and the directory, then atomically renaming it into place (see {@link
 * DurableFiles}). The atomic rename is the pass's one durability point — the manifest is never
 * observed torn.
 *
 * <p>{@link #latest()} re-validates every referenced segment (existence, exact byte length, and
 * CRC-32) before returning, so a manifest pointing at a missing or torn segment fails loudly. This
 * is the deliberate opposite of a persisted acceleration index: the manifest records only truth and
 * is checked against the files it names.
 *
 * <p>Threading: driven by the single cleaner actor; not thread-safe.
 */
public final class FileManifestStore implements ManifestStore {

  static final String MANIFEST_FILE = "manifest";
  static final String MANIFEST_TMP = "manifest.tmp";

  private final Path directory;

  /**
   * @param directory the compaction directory holding the manifest and its segments
   */
  public FileManifestStore(final Path directory) {
    this.directory = directory;
  }

  @Override
  public void commit(final CompactionManifest manifest, final List<Path> newFiles) {
    // The segment files were already written durably by the writer; fsync them again defensively so
    // that a store implementation swapped in here cannot commit a manifest ahead of its data.
    for (final Path file : newFiles) {
      fsyncFile(file);
    }
    try {
      DurableFiles.writeAtomically(
          directory.resolve(MANIFEST_FILE), directory.resolve(MANIFEST_TMP), manifest.serialize());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to commit compaction manifest", e);
    }
  }

  @Override
  public Optional<CompactionManifest> latest() {
    final Path manifestPath = directory.resolve(MANIFEST_FILE);
    if (!Files.exists(manifestPath)) {
      return Optional.empty();
    }
    final CompactionManifest manifest;
    try {
      manifest = CompactionManifest.parse(Files.readAllBytes(manifestPath));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read compaction manifest", e);
    }
    for (final CleanSegment segment : manifest.segments()) {
      validateSegment(segment);
    }
    return Optional.of(manifest);
  }

  private void validateSegment(final CleanSegment segment) {
    final Path path = directory.resolve(segment.fileName());
    if (!Files.exists(path)) {
      throw new IllegalStateException(
          "Committed manifest references missing clean segment: " + segment.fileName());
    }
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(path);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read clean segment " + segment.fileName(), e);
    }
    if (bytes.length != segment.byteLength()) {
      throw new IllegalStateException(
          "Torn clean segment "
              + segment.fileName()
              + ": expected "
              + segment.byteLength()
              + " bytes, found "
              + bytes.length);
    }
    final var crc = new CRC32();
    crc.update(bytes);
    if (crc.getValue() != segment.crc32()) {
      throw new IllegalStateException(
          "Corrupt clean segment "
              + segment.fileName()
              + ": CRC-32 mismatch (manifest "
              + segment.crc32()
              + ", file "
              + crc.getValue()
              + ")");
    }
  }

  private static void fsyncFile(final Path file) {
    try (final FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.force(true);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to fsync " + file, e);
    }
  }
}
