/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;

/**
 * Reads and writes a {@link CurrentClusterConfiguration} in a local persisted file: a {@link
 * Header} (a version byte followed by a CRC32C checksum of the body), then the serialized
 * configuration. The file is only ever written and read as header version {@value #VERSION}.
 */
public final class PersistedCurrentClusterConfiguration {

  /** Name of the file in the data directory that stores the persisted cluster configuration. */
  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";

  public static final byte VERSION = 2;

  /**
   * The version of a file written before 8.10, holding a single-partition-group configuration. It
   * is no longer read: every 8.10 broker rewrites it as {@value #VERSION} on its first start.
   */
  private static final byte PRE_8_10_VERSION = 1;

  private final Path configurationFile;
  private final ClusterConfigurationSerializer serializer;
  private CurrentClusterConfiguration configuration;
  // whether loading the file returns this configuration; not after a failed write, see update
  private boolean persisted = true;

  private PersistedCurrentClusterConfiguration(
      final Path configurationFile,
      final ClusterConfigurationSerializer serializer,
      final CurrentClusterConfiguration configuration) {
    this.configurationFile = configurationFile;
    this.serializer = serializer;
    this.configuration = configuration;
  }

  /**
   * Creates a new {@link PersistedCurrentClusterConfiguration}. If the file does not exist yet, the
   * configuration is the empty {@link CurrentClusterConfiguration#uninitialized()} and {@link
   * #isUninitialized()} returns {@code true}.
   *
   * @throws UncheckedIOException if any unexpected IO error occurs
   * @throws UnexpectedVersion if the header version is not {@value #VERSION}
   * @throws ChecksumMismatch if the checksum does not match
   * @throws MissingHeader if the file is too small for the header
   */
  public static PersistedCurrentClusterConfiguration ofFile(
      final Path configurationFile, final ClusterConfigurationSerializer serializer) {
    final CurrentClusterConfiguration currentlyPersisted;
    try {
      currentlyPersisted = readFromFile(configurationFile, serializer);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new PersistedCurrentClusterConfiguration(
        configurationFile, serializer, currentlyPersisted);
  }

  public CurrentClusterConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * Persists the configuration, replacing the previous one.
   *
   * <p>After a failed write, the file may hold the previous configuration or the one that failed to
   * be written, so the next update writes even a configuration equal to the one in memory. Retrying
   * is safe: every write rewrites the whole configuration to a temporary file and renames it over
   * the old one, so it never relies on syncing again what failed to sync.
   *
   * @throws IOException if writing fails
   */
  public void update(final CurrentClusterConfiguration updatedConfiguration) throws IOException {
    if (persisted && configuration.equals(updatedConfiguration)) {
      return;
    }
    persisted = false;
    writeToFile(
        serializer.encodeCurrentClusterConfiguration(updatedConfiguration), configurationFile);
    configuration = updatedConfiguration;
    persisted = true;
  }

  /**
   * Returns {@code true} while no configuration has been persisted yet, i.e. the cluster has no
   * members and no partition groups.
   */
  public boolean isUninitialized() {
    return configuration.isUninitialized();
  }

  /**
   * Writes an already serialized configuration into {@code path}, behind a header of the current
   * {@link #VERSION}.
   */
  public static void writeToFile(final byte[] body, final Path path) throws IOException {
    final var buffer =
        ByteBuffer.allocate(Header.LENGTH + body.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION)
            .putLong(checksum(body, 0, body.length))
            .put(body);
    // Write a new file and replace the old one with it, rather than overwriting the old one in
    // place: a write that fails, and a restart before the next one, would otherwise leave neither
    // the old nor the new configuration on disk.
    final var tempFile = path.resolveSibling(path.getFileName() + ".tmp");
    Files.write(
        tempFile,
        buffer.array(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.DSYNC);
    FileUtil.moveDurably(
        tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private static CurrentClusterConfiguration readFromFile(
      final Path configurationFile, final ClusterConfigurationSerializer serializer)
      throws IOException {
    if (!Files.exists(configurationFile)) {
      return CurrentClusterConfiguration.uninitialized();
    }

    final var content = Files.readAllBytes(configurationFile);
    final var header = Header.read(content, configurationFile);
    final var bodyLength = content.length - Header.LENGTH;
    final var actualChecksum = checksum(content, Header.LENGTH, bodyLength);
    if (header.checksum() != actualChecksum) {
      throw new ChecksumMismatch(configurationFile, header.checksum(), actualChecksum);
    }
    return serializer.decodeCurrentClusterConfiguration(content, Header.LENGTH, bodyLength);
  }

  private static long checksum(final byte[] bytes, final int offset, final int length) {
    final var checksum = new CRC32C();
    checksum.update(bytes, offset, length);
    return checksum.getValue();
  }

  /** The header in front of the serialized configuration. */
  public record Header(byte version, long checksum) {

    public static final int LENGTH = Byte.BYTES + Long.BYTES;

    /**
     * Reads the header at the start of {@code content}, without verifying the checksum.
     *
     * @throws MissingHeader if the content is too small for the header
     * @throws UnexpectedVersion if the header version is not {@value
     *     PersistedCurrentClusterConfiguration#VERSION}
     */
    public static Header read(final byte[] content, final Path file) {
      if (content.length < LENGTH) {
        throw new MissingHeader(file, content.length);
      }
      final var buffer = ByteBuffer.wrap(content, 0, LENGTH).order(ByteOrder.LITTLE_ENDIAN);
      final var header = new Header(buffer.get(), buffer.getLong());
      if (header.version() != VERSION) {
        throw new UnexpectedVersion(file, header.version());
      }
      return header;
    }
  }

  public static final class UnexpectedVersion extends RuntimeException {
    UnexpectedVersion(final Path topologyFile, final byte version) {
      super(
          version == PRE_8_10_VERSION
              ? ("Topology file %s was written before 8.10 (version '%s'), which is no longer"
                      + " supported. Start this broker on 8.10 first, which rewrites the file as"
                      + " version '%s'.")
                  .formatted(topologyFile, version, VERSION)
              : "Topology file %s had version '%s', but only version '%s' is supported"
                  .formatted(topologyFile, version, VERSION));
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
}
