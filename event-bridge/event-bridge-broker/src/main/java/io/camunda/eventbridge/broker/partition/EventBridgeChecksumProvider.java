/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partition;

import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * Pure-Java {@link CRC32CChecksumProvider} using the JDK's built-in {@link CRC32C} (Java 9+). Used
 * by {@link io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore} to compute per-file checksums
 * when writing and verifying snapshots.
 *
 * <p>This avoids taking a dependency on RocksDB just for checksum computation.
 */
final class EventBridgeChecksumProvider implements CRC32CChecksumProvider {

  private static final int BUFFER_SIZE = 8 * 1024;

  @Override
  public Map<String, Long> getSnapshotChecksums(final Path snapshotPath) {
    final Map<String, Long> checksums = new HashMap<>();
    try (final var stream = Files.walk(snapshotPath)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                final String relativeName =
                    snapshotPath.relativize(file).toString().replace('\\', '/');
                checksums.put(relativeName, computeChecksum(file));
              });
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to compute checksums for " + snapshotPath, e);
    }
    return checksums;
  }

  private static long computeChecksum(final Path file) {
    final var crc32c = new CRC32C();
    final byte[] buffer = new byte[BUFFER_SIZE];
    try (final InputStream in = Files.newInputStream(file)) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        crc32c.update(buffer, 0, read);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to compute checksum for " + file, e);
    }
    return crc32c.getValue();
  }
}
