/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;

/**
 * Manages reading and updating ClusterConfiguration in a local persisted file. The file consists of
 * a fixed-size header containing a version and a checksum, followed by the serialized
 * configuration.
 */
public final class PersistedClusterConfiguration {
  // Header is a single byte for the version, followed by a long for the checksum.
  public static final int HEADER_LENGTH = Byte.BYTES + Long.BYTES;
  // Constant version, to be incremented if the format changes.
  private static final byte VERSION = 1;
  private final Path topologyFile;
  private final ClusterConfigurationSerializer serializer;
  private ClusterConfiguration clusterConfiguration;

  private PersistedClusterConfiguration(
      final Path topologyFile,
      final ClusterConfigurationSerializer serializer,
      final ClusterConfiguration clusterConfiguration) {
    this.topologyFile = topologyFile;
    this.serializer = serializer;
    this.clusterConfiguration = clusterConfiguration;
  }

  /**
   * Creates a new PersistedClusterConfiguration. If the file does not exist yet, the configuration
   * is uninitialized. The file is created on the first {@link #update(ClusterConfiguration)}.
   *
   * @param topologyFile Path to the persisted configuration file. Does not need to exist yet.
   * @param serializer used to (de)serialize the configuration. Does not need to care about
   *     versioning or checksums.
   * @throws UncheckedIOException if any unexpected IO error occurs.
   * @throws UnexpectedVersion if the file exists but has an unexpected version.
   * @throws ChecksumMismatch if the file exists but the checksum does not match.
   * @throws MissingHeader if the file exists but is too small to contain the header.
   */
  static PersistedClusterConfiguration ofFile(
      final Path topologyFile, final ClusterConfigurationSerializer serializer) {
    final ClusterConfiguration currentlyPersisted;
    try {
      currentlyPersisted = readFromFile(topologyFile, serializer);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new PersistedClusterConfiguration(topologyFile, serializer, currentlyPersisted);
  }

  public ClusterConfiguration getConfiguration() {
    return clusterConfiguration;
  }

  void update(final ClusterConfiguration clusterConfiguration) throws IOException {
    if (this.clusterConfiguration.equals(clusterConfiguration)) {
      return;
    }
    writeToFile(clusterConfiguration);
    this.clusterConfiguration = clusterConfiguration;
  }

  public boolean isUninitialized() {
    return clusterConfiguration.isUninitialized();
  }

  private static ClusterConfiguration readFromFile(
      final Path topologyFile, final ClusterConfigurationSerializer serializer) throws IOException {
    if (!Files.exists(topologyFile)) {
      return ClusterConfiguration.uninitialized();
    }

    final var content = Files.readAllBytes(topologyFile);
    if (content.length < HEADER_LENGTH) {
      throw new MissingHeader(topologyFile, content.length);
    }
    final var header = ByteBuffer.wrap(content, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    final var version = header.get();
    final var expectedChecksum = header.getLong();

    if (version != VERSION) {
      throw new UnexpectedVersion(topologyFile, version);
    }

    final var actualChecksum = checksum(content, HEADER_LENGTH, content.length - HEADER_LENGTH);
    if (expectedChecksum != actualChecksum) {
      throw new ChecksumMismatch(topologyFile, expectedChecksum, actualChecksum);
    }

    // deserialize the topology
    return serializer.decodeClusterTopology(content, HEADER_LENGTH, content.length - HEADER_LENGTH);
  }

  private void writeToFile(final ClusterConfiguration clusterConfiguration) throws IOException {
    final var body = serializer.encode(clusterConfiguration);
    final var checksum = checksum(body, 0, body.length);
    final var buffer =
        ByteBuffer.allocate(HEADER_LENGTH + body.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION)
            .putLong(checksum)
            .put(body);
    Files.write(
        topologyFile,
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
    private UnexpectedVersion(final Path topologyFile, final byte version) {
      super(
          "Topology file %s had version '%s', but expected version '%s'"
              .formatted(topologyFile, version, VERSION));
    }
  }

  public static final class MissingHeader extends RuntimeException {
    private MissingHeader(final Path topologyFile, final Object fileSize) {
      super(
          "Topology file %s is too small to contain the expected header: %s bytes"
              .formatted(topologyFile, fileSize));
    }
  }

  public static final class ChecksumMismatch extends RuntimeException {
    private ChecksumMismatch(
        final Path topologyFile, final long expectedChecksum, final long actualChecksum) {
      super(
          "Corrupted topology file: %s. Expected checksum: '%d', actual checksum: '%d'"
              .formatted(topologyFile, expectedChecksum, actualChecksum));
    }
  }
}
