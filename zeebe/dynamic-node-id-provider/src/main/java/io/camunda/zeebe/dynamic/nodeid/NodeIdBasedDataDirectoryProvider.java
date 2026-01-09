/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * A data directory provider that appends the node ID to the configured data directory. This is
 * useful in dynamic node ID scenarios where multiple nodes share the same base directory and need
 * to be isolated by their node ID.
 */
public class NodeIdBasedDataDirectoryProvider implements DataDirectoryProvider {

  private final NodeIdProvider nodeIdProvider;

  public NodeIdBasedDataDirectoryProvider(final NodeIdProvider nodeIdProvider) {
    this.nodeIdProvider = nodeIdProvider;
  }

  @Override
  public CompletableFuture<Path> initialize(
      final Path baseDataDirectory, final boolean gracefulShutdown) {
    final NodeInstance nodeInstance = nodeIdProvider.currentNodeInstance();
    if (nodeInstance == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Node instance is not available"));
    }
    final int nodeId = nodeInstance.id();
    final Path dataDirectory = baseDataDirectory.resolve(String.valueOf(nodeId));
    return CompletableFuture.completedFuture(dataDirectory);
  }
}
