/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import io.atomix.cluster.Member;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface NodeIdProvider extends AutoCloseable {

  CompletableFuture<Void> initialize(int clusterSize);

  /**
   * Scale the available node ids to the new cluster size.
   *
   * @param newClusterSize the new cluster size
   * @return a CompletableFuture that completes when the scaling operation is done
   */
  CompletableFuture<Void> scale(int newClusterSize);

  /**
   * @return the node instance. Null can be returned when the provider is shutting down
   */
  NodeInstance currentNodeInstance();

  /**
   * Verify that the NodeIdProvider is currently active and that the node instance acquired is still
   * valid.
   *
   * @return A CompletableFuture with true if it's valid, false otherwise. The future always returns
   *     within a predefined time.
   */
  CompletableFuture<Boolean> isValid();

  /**
   * Indicates whether the previous node instance holding this node's identity shut down gracefully.
   *
   * <p>The information is available once the provider has been initialized and has determined the
   * shutdown state of the previous holder of the lease for this node ID. Implementations may always
   * return {@code true} when there is no prior holder (for example, for static node IDs) or when
   * graceful shutdown is guaranteed by design.
   *
   * @return a {@link CompletableFuture} completed with {@code true} if the previous node instance
   *     gracefully released its lease before this node started, or {@code false} if it crashed, its
   *     lease expired, or the provider cannot confirm a graceful shutdown
   */
  CompletableFuture<Boolean> previousNodeGracefullyShutdown();

  /**
   * Sets the current known cluster members.
   *
   * @param currentMembers the current members of the cluster
   */
  void setMembers(Set<Member> currentMembers);

  /**
   * Awaits until the NodeIdProvider is ready and other services can start safely.
   *
   * @return A CompletableFuture completed with true when the provider is ready.
   */
  CompletableFuture<Boolean> awaitReadiness();

  /**
   * NodeIdProvider to be used when the nodeId is static, i.e. it's defined in the configuration.
   *
   * @param nodeId the nodeId for this application
   */
  static NodeIdProvider staticProvider(final int nodeId) {
    if (nodeId < 0) {
      throw new IllegalArgumentException("Invalid nodeId: " + nodeId);
    }
    return new NodeIdProvider() {

      @Override
      public void close() throws Exception {}

      @Override
      public CompletableFuture<Void> initialize(final int clusterSize) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Void> scale(final int newClusterSize) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public NodeInstance currentNodeInstance() {
        return new NodeInstance(nodeId, Version.zero());
      }

      @Override
      public CompletableFuture<Boolean> isValid() {
        return CompletableFuture.completedFuture(true);
      }

      @Override
      public CompletableFuture<Boolean> previousNodeGracefullyShutdown() {
        // not important for static node assignment
        return CompletableFuture.completedFuture(true);
      }

      @Override
      public void setMembers(final Set<Member> currentMembers) {
        // no-op
      }

      @Override
      public CompletableFuture<Boolean> awaitReadiness() {
        return CompletableFuture.completedFuture(true);
      }
    };
  }
}
