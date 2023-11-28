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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Manages reading and updating ClusterTopology in a local persisted file * */
final class PersistedClusterTopology {
  private final Path topologyFile;
  private final ClusterTopologySerializer serializer;
  private ClusterTopology clusterTopology = ClusterTopology.uninitialized();

  PersistedClusterTopology(final Path topologyFile, final ClusterTopologySerializer serializer) {
    this.topologyFile = topologyFile;
    this.serializer = serializer;
    clusterTopology = readFromFile();
  }

  ClusterTopology getTopology() {
    return clusterTopology;
  }

  private ClusterTopology readFromFile() {
    if (Files.exists(topologyFile)) {
      try {
        return serializer.decodeClusterTopology(Files.readAllBytes(topologyFile));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      return ClusterTopology.uninitialized();
    }
  }

  private void writeToFile(final ClusterTopology clusterTopology) throws IOException {
    Files.write(
        topologyFile,
        serializer.encode(clusterTopology),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.DSYNC);
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
}
