/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.camunda.zeebe.topology.serializer.ClusterTopologySerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.CRC32C;

/**
 * Manages reading and updating ClusterTopology in a local persisted file. The file consists of a
 * fixed-size header containing a version and a checksum, followed by the serialized topology.
 */
final class PersistedClusterTopology {
  // Constant version, to be incremented if the format changes.
  private static final byte VERSION = 1;

  // Header is a single byte for the version, followed by a long for the checksum.
  private static final int HEADER_LENGTH = Byte.BYTES + Long.BYTES;
  private final Path topologyFile;
  private final ClusterTopologySerializer serializer;
  private ClusterTopology clusterTopology;

  private PersistedClusterTopology(
      final Path topologyFile,
      final ClusterTopologySerializer serializer,
      final ClusterTopology clusterTopology) {
    this.topologyFile = topologyFile;
    this.serializer = serializer;
    this.clusterTopology = clusterTopology;
  }

  /**
   * Creates a new PersistedClusterTopology. If the file does not exist yet, the topology is
   * uninitialized. The file is created on the first {@link #update(ClusterTopology)}.
   *
   * @param topologyFile Path to the persisted topology file. Does not need to exist yet.
   * @param serializer used to (de)serialize the topology. Does not need to care about versioning or
   *     checksums.
   * @throws UncheckedIOException if any unexpected IO error occurs.
   * @throws UnexpectedVersion if the file exists but has an unexpected version.
   * @throws ChecksumMismatch if the file exists but the checksum does not match.
   * @throws MissingHeader if the file exists but is too small to contain the header.
   */
  static PersistedClusterTopology ofFile(
      final Path topologyFile, final ClusterTopologySerializer serializer) {
    final ClusterTopology currentlyPersisted;
    try {
      currentlyPersisted = readFromFile(topologyFile, serializer);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new PersistedClusterTopology(topologyFile, serializer, currentlyPersisted);
  }

  ClusterTopology getTopology() {
    return clusterTopology;
  }

  void update(final ClusterTopology clusterTopology) throws IOException {
    if (this.clusterTopology.equals(clusterTopology)) {
      return;
    }
    writeToFile(clusterTopology);
    this.clusterTopology = clusterTopology;
  }

  public boolean isUninitialized() {
    return clusterTopology.isUninitialized();
  }

  private static ClusterTopology readFromFile(
      final Path topologyFile, final ClusterTopologySerializer serializer) throws IOException {
    if (!Files.exists(topologyFile)) {
      return ClusterTopology.uninitialized();
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

    final var body = Arrays.copyOfRange(content, HEADER_LENGTH, content.length);

    final var actualChecksum = checksum(body);
    if (expectedChecksum != actualChecksum) {
      throw new ChecksumMismatch(topologyFile, expectedChecksum, actualChecksum);
    }

    // deserialize the topology
    return serializer.decodeClusterTopology(body);
  }

  private void writeToFile(final ClusterTopology clusterTopology) throws IOException {
    final var body = serializer.encode(clusterTopology);
    final var checksum = checksum(body);
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

  private static long checksum(final byte[] bytes) {
    final var checksum = new CRC32C();
    checksum.update(bytes);
    return checksum.getValue();
  }

  private static final class UnexpectedVersion extends RuntimeException {
    private UnexpectedVersion(final Path topologyFile, final byte version) {
      super(
          "Topology file %s had version '%s', but expected version '%s'"
              .formatted(topologyFile, version, VERSION));
    }
  }

  private static final class MissingHeader extends RuntimeException {
    private MissingHeader(final Path topologyFile, final Object fileSize) {
      super(
          "Topology file %s is too small to contain the expected header: %s bytes"
              .formatted(topologyFile, fileSize));
    }
  }

  private static final class ChecksumMismatch extends RuntimeException {
    private ChecksumMismatch(
        final Path topologyFile, final long expectedChecksum, final long actualChecksum) {
      super(
          "Corrupted topology file: %s. Expected checksum: '%d', actual checksum: '%d'"
              .formatted(topologyFile, expectedChecksum, actualChecksum));
    }
  }
}
