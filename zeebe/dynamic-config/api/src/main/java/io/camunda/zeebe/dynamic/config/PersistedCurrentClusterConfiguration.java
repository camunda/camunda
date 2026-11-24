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
 * The multi-partition-group counterpart of {@link PersistedClusterConfiguration}. Reads and writes
 * a {@link CurrentClusterConfiguration} in a local persisted file, sharing the same on-disk header
 * layout (a version byte followed by a CRC32C checksum) as the legacy file so that a single file
 * can be migrated in place.
 *
 * <p>This class is used only when the new configuration model is enabled. It is kept separate from
 * {@link PersistedClusterConfiguration} so the legacy (feature-flag-off) persistence path stays
 * byte-for-byte identical and untouched.
 *
 * <h4>Versioning &amp; migration</h4>
 *
 * <ul>
 *   <li>Header version {@value #VERSION_LEGACY}: a legacy {@link
 *       io.camunda.zeebe.dynamic.config.state.ClusterConfiguration} body. On read, it is migrated
 *       to the new model via {@link CurrentClusterConfiguration#fromLegacy}. The migrated value is
 *       written back as version {@value #VERSION} immediately.
 *   <li>Header version {@value #VERSION}: a {@link CurrentClusterConfiguration} body.
 * </ul>
 *
 * First-boot migration after an upgrade is therefore automatic and requires no separate step.
 */
public final class PersistedCurrentClusterConfiguration {

  /** Name of the file in the data directory that stores the persisted cluster configuration. */
  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";

  public static final byte VERSION = 2;
  static final byte VERSION_LEGACY = 1;
  private static final int HEADER_LENGTH = Byte.BYTES + Long.BYTES;

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
   * #isUninitialized()} returns {@code true}. If it exists with a legacy (version 1) body, it is
   * migrated on read; the migrated value is written back as version 2 on the next {@link #update}.
   *
   * @throws UncheckedIOException if any unexpected IO error occurs
   * @throws PersistedClusterConfiguration.UnexpectedVersion if the header version is neither 1 nor
   *     2
   * @throws PersistedClusterConfiguration.ChecksumMismatch if the checksum does not match
   * @throws PersistedClusterConfiguration.MissingHeader if the file is too small for the header
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
    writeToFile(updatedConfiguration, configurationFile, serializer);
    configuration = updatedConfiguration;
    persisted = true;
  }

  /**
   * Returns {@code true} while no configuration has been persisted yet, i.e. the cluster has no
   * members and no partition groups. Mirrors {@code
   * PersistedClusterConfiguration#isUninitialized()} for the new model.
   */
  public boolean isUninitialized() {
    return configuration.isUninitialized();
  }

  private static CurrentClusterConfiguration readFromFile(
      final Path configurationFile, final ClusterConfigurationSerializer serializer)
      throws IOException {
    if (!Files.exists(configurationFile)) {
      return CurrentClusterConfiguration.uninitialized();
    }

    final var content = Files.readAllBytes(configurationFile);
    if (content.length < HEADER_LENGTH) {
      throw new PersistedClusterConfiguration.MissingHeader(configurationFile, content.length);
    }
    final var header = ByteBuffer.wrap(content, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    final var version = header.get();
    final var expectedChecksum = header.getLong();

    final var actualChecksum = checksum(content, HEADER_LENGTH, content.length - HEADER_LENGTH);
    if (expectedChecksum != actualChecksum) {
      throw new PersistedClusterConfiguration.ChecksumMismatch(
          configurationFile, expectedChecksum, actualChecksum);
    }

    return switch (version) {
      case VERSION ->
          serializer.decodeCurrentClusterConfiguration(
              content, HEADER_LENGTH, content.length - HEADER_LENGTH);
      case VERSION_LEGACY -> {
        // One-time migration: activate the pending plan's phase 0 (if any) so this broker
        // continues driving it forward under the new model, exactly as initPlan would for a
        // freshly-started plan. fromLegacy() stays a pure conversion; migration decides explicitly
        // when to start the pending change.
        final var migratedConfig =
            CurrentClusterConfiguration.fromLegacy(
                    serializer.decodeClusterTopology(
                        content, HEADER_LENGTH, content.length - HEADER_LENGTH))
                .activatePendingPhase();
        writeToFile(migratedConfig, configurationFile, serializer);
        yield migratedConfig;
      }
      default ->
          throw new PersistedClusterConfiguration.UnexpectedVersion(configurationFile, version);
    };
  }

  private static void writeToFile(
      final CurrentClusterConfiguration updatedConfiguration,
      final Path configurationFile,
      final ClusterConfigurationSerializer serializer)
      throws IOException {
    final var body = serializer.encodeCurrentClusterConfiguration(updatedConfiguration);
    final var checksum = checksum(body, 0, body.length);
    final var buffer =
        ByteBuffer.allocate(HEADER_LENGTH + body.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION)
            .putLong(checksum)
            .put(body);
    // Write a new file and replace the old one with it, rather than overwriting the old one in
    // place: a write that fails, and a restart before the next one, would otherwise leave neither
    // the old nor the new configuration on disk.
    final var tempFile = configurationFile.resolveSibling(configurationFile.getFileName() + ".tmp");
    Files.write(
        tempFile,
        buffer.array(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.DSYNC);
    FileUtil.moveDurably(
        tempFile,
        configurationFile,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  private static long checksum(final byte[] bytes, final int offset, final int length) {
    final var checksum = new CRC32C();
    checksum.update(bytes, offset, length);
    return checksum.getValue();
  }
}
