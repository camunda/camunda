/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.Header.HEADER_LENGTH;

import io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile;
import io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.ChecksumMismatch;
import io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.Header;
import io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.MissingHeader;
import io.camunda.zeebe.dynamic.config.api.PersistedClusterConfigurationFile.UnexpectedVersion;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages reading and updating ClusterConfiguration in a local persisted file. The file format
 * itself (header, checksum, versioning) is defined in {@link PersistedClusterConfigurationFile};
 * this class owns encoding/decoding the {@link ClusterConfiguration} body through the given {@link
 * ClusterConfigurationSerializer}.
 */
public final class PersistedClusterConfiguration {
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
  public static PersistedClusterConfiguration ofFile(
      final Path topologyFile, final ClusterConfigurationSerializer serializer) {
    final ClusterConfiguration currentlyPersisted;
    try {
      currentlyPersisted = readFromFile(topologyFile, serializer);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new PersistedClusterConfiguration(topologyFile, serializer, currentlyPersisted);
  }

  ClusterConfiguration getConfiguration() {
    return clusterConfiguration;
  }

  public void update(final ClusterConfiguration clusterConfiguration) throws IOException {
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
    final var header = Header.parseFrom(content, topologyFile);

    final var actualChecksum =
        PersistedClusterConfigurationFile.checksum(
            content, HEADER_LENGTH, content.length - HEADER_LENGTH);
    if (header.checksum() != actualChecksum) {
      throw new ChecksumMismatch(topologyFile, header.checksum(), actualChecksum);
    }
    // deserialize the topology
    return serializer.decodeClusterTopology(content, HEADER_LENGTH, content.length - HEADER_LENGTH);
  }

  private void writeToFile(final ClusterConfiguration clusterConfiguration) throws IOException {
    final var body = serializer.encode(clusterConfiguration);
    PersistedClusterConfigurationFile.writeToFile(body, topologyFile);
  }
}
