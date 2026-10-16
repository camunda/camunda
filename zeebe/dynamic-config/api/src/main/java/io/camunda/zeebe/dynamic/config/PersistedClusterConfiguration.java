/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.Header.HEADER_LENGTH;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;

/**
 * The on-disk header format (a version byte followed by a CRC32C checksum) shared by every version
 * of the persisted configuration file, plus the legacy (version 1) header version constant and the
 * exceptions raised on a corrupted or unexpected-version file.
 *
 * <p>{@link PersistedCurrentClusterConfiguration} dispatches on {@link Header#version()} to read
 * either this legacy format or its own (version 2), and reuses {@link #writeToFile} and the
 * exception types below. {@code io.camunda.debug.cli.TopologyMetaCommand} uses the same members
 * directly to print or edit a {@code .topology.meta} file of either version.
 */
public final class PersistedClusterConfiguration {
  // Header is a single byte for the version, followed by a long for the checksum.
  // Constant version, to be incremented if the format changes.
  public static final byte VERSION = 1;

  private PersistedClusterConfiguration() {}

  /**
   * Writes the serialized configuration into path, under the legacy {@link #VERSION}.
   *
   * @param body the serialized configuration
   * @param path the path where to save the file
   */
  public static void writeToFile(final byte[] body, final Path path) throws IOException {
    writeToFile(body, path, VERSION);
  }

  public static void writeToFile(final byte[] body, final Path path, final byte version)
      throws IOException {
    final var checksum = checksum(body, 0, body.length);
    final var buffer =
        ByteBuffer.allocate(HEADER_LENGTH + body.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(version)
            .putLong(checksum)
            .put(body);
    Files.write(
        path,
        buffer.array(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.DSYNC);
  }

  private static long checksum(final byte[] bytes, final int offset, final int length) {
    final var checksum = new CRC32C();
    checksum.update(bytes, offset, length);
    return checksum.getValue();
  }

  public static final class UnexpectedVersion extends RuntimeException {
    UnexpectedVersion(final Path topologyFile, final byte version) {
      super(
          "Topology file %s had version '%s', which is not a supported version"
              .formatted(topologyFile, version));
    }
  }

  public static final class MissingHeader extends RuntimeException {
    MissingHeader(final Path topologyFile, final Object fileSize) {
      super(
          "Topology file %s is too small to contain the expected header: %s bytes"
              .formatted(topologyFile, fileSize));
    }
  }

  public static final class ChecksumMismatch extends RuntimeException {
    ChecksumMismatch(
        final Path topologyFile, final long expectedChecksum, final long actualChecksum) {
      super(
          "Corrupted topology file: %s. Expected checksum: '%d', actual checksum: '%d'"
              .formatted(topologyFile, expectedChecksum, actualChecksum));
    }
  }

  public record Header(byte version, long checksum) {

    public static final int HEADER_LENGTH = Byte.BYTES + Long.BYTES;

    /**
     * Parses the header without rejecting unknown versions, so a caller that can read more than one
     * on-disk format dispatches on {@link #version()} itself.
     */
    public static Header parseAnyVersion(final byte[] content, final Path topologyFile) {
      if (content.length < HEADER_LENGTH) {
        throw new MissingHeader(topologyFile, content.length);
      }
      final var header = ByteBuffer.wrap(content, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
      return new Header(header.get(), header.getLong());
    }
  }
}
