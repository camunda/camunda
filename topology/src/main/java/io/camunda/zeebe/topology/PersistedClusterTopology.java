/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Manages reading and updating ClusterTopology in a local persisted file * */
final class PersistedClusterTopology {
  private final Path topologyFile;
  private ClusterTopology clusterTopology;

  PersistedClusterTopology(final Path topologyFile) {
    this.topologyFile = topologyFile;
  }

  void initialize() throws IOException {
    if (Files.exists(topologyFile)) {
      final var serializedTopology = Files.readAllBytes(topologyFile);
      if (serializedTopology.length > 0) {
        clusterTopology = ClusterTopology.decode(serializedTopology);
      }
      return;
    }
    clusterTopology = null;
  }

  ClusterTopology getTopology() {
    return clusterTopology;
  }

  void update(final ClusterTopology clusterTopology) throws IOException {
    final var serializedTopology = clusterTopology.encode();
    Files.write(
        topologyFile,
        serializedTopology,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.DSYNC);

    this.clusterTopology = clusterTopology;
  }
}
