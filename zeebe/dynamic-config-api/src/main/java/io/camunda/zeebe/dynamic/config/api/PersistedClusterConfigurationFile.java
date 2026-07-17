/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.Header.HEADER_LENGTH;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;

/**
 * File format of the persisted cluster configuration: a fixed-size header (version + checksum)
 * followed by the serialized configuration body. Pure byte-level format, with no dependency on how
 * the body itself is encoded/decoded — {@link
 * io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration} in the {@code
 * zeebe-cluster-config} module owns reading/writing the actual {@code ClusterConfiguration} through
 * this format.
 */
public final class PersistedClusterConfigurationFile {

  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";

  // Header is a single byte for the version, followed by a long for the checksum.
  // Constant version, to be incremented if the format changes.
  private static final byte VERSION = 1;

  private PersistedClusterConfigurationFile() {}

  /**
   * Writes the serialized ClusterConfiguration into path.
   *
   * @param body the serialized ClusterConfiguration
   * @param path the path where to save the file
   */
  public static void writeToFile(final byte[] body, final Path path) throws IOException {
    final var checksum = checksum(body, 0, body.length);
    final var buffer =
        ByteBuffer.allocate(HEADER_LENGTH + body.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION)
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

  public static long checksum(final byte[] bytes, final int offset, final int length) {
    final var checksum = new CRC32C();
    checksum.update(bytes, offset, length);
    return checksum.getValue();
  }

  public static final class UnexpectedVersion extends RuntimeException {
    public UnexpectedVersion(final Path topologyFile, final byte version) {
      super(
          "Topology file %s had version '%s', but expected version '%s'"
              .formatted(topologyFile, version, VERSION));
    }
  }

  public static final class MissingHeader extends RuntimeException {
    public MissingHeader(final Path topologyFile, final Object fileSize) {
      super(
          "Topology file %s is too small to contain the expected header: %s bytes"
              .formatted(topologyFile, fileSize));
    }
  }

  public static final class ChecksumMismatch extends RuntimeException {
    public ChecksumMismatch(
        final Path topologyFile, final long expectedChecksum, final long actualChecksum) {
      super(
          "Corrupted topology file: %s. Expected checksum: '%d', actual checksum: '%d'"
              .formatted(topologyFile, expectedChecksum, actualChecksum));
    }
  }

  public record Header(byte version, long checksum) {

    public static final int HEADER_LENGTH = Byte.BYTES + Long.BYTES;

    public static Header parseFrom(final byte[] content, final Path topologyFile) {
      if (content.length < HEADER_LENGTH) {
        throw new MissingHeader(topologyFile, content.length);
      }
      final var header = ByteBuffer.wrap(content, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
      final var version = header.get();
      final var expectedChecksum = header.getLong();

      if (version != VERSION) {
        throw new UnexpectedVersion(topologyFile, version);
      }

      return new Header(version, expectedChecksum);
    }
  }
}
